package ksu.finalproject.domain.food.dto.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDebugE2EResponseDto {
    @JsonProperty("ai_log_id")
    private Long aiLogId;

    @JsonProperty("llm")
    private FoodDebugLlmResponseDto llm;

    @JsonProperty("match_preview")
    private FoodDebugMatchResponseDto matchPreview;

    @JsonProperty("final_candidates")
    private List<FoodAnalyzeCandidateDto> finalCandidates;
}
