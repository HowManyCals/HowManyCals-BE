package ksu.finalproject.domain.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class FoodImageAnalyzeResponseDto {
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Map<String, Object> analysisResult;
}

