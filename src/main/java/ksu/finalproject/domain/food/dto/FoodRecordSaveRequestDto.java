package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import ksu.finalproject.domain.food.entity.enums.MealType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class FoodRecordSaveRequestDto {

    // AI 분석에서 이어진 경우 (수동 입력 시 null)
    @JsonProperty("ai_log_id")
    private Long aiLogId;

    // DB에 있는 음식인 경우 (DB에 없는 음식 수동 입력 시 null)
    @JsonProperty("food_id")
    private Long foodId;

    @NotBlank(message = "음식명은 필수 입력 항목이에요.")
    @JsonProperty("food_name")
    private String foodName;

    @NotNull(message = "날짜는 필수 입력 항목이에요.")
    @PastOrPresent(message = "미래 날짜는 기록할 수 없어요.")
    @JsonProperty("eaten_date")
    private LocalDate eatenDate;

    @NotNull(message = "식사 종류는 필수 입력 항목이에요.")
    @JsonProperty("meal_type")
    private MealType mealType;

    // 섭취량(g) - 현재는 FE에서 표시하지는 않지만, 확장 고려해서 nullable
    @JsonProperty("amount_g")
    private Double amountG;

    @NotNull(message = "칼로리는 필수 입력 항목이에요.")
    @JsonProperty("calories")
    private Double calories;

    @JsonProperty("carbohydrate")
    private Double carbohydrate;

    @JsonProperty("protein")
    private Double protein;

    @JsonProperty("fat")
    private Double fat;
}

