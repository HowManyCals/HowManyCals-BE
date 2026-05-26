package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 추론 서버의 confidence_score가 0.6 이하일 경우,
 * LLM(Gemini)에 음식명 추정을 요청할 때 사용하는 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmFoodAnalysisRequestDto {

    /** AI 분석 로그 ID - 어떤 분석 요청에서 LLM 폴백이 발생했는지 추적 */
    @JsonProperty("ai_log_id")
    private Long aiLogId;

    /** AI 추론 서버가 반환한 원본 낮은 신뢰도 후보 음식명 */
    @JsonProperty("original_food_name")
    private String originalFoodName;

    /** AI 추론 서버가 반환한 confidence_score */
    @JsonProperty("confidence_score")
    private Double confidenceScore;
}

