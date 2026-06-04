package ksu.finalproject.domain.recommendation.service;

import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.repository.FoodRepository;
import ksu.finalproject.domain.foodrecord.entity.FoodRecord;
import ksu.finalproject.domain.foodrecord.repository.FoodRecordRepository;
import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.goal.entity.WeightGoal;
import ksu.finalproject.domain.goal.repository.CaloriesGoalRepository;
import ksu.finalproject.domain.goal.repository.WeightGoalRepository;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine;
import ksu.finalproject.domain.recommendation.engine.RecommendationInput;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.ActivityLevel;
import ksu.finalproject.domain.user.entity.enums.Gender;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.domain.weight.entity.WeightRecord;
import ksu.finalproject.domain.weight.repository.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecommendationInputService {
    private static final String CATALOG_VERSION = "food-v2";

    private final UserRepository userRepository;
    private final CaloriesGoalRepository caloriesGoalRepository;
    private final WeightGoalRepository weightGoalRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final FoodRepository foodRepository;
    private final FoodRecordRepository foodRecordRepository;

    @Transactional(readOnly = true)
    public RecommendationInput loadInput(int userId, LocalDate recommendationDate) {
        Users user = loadUser(userId);
        CaloriesGoal caloriesGoal = loadLatestCaloriesGoal(user);
        double currentWeight = loadCurrentWeight(user, recommendationDate);
        double targetWeight = loadTargetWeight(user, currentWeight);
        List<DietRecommendationEngine.Food> foods = loadFoods();
        if (foods.isEmpty()) {
            throw new IllegalArgumentException("recommendable food catalog is empty");
        }

        return new RecommendationInput(
                userId,
                Math.toIntExact(caloriesGoal.getId()),
                CATALOG_VERSION,
                new DietRecommendationEngine.UserProfile(
                        userId,
                        deriveBirthDate(user.getAge(), recommendationDate),
                        toRecommendationGender(user.getGender()),
                        requirePositive(user.getHeight(), "heightCm"),
                        toRecommendationActivityLevel(user.getActivityLevel())
                ),
                new DietRecommendationEngine.Goal(
                        currentWeight,
                        targetWeight,
                        null,
                        toDailyKcalGoal(caloriesGoal)
                ),
                foods,
                loadRecentEatenGroups(user, recommendationDate),
                Map.of()
        );
    }

    private Users loadUser(int userId) {
        return userRepository.findById((long) userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }

    private CaloriesGoal loadLatestCaloriesGoal(Users user) {
        return caloriesGoalRepository.findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("calories goal not found: " + user.getId()));
    }

    private double loadCurrentWeight(Users user, LocalDate recommendationDate) {
        return weightRecordRepository
                .findTopByUserAndRecordedDateLessThanOrderByRecordedDateDescCreatedAtDesc(user, recommendationDate)
                .map(WeightRecord::getWeight)
                .map(value -> requirePositive(value, "currentWeight"))
                .orElseGet(() -> requirePositive(user.getWeight(), "user.weightKg"));
    }

    private double loadTargetWeight(Users user, double currentWeight) {
        return weightGoalRepository.findTopByUserOrderByCreatedAtDesc(user)
                .map(WeightGoal::getGoalWeights)
                .filter(value -> value > 0)
                .orElse(currentWeight);
    }

    private LocalDate deriveBirthDate(Integer age, LocalDate recommendationDate) {
        if (age == null || age <= 0) {
            throw new IllegalArgumentException("age must be positive for recommendation");
        }
        return recommendationDate.minusYears(age.longValue());
    }

    private int toDailyKcalGoal(CaloriesGoal caloriesGoal) {
        return (int) Math.round(requirePositive(caloriesGoal.getGoalCalories(), "dailyKcalGoal"));
    }

    private List<DietRecommendationEngine.Food> loadFoods() {
        return foodRepository.findAllByIsActiveTrue().stream()
                .filter(food -> food.getServingKcal() != null && food.getServingKcal() > 0)
                .map(this::toRecommendationFood)
                .toList();
    }

    private DietRecommendationEngine.Food toRecommendationFood(Food food) {
        DietRecommendationEngine.MealRole role = classifyRole(food);
        return new DietRecommendationEngine.Food(
                Math.toIntExact(food.getId()),
                food.getFoodName(),
                groupName(food),
                role,
                roundedCalories(food.getServingKcal()),
                isRecommendable(food, role)
        );
    }

    private Set<String> loadRecentEatenGroups(Users user, LocalDate recommendationDate) {
        return foodRecordRepository.findByUserAndEatenDateGreaterThanEqualAndEatenDateLessThan(
                        user,
                        recommendationDate.minusDays(7),
                        recommendationDate
                ).stream()
                .map(this::groupName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private DietRecommendationEngine.Gender toRecommendationGender(Gender gender) {
        if (gender == null) {
            return DietRecommendationEngine.Gender.OTHER;
        }
        return switch (gender) {
            case MALE -> DietRecommendationEngine.Gender.MALE;
            case FEMALE -> DietRecommendationEngine.Gender.FEMALE;
        };
    }

    private DietRecommendationEngine.ActivityLevel toRecommendationActivityLevel(ActivityLevel activityLevel) {
        if (activityLevel == null) {
            return DietRecommendationEngine.ActivityLevel.LEVEL_3;
        }
        return switch (activityLevel) {
            case NONE, MIN -> DietRecommendationEngine.ActivityLevel.LEVEL_1;
            case LOW -> DietRecommendationEngine.ActivityLevel.LEVEL_2;
            case MEDIUM -> DietRecommendationEngine.ActivityLevel.LEVEL_3;
            case HIGH -> DietRecommendationEngine.ActivityLevel.LEVEL_4;
            case MAX -> DietRecommendationEngine.ActivityLevel.LEVEL_5;
        };
    }

    private boolean isRecommendable(Food food, DietRecommendationEngine.MealRole role) {
        int servingKcal = roundedCalories(food.getServingKcal());
        if (!Boolean.TRUE.equals(food.getIsActive()) || servingKcal <= 0 || servingKcal > 1_500) {
            return false;
        }
        if (containsAny(foodText(food), "피자", "버거", "햄버거", "핫도그")) {
            return false;
        }
        return role != DietRecommendationEngine.MealRole.BEVERAGE
                && role != DietRecommendationEngine.MealRole.SNACK_DESSERT
                && role != DietRecommendationEngine.MealRole.SAUCE;
    }

    private DietRecommendationEngine.MealRole classifyRole(Food food) {
        String text = foodText(food);
        if (containsAny(text, "음료", "커피", "라떼", "스무디", "에이드", "주스", "아이스티")) {
            return DietRecommendationEngine.MealRole.BEVERAGE;
        }
        if (containsAny(text, "소스", "양념", "드레싱", "시럽", "초간장")) {
            return DietRecommendationEngine.MealRole.SAUCE;
        }
        if (isOneDish(food)) {
            return DietRecommendationEngine.MealRole.ONE_DISH;
        }
        if (containsAny(text, "찌개", "전골")) {
            return DietRecommendationEngine.MealRole.STEW;
        }
        if (containsAny(text, "국 및 탕", "국", "탕")) {
            return DietRecommendationEngine.MealRole.SOUP;
        }
        if (isPlainRice(food)) {
            return DietRecommendationEngine.MealRole.STAPLE;
        }
        if (looksLikeMainProtein(food)) {
            return DietRecommendationEngine.MealRole.MAIN;
        }
        if (containsAny(text, "김치", "깍두기", "겉절이")) {
            return DietRecommendationEngine.MealRole.KIMCHI;
        }
        if (containsAny(text, "과자", "디저트", "케이크", "도넛", "와플", "마카롱", "아이스크림", "빙수", "떡", "빵")) {
            return DietRecommendationEngine.MealRole.SNACK_DESSERT;
        }
        return DietRecommendationEngine.MealRole.SIDE;
    }

    private boolean isOneDish(Food food) {
        return containsAny(foodText(food), "국밥", "볶음밥", "비빔밥", "덮밥", "김밥", "주먹밥", "오므라이스",
                "카레라이스", "짜장면", "자장면", "짬뽕", "라면", "국수", "냉면", "우동", "스파게티",
                "파스타", "죽", "스프", "샌드위치", "만두");
    }

    private boolean isPlainRice(Food food) {
        String text = foodText(food);
        if (containsAny(text, "국밥", "볶음밥", "비빔밥", "덮밥", "김밥", "주먹밥", "오므라이스", "카레", "리조또")) {
            return false;
        }
        return containsAny(text, "쌀밥", "기장밥", "보리밥", "수수밥", "잡곡밥", "현미밥", "흑미밥", "오곡밥", "찰밥", "콩밥");
    }

    private boolean looksLikeMainProtein(Food food) {
        return containsAny(foodText(food), "고기", "돼지", "소고기", "쇠고기", "닭", "오리", "갈비", "불고기", "제육",
                "생선", "고등어", "갈치", "조기", "삼치", "동태", "명태", "오징어", "낙지", "주꾸미", "새우",
                "게", "해물", "두부", "계란", "달걀", "햄", "소시지", "구이", "볶음", "찜", "조림", "튀김");
    }

    private String groupName(Food food) {
        if (StringUtils.hasText(food.getSubCategory())) {
            return food.getSubCategory();
        }
        if (StringUtils.hasText(food.getFoodName())) {
            int underscore = food.getFoodName().indexOf('_');
            return underscore > 0 ? food.getFoodName().substring(0, underscore) : food.getFoodName();
        }
        return "";
    }

    private String groupName(FoodRecord foodRecord) {
        if (foodRecord.getFood() != null) {
            return groupName(foodRecord.getFood());
        }
        if (StringUtils.hasText(foodRecord.getFoodName())) {
            int underscore = foodRecord.getFoodName().indexOf('_');
            return underscore > 0 ? foodRecord.getFoodName().substring(0, underscore) : foodRecord.getFoodName();
        }
        return "";
    }

    private String foodText(Food food) {
        return String.join(" ",
                nullToBlank(food.getFoodName()),
                nullToBlank(food.getMainCategory()),
                nullToBlank(food.getSubCategory()),
                nullToBlank(food.getDetailCategory())
        );
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null) {
            return false;
        }
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int roundedCalories(Double value) {
        return (int) Math.round(requirePositive(value, "servingKcal"));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private double requirePositive(Number value, String fieldName) {
        if (value == null || value.doubleValue() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive for recommendation");
        }
        return value.doubleValue();
    }
}



