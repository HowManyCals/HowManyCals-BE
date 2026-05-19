package ksu.finalproject.domain.dietlog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Builder;

@Getter
@Builder
public class DietLogResponseDto {
    @JsonProperty("record_days")
    private long recordDays;

    @JsonProperty("goal_achieved_days")
    private long goalAchievedDays;

    @JsonProperty("streak_days")
    private long streakDays;
}
