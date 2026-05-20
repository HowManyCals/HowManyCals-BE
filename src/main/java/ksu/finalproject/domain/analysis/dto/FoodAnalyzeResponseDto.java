package ksu.finalproject.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FoodAnalyzeResponseDto {
    // 분석 요청 상태
    private String status;

    @JsonProperty("ai_log_id")
    private Long aiLogId;
}

