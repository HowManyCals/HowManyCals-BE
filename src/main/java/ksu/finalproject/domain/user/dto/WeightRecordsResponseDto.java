package ksu.finalproject.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class WeightRecordsResponseDto {

    @JsonProperty("records")
    private List<WeightRecordDto> records;

    @Getter
    @Builder
    public static class WeightRecordDto {

        @JsonProperty("date")
        private LocalDate date;

        @JsonProperty("weight")
        private Double weight;
    }
}

