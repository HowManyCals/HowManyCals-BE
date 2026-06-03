package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.analysis.entity.DataSource;
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

    @JsonProperty("food_id")
    private Long foodId;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("recognized_name")
    private String recognizedName;

    @JsonProperty("matched_food_name")
    private String matchedFoodName;

    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("serving_kcal")
    private Double servingKcal;

    @JsonProperty("carbohydrate")
    private Double carbohydrate;

    @JsonProperty("protein")
    private Double protein;

    @JsonProperty("fat")
    private Double fat;

    @JsonProperty("serving_unit_label")
    private String servingUnitLabel;

    @JsonProperty("data_source")
    private DataSource dataSource;
}
