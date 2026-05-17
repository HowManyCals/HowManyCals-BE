package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.food.entity.enums.MealType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DailyFoodRecordResponseDto {

    @JsonProperty("date")
    private LocalDate date;

    // 하루 총 칼로리
    @JsonProperty("total_calories")
    private Double totalCalories;

    // 식사 종류별 기록 (BREAKFAST / LUNCH / DINNER / SNACK)
    @JsonProperty("meals")
    private Map<MealType, List<FoodRecordResponseDto>> meals;
}

