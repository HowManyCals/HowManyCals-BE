package ksu.finalproject.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class WeightRecordSaveRequestDto {
    @NotNull(message = "체중은 필수 입력 항목이에요.")
    @JsonProperty("weight")
    private Double weight;

    @NotNull(message = "날짜는 필수 입력 항목이에요.")
    @PastOrPresent(message = "미래 날짜는 기록할 수 없어요.")
    @JsonProperty("recorded_date")
    private LocalDate recordedDate;
}
