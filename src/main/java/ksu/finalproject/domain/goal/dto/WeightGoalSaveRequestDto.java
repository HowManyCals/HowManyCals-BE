package ksu.finalproject.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WeightGoalSaveRequestDto {
    @Min(value = 20, message = "목표 체중은 20kg 이상으로 입력해주세요.")
    @Max(value = 300, message = "목표 체중은 300kg 이하로 입력해주세요.")
    @JsonProperty("goal_weight")
    private Double goalWeight;
}
