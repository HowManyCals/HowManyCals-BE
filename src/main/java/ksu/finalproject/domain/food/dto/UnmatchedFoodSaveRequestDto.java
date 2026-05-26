package ksu.finalproject.domain.food.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FE에서 미매칭 음식 피드백을 제출할 때 사용하는 DTO
 * 사용자가 "지원하지 않는 음식이에요" 화면에서 음식명을 입력하면 해당 값을 받음
 */
@Getter
@NoArgsConstructor
public class UnmatchedFoodSaveRequestDto {

    /** LLM이 추정한 음식명 (또는 사용자가 직접 입력한 음식명) */
    @NotBlank(message = "음식명은 필수 입력 항목이에요.")
    @JsonProperty("llm_food_name")
    private String llmFoodName;
}

