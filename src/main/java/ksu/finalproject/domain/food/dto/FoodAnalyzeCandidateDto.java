package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}

