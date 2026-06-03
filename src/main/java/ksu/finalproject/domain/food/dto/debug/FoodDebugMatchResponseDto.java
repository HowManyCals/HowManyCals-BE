package ksu.finalproject.domain.food.dto.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDebugMatchResponseDto {
    @JsonProperty("request")
    private FoodDebugLlmResponseDto request;

    @JsonProperty("candidate_count")
    private Integer candidateCount;

    @JsonProperty("candidates")
    private List<FoodDebugMatchCandidateDto> candidates;
}
