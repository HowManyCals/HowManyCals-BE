package ksu.finalproject.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobResult;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecommendationJobResponseDto {

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("status")
    private RecommendationJobStatus status;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("elapsed_millis")
    private Long elapsedMillis;

    public static RecommendationJobResponseDto from(RecommendationJobResult result) {
        return RecommendationJobResponseDto.builder()
                .userId(result.userId())
                .status(result.status())
                .failureReason(result.failureReason())
                .elapsedMillis(result.elapsed().toMillis())
                .build();
    }
}
