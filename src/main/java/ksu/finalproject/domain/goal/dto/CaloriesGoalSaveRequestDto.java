package ksu.finalproject.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CaloriesGoalSaveRequestDto {
    @Max(value = 5000, message = "목표 칼로리는 5000 이하로 입력해주세요.")
    @JsonProperty("goal_calories")
    private Double goalCalories;
}
