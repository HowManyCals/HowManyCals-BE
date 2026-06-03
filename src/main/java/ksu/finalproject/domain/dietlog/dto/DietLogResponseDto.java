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

    @JsonProperty("current_streak_days")
    private long currentStreakDays;

    @JsonProperty("max_streak_days")
    private long maxStreakDays;
}
