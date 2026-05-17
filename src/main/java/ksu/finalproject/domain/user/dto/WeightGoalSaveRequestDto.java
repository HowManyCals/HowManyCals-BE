package ksu.finalproject.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WeightGoalSaveRequestDto {
    @NotNull(message = "목표 체중은 필수 입력 항목이에요.")
    @JsonProperty("target_weight")
    private Double targetWeight;
}
