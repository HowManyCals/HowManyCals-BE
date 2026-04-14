package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.food.entity.enums.AnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAnalysisResultDto {
    @JsonProperty("analysis_status")
    private AnalysisStatus analysisStatus;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("inference_time_ms")
    private Long inferenceTimeMs;

    @Builder.Default
    private List<FoodAnalyzeCandidateDto> candidates = Collections.emptyList();

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("ai_log_id")
    private Long aiLogId;
}

