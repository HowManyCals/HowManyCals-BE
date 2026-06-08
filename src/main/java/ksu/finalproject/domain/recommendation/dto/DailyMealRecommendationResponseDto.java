package ksu.finalproject.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.food.entity.enums.MealType;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DailyMealRecommendationResponseDto {

    @JsonProperty("recommendation_id")
    private Long recommendationId;

    @JsonProperty("recommendation_date")
    private LocalDate recommendationDate;

    @JsonProperty("status")
    private RecommendationJobStatus status;

    @JsonProperty("target_daily_kcal")
    private Integer targetDailyKcal;

    @JsonProperty("breakfast_target_kcal")
    private Integer breakfastTargetKcal;

    @JsonProperty("lunch_target_kcal")
    private Integer lunchTargetKcal;

    @JsonProperty("dinner_target_kcal")
    private Integer dinnerTargetKcal;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("meals")
    private Map<MealType, List<ItemDto>> meals;

    @Getter
    @Builder
    public static class ItemDto {
        @JsonProperty("food_id")
        private Long foodId;

        @JsonProperty("food_name")
        private String foodName;

        @JsonProperty("group_name")
        private String groupName;

        @JsonProperty("display_order")
        private Integer displayOrder;

        @JsonProperty("calories")
        private Integer calories;

        @JsonProperty("carbohydrate")
        private Double carbohydrate;

        @JsonProperty("protein")
        private Double protein;

        @JsonProperty("fat")
        private Double fat;
    }
}
