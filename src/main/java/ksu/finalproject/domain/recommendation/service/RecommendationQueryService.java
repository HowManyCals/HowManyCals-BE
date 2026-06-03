package ksu.finalproject.domain.recommendation.service;

import ksu.finalproject.domain.food.entity.enums.MealType;
import ksu.finalproject.domain.recommendation.dto.DailyMealRecommendationResponseDto;
import ksu.finalproject.domain.recommendation.entity.DailyMealRecommendation;
import ksu.finalproject.domain.recommendation.entity.MealRecommendationItem;
import ksu.finalproject.domain.recommendation.repository.DailyMealRecommendationRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationQueryService {

    private final DailyMealRecommendationRepository dailyMealRecommendationRepository;

    @Transactional(readOnly = true)
    public DailyMealRecommendationResponseDto getDailyRecommendation(Long userId, LocalDate recommendationDate)
            throws CustomException {
        DailyMealRecommendation recommendation = loadRecommendation(userId, recommendationDate);
        Map<MealType, List<DailyMealRecommendationResponseDto.ItemDto>> meals = new LinkedHashMap<>();
        List<MealRecommendationItem> items = sortItems(recommendation.getItems());
        for (MealType mealType : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
            meals.put(mealType, items.stream()
                    .filter(item -> item.getMealType() == mealType)
                    .map(item -> DailyMealRecommendationResponseDto.ItemDto.builder()
                            .foodId(item.getFood() != null ? item.getFood().getId() : null)
                            .foodName(item.getFoodNameSnapshot())
                            .groupName(item.getGroupNameSnapshot())
                            .displayOrder(item.getDisplayOrder())
                            .calories(item.getCaloriesSnapshot())
                            .build())
                    .toList());
        }

        return DailyMealRecommendationResponseDto.builder()
                .recommendationId(recommendation.getId())
                .recommendationDate(recommendation.getRecommendationDate())
                .status(recommendation.getStatus())
                .targetDailyKcal(recommendation.getTargetDailyKcal())
                .breakfastTargetKcal(recommendation.getBreakfastTargetKcal())
                .lunchTargetKcal(recommendation.getLunchTargetKcal())
                .dinnerTargetKcal(recommendation.getDinnerTargetKcal())
                .failureReason(recommendation.getFailureReason())
                .meals(meals)
                .build();
    }

    private DailyMealRecommendation loadRecommendation(Long userId, LocalDate recommendationDate) throws CustomException {
        return dailyMealRecommendationRepository.findWithItemsByUserIdAndRecommendationDate(userId, recommendationDate)
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_DAILY_MEAL_RECOMMENDATION));
    }

    private List<MealRecommendationItem> sortItems(List<MealRecommendationItem> items) {
        return items.stream()
                .sorted(Comparator.comparingInt((MealRecommendationItem item) -> mealTypeOrder(item.getMealType()))
                        .thenComparing(MealRecommendationItem::getDisplayOrder))
                .toList();
    }

    private int mealTypeOrder(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> 1;
            case LUNCH -> 2;
            case DINNER -> 3;
            case SNACK -> 4;
        };
    }
}
