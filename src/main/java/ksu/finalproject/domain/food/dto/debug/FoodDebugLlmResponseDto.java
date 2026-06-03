package ksu.finalproject.domain.food.dto.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDebugLlmResponseDto {
    @JsonProperty("ai_log_id")
    private Long aiLogId;

    @JsonProperty("recognized_name")
    private String recognizedName;

    @JsonProperty("main_category")
    private String mainCategory;

    @JsonProperty("base_food")
    private String baseFood;

    @JsonProperty("modifiers")
    private List<String> modifiers;

    @JsonProperty("search_terms")
    private List<String> searchTerms;

    @JsonProperty("normalized_recognized_name")
    private String normalizedRecognizedName;

    @JsonProperty("normalized_main_category")
    private String normalizedMainCategory;

    @JsonProperty("normalized_base_food")
    private String normalizedBaseFood;

    @JsonProperty("normalized_modifiers")
    private List<String> normalizedModifiers;

    @JsonProperty("normalized_search_terms")
    private List<String> normalizedSearchTerms;
}
