package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LLM(Gemini)이 음식명 추정 후 반환하는 응답 DTO
 * 영양정보는 신뢰도가 불명확하므로 요청하지 않음
 * 음식명과 confidence만 반환받음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmFoodAnalysisResponseDto {

    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("confidence_score")
    private Double confidenceScore;
}

