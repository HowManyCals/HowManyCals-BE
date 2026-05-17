package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.food.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FoodAnalysisResultProcessorService {
    private final FoodRepository foodRepository;

    public FoodAnalysisResultDto process(FoodAnalysisResultDto result) {
        List<FoodAnalyzeCandidateDto> candidates = result.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return result;
        }

        List<String> foodNames = candidates.stream()
                .map(FoodAnalyzeCandidateDto::getFoodName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (foodNames.isEmpty()) {
            return result;
        }

        Map<String, Food> foodsByName = foodRepository.findAllByFoodNameIn(foodNames).stream()
                .collect(Collectors.toMap(
                        Food::getFoodName,
                        food -> food,
                        (first, second) -> first,
                        LinkedHashMap::new));

        List<FoodAnalyzeCandidateDto> processedCandidates = candidates.stream()
                .map(candidate -> processCandidate(candidate, foodsByName.get(candidate.getFoodName())))
                .toList();

        return FoodAnalysisResultDto.builder()
                .analysisStatus(result.getAnalysisStatus())
                .modelVersion(result.getModelVersion())
                .inferenceTimeMs(result.getInferenceTimeMs())
                .candidates(processedCandidates)
                .errorMessage(result.getErrorMessage())
                .aiLogId(result.getAiLogId())
                .build();
    }

    private FoodAnalyzeCandidateDto processCandidate(FoodAnalyzeCandidateDto candidate, Food food) {
        if (candidate == null) {
            return null;
        }

        String servingUnitLabel = resolveServingUnitLabel(candidate, food);

        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(candidate.getFoodName())
                .servingUnitLabel(servingUnitLabel)
                .build();
    }

    private String resolveServingUnitLabel(FoodAnalyzeCandidateDto candidate, Food food) {
        if (food != null && food.getServingUnit() != null)
            return food.getServingUnit().toDisplayLabel(food.getServingWeight());
        if (StringUtils.hasText(candidate.getServingUnitLabel()))
            return candidate.getServingUnitLabel();
        return null;
    }
}
