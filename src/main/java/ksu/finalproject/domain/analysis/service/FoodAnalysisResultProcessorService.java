package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisRequestDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
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
    private static final int SHORT_WORD_MAX_DISTANCE = 1;
    private static final int MEDIUM_WORD_MAX_DISTANCE = 2;
    private static final int LONG_WORD_MAX_DISTANCE = 3;

    private final FoodAliasRepository foodAliasRepository;
    private final FoodAliasSyncService foodAliasSyncService;
    private final LlmService llmService;

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
                .map(candidate -> processCandidate(result.getAiLogId(), candidate, exactFoodsByName))
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

    // 후보 1건을 exact/llm/unmatched 분기 후 FE 응답 후보 DTO로 변환
    private FoodAnalyzeCandidateDto processCandidate(Long aiLogId, AiAnalysisCandidateDto candidate, Map<String, Food> exactFoodsByName) {
        log.info("음식 후보 검사 시작 foodName={}, confidenceScore={}",
                candidate.getFoodName(),
                candidate.getConfidenceScore());

        // 신뢰도가 0.6 미만이면 exact 매칭을 생략하고 바로 LLM fallback
        if (candidate.getConfidenceScore() != null && candidate.getConfidenceScore() < 0.6) {
            log.info("음식 후보 exact 매칭 생략 - LLM fallback 바로 진행 aiLogId={}, foodName={}, confidenceScore={}",
                    aiLogId,
                    candidate.getFoodName(),
                    candidate.getConfidenceScore());

            FoodAnalyzeCandidateDto llmCandidate = processWithLlmFallback(aiLogId, candidate);
            if (llmCandidate != null) {
                return llmCandidate;
            }

            log.warn("음식 후보 LLM fallback 종료 - 대체 결과 없음 aiLogId={}, foodName={}", aiLogId, candidate.getFoodName());
            return buildUnmatchedCandidate(candidate);
        }

        Food exactFood = exactFoodsByName.get(normalize(candidate.getFoodName()));

        if (exactFood != null) {
            log.info("음식 후보 exact 매칭 성공 candidateFoodName={}, matchedFoodName={}",
                    candidate.getFoodName(),
                    exactFood.getFoodName());
        } else {
            log.info("음식 후보 exact 매칭 실패 candidateFoodName={}", candidate.getFoodName());
            return buildUnmatchedCandidate(candidate);
        }

        return buildMatchedCandidate(candidate, exactFood, DataSource.DB_EXACT);
    }

    private FoodAnalyzeCandidateDto processWithLlmFallback(Long aiLogId, AiAnalysisCandidateDto candidate) {
        LlmFoodAnalysisResponseDto llmResponse = llmService.analyzeFromLlm(
                LlmFoodAnalysisRequestDto.builder()
                        .aiLogId(aiLogId)
                        .originalFoodName(candidate.getFoodName())
                        .confidenceScore(candidate.getConfidenceScore())
                        .build()
        );

        if (llmResponse == null || llmResponse.getCandidates() == null || llmResponse.getCandidates().isEmpty()) {
            return null;
        }

        List<String> normalizedCandidates = llmResponse.getCandidates().stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .toList();

        Map<String, FoodAlias> aliasesByNormalizedName = foodAliasRepository.findAllActiveWithFoodByNormalizedAliasIn(normalizedCandidates)
                .stream()
                .collect(Collectors.toMap(
                        FoodAlias::getNormalizedAlias,
                        alias -> alias,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        FoodAlias matchedAlias = null;
        String matchedCandidateName = null;
        for (String llmCandidateName : llmResponse.getCandidates()) {
            FoodAlias alias = aliasesByNormalizedName.get(normalize(llmCandidateName));
            if (alias != null) {
                matchedAlias = alias;
                matchedCandidateName = llmCandidateName;
                break;
            }
        }

        if (matchedAlias != null) {
            log.info("LLM 음식명 exact 매칭 성공 aiLogId={}, llmFoodName={}, matchedFoodName={}",
                    aiLogId,
                    matchedCandidateName,
                    matchedAlias.getFood().getFoodName());

            return buildMatchedCandidate(
                    AiAnalysisCandidateDto.builder()
                            .aiModelIndex(candidate.getAiModelIndex())
                            .confidenceScore(candidate.getConfidenceScore())
                            .foodName(matchedCandidateName)
                            .servingUnitLabel(candidate.getServingUnitLabel())
                            .build(),
                    matchedAlias.getFood(),
                    DataSource.DB_FUZZY
            );
        }

        log.warn("LLM 음식명 exact 매칭 실패 aiLogId={}, llmCandidates={}", aiLogId, llmResponse.getCandidates());
        log.info("LLM 후보 레벤슈타인 fallback 시작 aiLogId={}, llmCandidates={}", aiLogId, llmResponse.getCandidates());

        LevenshteinMatch bestMatch = findBestLevenshteinMatch(llmResponse.getCandidates());
        if (bestMatch != null) {
            log.info("LLM 후보 레벤슈타인 매칭 성공 aiLogId={}, llmFoodName={}, matchedAlias={}, matchedFoodName={}, distance={}",
                    aiLogId,
                    bestMatch.candidateName(),
                    bestMatch.alias().getAliasName(),
                    bestMatch.alias().getFood().getFoodName(),
                    bestMatch.distance());

            return buildMatchedCandidate(
                    AiAnalysisCandidateDto.builder()
                            .aiModelIndex(candidate.getAiModelIndex())
                            .confidenceScore(candidate.getConfidenceScore())
                            .foodName(bestMatch.candidateName())
                            .servingUnitLabel(candidate.getServingUnitLabel())
                            .build(),
                    bestMatch.alias().getFood(),
                    DataSource.DB_FUZZY
            );
        }

        log.warn("LLM 후보 레벤슈타인 매칭 실패 aiLogId={}, llmCandidates={}", aiLogId, llmResponse.getCandidates());
        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(llmResponse.getCandidates().get(0))
                .servingUnitLabel(candidate.getServingUnitLabel())
                .dataSource(DataSource.UNMATCHED)
                .build();
    }

    private LevenshteinMatch findBestLevenshteinMatch(List<String> llmCandidates) {
        List<FoodAlias> activeAliases = foodAliasRepository.findAllActiveWithFood();
        LevenshteinMatch bestMatch = null;

        for (String llmCandidate : llmCandidates) {
            String normalizedCandidate = normalize(llmCandidate);
            if (!StringUtils.hasText(normalizedCandidate)) {
                continue;
            }

            for (FoodAlias alias : activeAliases) {
                String normalizedAlias = alias.getNormalizedAlias();
                if (!StringUtils.hasText(normalizedAlias)) {
                    continue;
                }

                int distance = calculateLevenshteinDistance(normalizedCandidate, normalizedAlias);
                if (!isAcceptableDistance(normalizedCandidate, normalizedAlias, distance)) {
                    continue;
                }

                if (bestMatch == null || distance < bestMatch.distance()) {
                    bestMatch = new LevenshteinMatch(llmCandidate, alias, distance);
                }
            }
        }

        return bestMatch;
    }

    private boolean isAcceptableDistance(String source, String target, int distance) {
        int longestLength = Math.max(source.length(), target.length());
        if (longestLength <= 4) {
            return distance <= SHORT_WORD_MAX_DISTANCE;
        }
        if (longestLength <= 8) {
            return distance <= MEDIUM_WORD_MAX_DISTANCE;
        }
        return distance <= LONG_WORD_MAX_DISTANCE;
    }

    private int calculateLevenshteinDistance(String source, String target) {
        int[][] dp = new int[source.length() + 1][target.length() + 1];

        for (int i = 0; i <= source.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= target.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= source.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                int substitutionCost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + substitutionCost
                );
            }
        }

        return dp[source.length()][target.length()];
    }

    private record LevenshteinMatch(String candidateName, FoodAlias alias, int distance) {
    }

    private FoodAnalyzeCandidateDto buildMatchedCandidate(AiAnalysisCandidateDto candidate, Food resolvedFood, DataSource dataSource) {
        String servingUnitLabel = resolveServingUnitLabel(candidate, resolvedFood);
        Double servingKcal = null;
        Double carbohydrate = null;
        Double protein = null;
        Double fat = null;
        String responseFoodName = candidate.getFoodName();

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

    private FoodAnalyzeCandidateDto buildUnmatchedCandidate(AiAnalysisCandidateDto candidate) {
        log.warn("음식 후보 DB 매칭 실패 candidateFoodName={}", candidate.getFoodName());
        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(candidate.getFoodName())
                .servingUnitLabel(candidate.getServingUnitLabel())
                .dataSource(DataSource.UNMATCHED)
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
