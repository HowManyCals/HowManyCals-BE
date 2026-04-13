package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FoodAnalyzeResponseDto {
    private String status;

    @JsonProperty("ai_log_id")
    private Long aiLogId;
}

