package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.analysis.entity.DataSource;
import ksu.finalproject.domain.food.entity.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManualFoodSearchItemDto {

	@JsonProperty("food_id")
	private Long foodId;

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

	@JsonProperty("source_type")
	private SourceType sourceType;

	@JsonProperty("data_source")
	private DataSource dataSource;
}

