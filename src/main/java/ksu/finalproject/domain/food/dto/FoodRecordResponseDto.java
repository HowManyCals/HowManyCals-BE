package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.food.entity.FoodRecord;
import ksu.finalproject.domain.food.entity.enums.MealType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class FoodRecordResponseDto {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("food_id")
    private Long foodId;

    @JsonProperty("ai_log_id")
    private Long aiLogId;

    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("eaten_date")
    private LocalDate eatenDate;

    @JsonProperty("meal_type")
    private MealType mealType;

    @JsonProperty("amount_g")
    private Double amountG;

    @JsonProperty("calories")
    private Double calories;

    @JsonProperty("carbohydrate")
    private Double carbohydrate;

    @JsonProperty("protein")
    private Double protein;

    @JsonProperty("fat")
    private Double fat;

    public static FoodRecordResponseDto from(FoodRecord record) {
        return FoodRecordResponseDto.builder()
                .recordId(record.getId())
                .foodId(record.getFood() != null ? record.getFood().getId() : null)
                .aiLogId(record.getAiAnalysisLog() != null ? record.getAiAnalysisLog().getId() : null)
                .foodName(record.getFoodName())
                .eatenDate(record.getEatenDate())
                .mealType(record.getMealType())
                .calories(record.getCalories())
                .carbohydrate(record.getCarbohydrate())
                .protein(record.getProtein())
                .fat(record.getFat())
                .build();
    }
}

