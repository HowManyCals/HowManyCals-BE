package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * LLM(Gemini)이 음식명 추정 후 반환하는 응답 DTO
 * 가장 가능성 높은 음식 후보 3개를 반환받음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmFoodAnalysisResponseDto {

    @JsonProperty("candidates")
    @Builder.Default
    private List<String> candidates = Collections.emptyList();
}

