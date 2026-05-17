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

    @JsonProperty("serving_unit_label") // 900g, 200ml 이런 식으로 내려줌
    private String servingUnitLabel;
}

