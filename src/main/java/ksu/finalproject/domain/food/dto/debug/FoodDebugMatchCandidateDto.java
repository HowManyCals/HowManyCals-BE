package ksu.finalproject.domain.food.dto.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDebugMatchCandidateDto {
    @JsonProperty("food_id")
    private Long foodId;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("main_category")
    private String mainCategory;

    @JsonProperty("sub_category")
    private String subCategory;

    @JsonProperty("detail_category")
    private String detailCategory;

    @JsonProperty("score")
    private Integer score;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("serving_kcal")
    private Double servingKcal;

    @JsonProperty("serving_unit_label")
    private String servingUnitLabel;
}
