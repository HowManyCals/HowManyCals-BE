package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAnalyzeCandidateDto {
    @JsonProperty("ai_model_index")
    private Integer aiModelIndex;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("serving_unit") // 공기, 인분, 그릇 등
    private ServingUnit servingUnit;

    @JsonProperty("serving_unit_label") // 1공기, 2공기에서의 1, 2 같은 수량을 의미
    private String servingUnitLabel; // 소, 중, 대의 표현을 고려하여 string으로 지정
}

