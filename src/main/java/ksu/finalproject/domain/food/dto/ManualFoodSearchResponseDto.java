package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ManualFoodSearchResponseDto {

    @JsonProperty("query")
    private String query;

    @JsonProperty("items")
    private List<ManualFoodSearchItemDto> items;
}

