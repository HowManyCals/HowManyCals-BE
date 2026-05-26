package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.entity.DataSource;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodAnalysisResultProcessorService {
    private static final int SHORT_WORD_MAX_DISTANCE = 1;
    private static final int MEDIUM_WORD_MAX_DISTANCE = 2;
    private static final int LONG_WORD_MAX_DISTANCE = 3;

    private final FoodRepository foodRepository;

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

        // 활성화된 음식만 SELECT *
        List<Food> activeFoods = foodRepository.findAllByIsActiveTrue();
        Map<String, Food> exactFoodsByName = buildExactAliasMap(activeFoods);
        log.info("음식 DB 조회 완료 aiLogId={}, activeFoodCount={}, exactAliasCount={}",
                result.getAiLogId(),
                activeFoods.size(),
                exactFoodsByName.size());

        List<FoodAnalyzeCandidateDto> processedCandidates = candidates.stream()
                .map(candidate -> processCandidate(candidate, activeFoods, exactFoodsByName))
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

    // exact match용 alias 맵 구성
    private Map<String, Food> buildExactAliasMap(List<Food> foods) {
        Map<String, Food> aliasMap = new LinkedHashMap<>();

        for (Food food : foods) {
            for (String alias : buildSearchAliases(food)) {
                String normalizedAlias = normalize(alias);
                if (StringUtils.hasText(normalizedAlias)) {
                    aliasMap.putIfAbsent(normalizedAlias, food);
                }
            }
        }

        return aliasMap;
    }

    // 음식 엔티티 하나에 대해 검색 가능한 alias 목록 생성
    private List<String> buildSearchAliases(Food food) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();

        // 예시
        // foodName           mainCategory   subCategory    detailCategory
        // 카레라이스_돼지고기     밥류         카레라이스         돼지고기
        aliases.add(food.getFoodName());         // 카레라이스_돼지고기
        aliases.add(food.getDisplayName());      // 돼지고기 카레라이스
        aliases.add(food.getSubCategory());      // 카레라이스
        aliases.add(food.getDetailCategory());   // 돼지고기

        if (StringUtils.hasText(food.getSubCategory()) && StringUtils.hasText(food.getDetailCategory())) {
            aliases.add(food.getSubCategory() + "_" + food.getDetailCategory()); // 카레라이스_돼지고기
            aliases.add(food.getSubCategory() + " " + food.getDetailCategory()); // 카레라이스 돼지고기
            aliases.add(food.getDetailCategory() + " " + food.getSubCategory()); // 돼지고기 카레라이스
            aliases.add(food.getSubCategory() + food.getDetailCategory());       // 카레라이스돼지고기
            aliases.add(food.getDetailCategory() + food.getSubCategory());       // 돼지고기카레라이스
        }

        return new ArrayList<>(aliases);
    }

    // alias 기반으로 레벤슈타인 거리가 가장 가까운 fuzzy 후보 탐색
    private Food findBestFuzzyMatch(String targetName, List<Food> foods) {
        String normalizedTarget = normalize(targetName);

        if (!StringUtils.hasText(normalizedTarget)) return null;

        Food bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        String bestAlias = null;

        for (Food food : foods) {
            for (String alias : buildSearchAliases(food)) {
                String normalizedAlias = normalize(alias);

                if (!StringUtils.hasText(normalizedAlias)) {
                    continue;
                }

                int distance = calculateLevenshteinDistance(normalizedTarget, normalizedAlias);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = food;
                    bestAlias = normalizedAlias;
                }
            }
        }

        if (bestMatch == null || !isAcceptableDistance(normalizedTarget, bestAlias, bestDistance)) {
            return null;
        }

        return bestMatch;
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

    // 레벤슈타인 거리 기반 fuzzy 매칭 허용 범위 판정
    private boolean isAcceptableDistance(String normalizedTarget, String normalizedAlias, int distance) {
        if (!StringUtils.hasText(normalizedTarget) || !StringUtils.hasText(normalizedAlias)) {
            return false;
        }

        int longestLength = Math.max(normalizedTarget.length(), normalizedAlias.length());
        if (longestLength <= 4) {
            return distance <= SHORT_WORD_MAX_DISTANCE;
        }
        if (longestLength <= 8) {
            return distance <= MEDIUM_WORD_MAX_DISTANCE;
        }
        return distance <= LONG_WORD_MAX_DISTANCE;
    }

    // 두 문자열의 레벤슈타인 거리 계산
    private int calculateLevenshteinDistance(String source, String target) {
        int[][] dp = new int[source.length() + 1][target.length() + 1];

        // 베이스 케이스 삽입
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

    // 후보 1건을 exact/fuzzy/unmatched 분기 후 FE 응답 후보 DTO로 변환
    private FoodAnalyzeCandidateDto processCandidate(AiAnalysisCandidateDto candidate, List<Food> activeFoods, Map<String, Food> exactFoodsByName) {
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
        if (exactFood != null) {
            log.info("음식 후보 exact 매칭 성공 candidateFoodName={}, matchedFoodName={}",
                    candidate.getFoodName(),
                    exactFood.getFoodName());
        } else {
            log.info("음식 후보 exact 매칭 실패 candidateFoodName={}", candidate.getFoodName());
        }

        Food fuzzyFood = exactFood == null ? findBestFuzzyMatch(candidate.getFoodName(), activeFoods) : null;
        if (exactFood == null && fuzzyFood != null) {
            log.info("음식 후보 fuzzy 매칭 성공 candidateFoodName={}, matchedFoodName={}",
                    candidate.getFoodName(),
                    fuzzyFood.getFoodName());
        }

        DataSource dataSource;
        Food resolvedFood;

        if (exactFood != null) {
            // DB_EXACT : 완전 매칭
            dataSource = DataSource.DB_EXACT;
            resolvedFood = exactFood;
        } else if (fuzzyFood != null){
            // DB_FUZZY : 유사 매칭
            dataSource = DataSource.DB_FUZZY;
            resolvedFood = fuzzyFood;
        } else {
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
