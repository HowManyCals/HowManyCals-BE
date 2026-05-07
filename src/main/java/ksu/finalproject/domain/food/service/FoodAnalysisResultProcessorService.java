package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.food.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
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

    private static final String DEFAULT_SERVING_AMOUNT = "1";

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

        ServingUnit servingUnit = resolveServingUnit(candidate, food);
        String servingUnitLabel = resolveServingUnitLabel(candidate, food, servingUnit);

        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(candidate.getFoodName())
                .servingUnit(servingUnit)
                .servingUnitLabel(servingUnitLabel)
                .build();
    }

    private ServingUnit resolveServingUnit(FoodAnalyzeCandidateDto candidate, Food food) {
        if (food != null && food.getServingUnit() != null) {
            return food.getServingUnit();
        }
        return candidate.getServingUnit();
    }

    private String resolveServingUnitLabel(FoodAnalyzeCandidateDto candidate, Food food, ServingUnit servingUnit) {
        if (food != null && food.getServingUnit() != null) {
            return food.getServingUnit().toDisplayLabel(food.getServingAmount());
        }
        if (StringUtils.hasText(candidate.getServingUnitLabel())) { //
            return candidate.getServingUnitLabel();
        }
        if (servingUnit != null) { // 제공 기준이 없을 경우
            return servingUnit.toDisplayLabel(DEFAULT_SERVING_AMOUNT);
        }
        return null;
    }
}
