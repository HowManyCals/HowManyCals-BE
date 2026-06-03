package ksu.finalproject.domain.weight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class WeightYearlyRecordsResponseDto {

    @JsonProperty("records")
    private Map<Integer, List<WeightMonthlyRecordDto>> records;

    @Getter
    @Builder
    public static class WeightMonthlyRecordDto {

        @JsonProperty("date")
        private LocalDate date;

        @JsonProperty("weight")
        private Double weight;
    }
}

