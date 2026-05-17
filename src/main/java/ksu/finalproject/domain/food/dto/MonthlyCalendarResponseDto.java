package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Builder
public class MonthlyCalendarResponseDto {

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("month")
    private Integer month;

    // 날짜 → 하루 총 칼로리 (기록 없는 날짜는 포함 안 됨)
    @JsonProperty("daily_calories")
    private Map<LocalDate, Double> dailyCalories;
}

