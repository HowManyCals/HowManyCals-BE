package ksu.finalproject.domain.recommendation.service;

import ksu.finalproject.domain.food.repository.FoodRepository;
import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.goal.repository.CaloriesGoalRepository;
import ksu.finalproject.domain.recommendation.entity.DailyMealRecommendation;
import ksu.finalproject.domain.recommendation.entity.MealRecommendationItem;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine;
import ksu.finalproject.domain.recommendation.engine.RecommendationInput;
import ksu.finalproject.domain.recommendation.repository.DailyMealRecommendationRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecommendationPersistenceService {

    private final DailyMealRecommendationRepository dailyMealRecommendationRepository;
    private final UserRepository userRepository;
    private final CaloriesGoalRepository caloriesGoalRepository;
    private final FoodRepository foodRepository;

    @Transactional
    public void saveSuccess(
            RecommendationInput input,
            LocalDate recommendationDate,
            DietRecommendationEngine.DailyRecommendation recommendation
    ) {
        DietRecommendationEngine.DailyCaloriePlan plan = recommendation.caloriePlan();
        DailyMealRecommendation entity = loadOrCreateRecommendation(input.userId(), recommendationDate);
        CaloriesGoal caloriesGoal = loadCaloriesGoal(input.goalId());
        entity.updateSuccess(caloriesGoal, input.catalogVersion(), plan);
        entity.replaceItems(toItems(recommendation));
        dailyMealRecommendationRepository.save(entity);
    }

    @Transactional
    public void saveFailure(int userId, LocalDate recommendationDate, Integer goalId, String failureReason) {
        DailyMealRecommendation entity = loadOrCreateRecommendation(userId, recommendationDate);
        entity.updateFailure(loadCaloriesGoalOrNull(goalId), failureReason);
        entity.replaceItems(List.of());
        dailyMealRecommendationRepository.save(entity);
    }

    private DailyMealRecommendation loadOrCreateRecommendation(int userId, LocalDate recommendationDate) {
        return dailyMealRecommendationRepository.findByUserIdAndRecommendationDate((long) userId, recommendationDate)
                .orElseGet(() -> DailyMealRecommendation.builder()
                        .user(loadUserReference(userId))
                        .recommendationDate(recommendationDate)
                        .build());
    }

    private Users loadUserReference(int userId) {
        return userRepository.getReferenceById((long) userId);
    }

    private CaloriesGoal loadCaloriesGoal(Integer goalId) {
        if (goalId == null) {
            throw new IllegalArgumentException("goalId is required for successful recommendation persistence");
        }
        return caloriesGoalRepository.findById((long) goalId)
                .orElseThrow(() -> new IllegalArgumentException("calories goal not found: " + goalId));
    }

    private CaloriesGoal loadCaloriesGoalOrNull(Integer goalId) {
        if (goalId == null) {
            return null;
        }
        return caloriesGoalRepository.findById((long) goalId).orElse(null);
    }

    private List<MealRecommendationItem> toItems(DietRecommendationEngine.DailyRecommendation recommendation) {
        java.util.ArrayList<MealRecommendationItem> items = new java.util.ArrayList<>();
        for (DietRecommendationEngine.MealRecommendation meal : recommendation.meals()) {
            for (int i = 0; i < meal.foods().size(); i++) {
                DietRecommendationEngine.Food food = meal.foods().get(i);
                items.add(MealRecommendationItem.builder()
                        .food(foodRepository.findById((long) food.foodId()).orElse(null))
                        .mealType(toDomainMealType(meal.mealType()))
                        .foodNameSnapshot(food.displayName())
                        .groupNameSnapshot(food.groupName())
                        .displayOrder(i + 1)
                        .caloriesSnapshot(food.calories())
                        .build());
            }
        }
        return List.copyOf(items);
    }

    private ksu.finalproject.domain.food.entity.enums.MealType toDomainMealType(DietRecommendationEngine.MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> ksu.finalproject.domain.food.entity.enums.MealType.BREAKFAST;
            case LUNCH -> ksu.finalproject.domain.food.entity.enums.MealType.LUNCH;
            case DINNER -> ksu.finalproject.domain.food.entity.enums.MealType.DINNER;
        };
    }
}



