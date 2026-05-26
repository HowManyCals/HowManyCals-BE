package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.entity.DataSource;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.FoodAlias;
import ksu.finalproject.domain.food.repository.FoodAliasRepository;
import ksu.finalproject.domain.food.service.FoodAliasSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodAnalysisResultProcessorService {
    private final FoodAliasRepository foodAliasRepository;
    private final FoodAliasSyncService foodAliasSyncService;

    // AI 콜백 원본 DTO를 FE 응답 DTO로 가공
    public FoodAnalysisResultDto process(AiAnalysisCallbackDto result) {
        List<AiAnalysisCandidateDto> candidates = result.getCandidates();
        log.info("음식 후보 후처리 시작 aiLogId={}, candidateCount={}",
                result.getAiLogId(),
                candidates != null ? candidates.size() : 0);

        if (candidates == null || candidates.isEmpty()) {
            log.info("음식 후보 후처리 종료 - 후보 없음 aiLogId={}", result.getAiLogId());
            return toResponseDto(result, Collections.emptyList());
        }

        foodAliasSyncService.syncIfNeeded();

        List<String> normalizedCandidateNames = candidates.stream()
                .map(AiAnalysisCandidateDto::getFoodName)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, Food> exactFoodsByName = foodAliasRepository.findAllActiveWithFoodByNormalizedAliasIn(normalizedCandidateNames)
                .stream()
                .collect(Collectors.toMap(
                        FoodAlias::getNormalizedAlias,
                        FoodAlias::getFood,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        log.info("음식 alias exact 조회 완료 aiLogId={}, candidateCount={}, exactMatchCount={}",
                result.getAiLogId(),
                normalizedCandidateNames.size(),
                exactFoodsByName.size());

        List<FoodAnalyzeCandidateDto> processedCandidates = candidates.stream()
                .map(candidate -> processCandidate(candidate, exactFoodsByName))
                .filter(Objects::nonNull)
                .toList();

        log.info("음식 후보 후처리 완료 aiLogId={}, processedCandidateCount={}",
                result.getAiLogId(),
                processedCandidates.size());

        return toResponseDto(result, processedCandidates);
    }

    // FE에 내려줄 최종 응답 DTO 생성
    private FoodAnalysisResultDto toResponseDto(AiAnalysisCallbackDto result, List<FoodAnalyzeCandidateDto> candidates) {
        return FoodAnalysisResultDto.builder()
                .analysisStatus(result.getAnalysisStatus())
                .modelVersion(result.getModelVersion())
                .inferenceTimeMs(result.getInferenceTimeMs())
                .candidates(candidates)
                .errorMessage(result.getErrorMessage())
                .aiLogId(result.getAiLogId())
                .build();
    }

    // 공백/언더스코어 제거 후 비교 가능한 문자열로 정규화
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("[_\\s]+", "").toLowerCase();
        // 모든 공백 제거
        // - \s (공백문자 whitespace)
        // - +는 1개 이상 반복된 것 모두
    }

    // 후보 1건을 exact/unmatched 분기 후 FE 응답 후보 DTO로 변환
    private FoodAnalyzeCandidateDto processCandidate(AiAnalysisCandidateDto candidate, Map<String, Food> exactFoodsByName) {
        log.info("음식 후보 검사 시작 foodName={}, confidenceScore={}",
                candidate.getFoodName(),
                candidate.getConfidenceScore());

        // 신뢰도가 0.6이하라면 LLM fallback
        if (candidate.getConfidenceScore() != null && candidate.getConfidenceScore() <= 0.6){
            log.info("음식 후보 검사 종료 - LLM fallback 대상 foodName={}, confidenceScore={}",
                    candidate.getFoodName(),
                    candidate.getConfidenceScore());
            return null; // llm 처리 로직
        }

        Food exactFood = exactFoodsByName.get(normalize(candidate.getFoodName()));
        DataSource dataSource;
        Food resolvedFood;

        if (exactFood != null) {
            log.info("음식 후보 exact 매칭 성공 candidateFoodName={}, matchedFoodName={}",
                    candidate.getFoodName(),
                    exactFood.getFoodName());
            // DB_EXACT : 완전 매칭
            dataSource = DataSource.DB_EXACT;
            resolvedFood = exactFood;
        } else {
            log.info("음식 후보 exact 매칭 실패 candidateFoodName={}", candidate.getFoodName());
            // UNMATCHED: 지원하지 않는 음식
            dataSource = DataSource.UNMATCHED;
            resolvedFood = null;
            log.warn("음식 후보 DB 매칭 실패 candidateFoodName={}", candidate.getFoodName());
        }

        String servingUnitLabel = resolveServingUnitLabel(candidate, resolvedFood);
        String responseFoodName = candidate.getFoodName();
        Double servingKcal = null;
        Double carbohydrate = null;
        Double protein = null;
        Double fat = null;

        if (resolvedFood != null) {
            responseFoodName = resolvedFood.getDisplayName();
            servingKcal = resolvedFood.getServingKcal();
            carbohydrate = resolvedFood.getCarbohydrate();
            protein = resolvedFood.getProtein();
            fat = resolvedFood.getFat();
        }

        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(responseFoodName)
                .servingKcal(servingKcal)
                .carbohydrate(carbohydrate)
                .protein(protein)
                .fat(fat)
                .servingUnitLabel(servingUnitLabel)
                .dataSource(dataSource)
                .build();
    }

    // servingUnit + servingWeight 방식
    // 예: ml + 300 => 300ml
    private String resolveServingUnitLabel(AiAnalysisCandidateDto candidate, Food food) {
        if (food != null && food.getServingUnit() != null)
            return food.getServingUnit().toDisplayLabel(food.getServingWeight());
        if (StringUtils.hasText(candidate.getServingUnitLabel()))
            return candidate.getServingUnitLabel();
        return null;
    }
}
