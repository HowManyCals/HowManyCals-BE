package ksu.finalproject.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeightSummaryResponseDto {

    @JsonProperty("current_weight") // (최신) 현재 체중
    private Double currentWeight;

    @JsonProperty("target_weight") // (최신) 목표 체중
    private Double targetWeight;
}
