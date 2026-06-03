package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM(Gemini)이 구조화된 음식 분류 결과를 반환하는 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmFoodAnalysisResponseDto {

    @JsonProperty("main_category")
    private String mainCategory;

    @JsonProperty("base_food")
    private String baseFood;

    @JsonProperty("modifiers")
    private List<String> modifiers;
}

