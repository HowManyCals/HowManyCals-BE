package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisRequestDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
import ksu.finalproject.domain.analysis.entity.DataSource;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.UnmatchedFoodLog;
import ksu.finalproject.domain.food.repository.UnmatchedFoodLogRepository;
import ksu.finalproject.domain.food.service.FoodMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodAnalysisResultProcessorService {
    private static final String UNMATCHED_FOOD_NAME = "UNMATCHED";
    private static final int TOP_CANDIDATE_LIMIT = 15;
    private static final int AMBIGUOUS_RESPONSE_LIMIT = 3;
    private static final int AUTO_CONFIRM_SCORE_THRESHOLD = 85;
    private static final int AUTO_CONFIRM_GAP_THRESHOLD = 15;

    private final FoodMatchService foodMatchService;
    private final LlmService llmService;
    private final UnmatchedFoodLogRepository unmatchedFoodLogRepository;

    public FoodAnalysisResultDto process(AiAnalysisCallbackDto result) {
        List<AiAnalysisCandidateDto> candidates = result.getCandidates();
        log.info("음식 후보 후처리 시작 aiLogId={}, candidateCount={}",
                result.getAiLogId(),
                candidates != null ? candidates.size() : 0);

        if (candidates == null || candidates.isEmpty()) {
            log.info("음식 후보 후처리 종료 - 후보 없음 aiLogId={}", result.getAiLogId());
            return toResponseDto(result, Collections.emptyList());
        }

        AiAnalysisCandidateDto primaryCandidate = candidates.get(0);
        List<FoodAnalyzeCandidateDto> processedCandidates = processPrimaryCandidate(result.getAiLogId(), primaryCandidate);

        log.info("음식 후보 후처리 완료 aiLogId={}, processedCandidateCount={}",
                result.getAiLogId(),
                processedCandidates.size());

        return toResponseDto(result, processedCandidates);
    }

    private List<FoodAnalyzeCandidateDto> processPrimaryCandidate(Long aiLogId, AiAnalysisCandidateDto candidate) {
        log.info("음식 후보 검사 시작 aiLogId={}, foodName={}, confidenceScore={}",
                aiLogId,
                candidate.getFoodName(),
                candidate.getConfidenceScore());

        if (candidate.getConfidenceScore() != null && candidate.getConfidenceScore() < 0.6) {
            return processWithLlmBranch(aiLogId, candidate);
        }

        List<FoodMatchService.MatchResult> matches = foodMatchService.matchFoods(
                null,
                candidate.getFoodName(),
                List.of(),
                TOP_CANDIDATE_LIMIT
        );
        if (matches.isEmpty()) {
            log.warn("AI 후보 food 직접 매칭 실패 aiLogId={}, foodName={}", aiLogId, candidate.getFoodName());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, null, "AI_DIRECT_NO_MATCH"));
        }

        return selectResponseCandidates(aiLogId, candidate, matches, "AI_DIRECT");
    }

    private List<FoodAnalyzeCandidateDto> processWithLlmBranch(Long aiLogId, AiAnalysisCandidateDto candidate) {
        log.info("음식 신뢰도 60% 미만 - LLM branch 진행 aiLogId={}, foodName={}, confidenceScore={}",
                aiLogId,
                candidate.getFoodName(),
                candidate.getConfidenceScore());

        LlmFoodAnalysisResponseDto llmResponse = llmService.analyzeFromLlm(
                LlmFoodAnalysisRequestDto.builder()
                        .aiLogId(aiLogId)
                        .originalFoodName(candidate.getFoodName())
                        .confidenceScore(candidate.getConfidenceScore())
                        .build()
        );

        if (llmResponse == null || !StringUtils.hasText(llmResponse.getBaseFood())) {
            log.warn("LLM branch 실패 - 구조화 응답 없음 aiLogId={}, foodName={}", aiLogId, candidate.getFoodName());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, null, "LLM_RESPONSE_EMPTY"));
        }

        log.info("LLM branch 응답 aiLogId={}, mainCategory={}, baseFood={}, modifiers={}",
                aiLogId,
                llmResponse.getMainCategory(),
                llmResponse.getBaseFood(),
                llmResponse.getModifiers());

        List<FoodMatchService.MatchResult> matches = foodMatchService.matchFoods(
                llmResponse.getMainCategory(),
                llmResponse.getBaseFood(),
                llmResponse.getModifiers(),
                TOP_CANDIDATE_LIMIT
        );
        if (matches.isEmpty()) {
            log.warn("LLM branch food 매칭 실패 aiLogId={}, baseFood={}, modifiers={}",
                    aiLogId,
                    llmResponse.getBaseFood(),
                    llmResponse.getModifiers());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, "LLM_BRANCH_NO_MATCH"));
        }

        AiAnalysisCandidateDto llmCandidate = AiAnalysisCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(llmResponse.getBaseFood())
                .servingUnitLabel(candidate.getServingUnitLabel())
                .build();
        return selectResponseCandidates(aiLogId, llmCandidate, matches, "LLM_BRANCH");
    }

    private List<FoodAnalyzeCandidateDto> selectResponseCandidates(
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            List<FoodMatchService.MatchResult> matches,
            String branch
    ) {
        log.info("{} raw 후보 aiLogId={}, rawCount={}, preview={}",
                branch,
                aiLogId,
                matches.size(),
                summarizeMatches(matches));

        List<FoodMatchService.MatchResult> distinctMatches = deduplicateMatches(branch, aiLogId, matches);
        if (distinctMatches.isEmpty()) {
            log.warn("{} 중복 제거 후 후보가 비었습니다. aiLogId={}", branch, aiLogId);
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, null, branch + "_DEDUP_EMPTY"));
        }

        FoodMatchService.MatchResult top1 = distinctMatches.get(0);
        FoodMatchService.MatchResult top2 = distinctMatches.size() > 1 ? distinctMatches.get(1) : null;
        int gap = top2 == null ? top1.score() : top1.score() - top2.score();

        log.info("{} distinct 후보 aiLogId={}, distinctCount={}, preview={}",
                branch,
                aiLogId,
                distinctMatches.size(),
                summarizeMatches(distinctMatches));

        log.info("{} 후보 계산 aiLogId={}, top1FoodId={}, top1Score={}, top2Score={}, gap={}, distinctCandidateCount={}",
                branch,
                aiLogId,
                top1.food().getId(),
                top1.score(),
                top2 != null ? top2.score() : null,
                gap,
                distinctMatches.size());

        if (top1.score() >= AUTO_CONFIRM_SCORE_THRESHOLD && gap >= AUTO_CONFIRM_GAP_THRESHOLD) {
            log.info("{} 자동 확정 aiLogId={}, foodId={}, score={}, reason={}",
                    branch,
                    aiLogId,
                    top1.food().getId(),
                    top1.score(),
                    top1.reason());
            return List.of(buildMatchedCandidate(candidate, top1.food(), DataSource.DB_EXACT));
        }

        if (distinctMatches.size() == 1) {
            log.info("{} 단일 distinct 후보 반환 aiLogId={}, foodId={}, score={}, reason={}",
                    branch,
                    aiLogId,
                    top1.food().getId(),
                    top1.score(),
                    top1.reason());
            return List.of(buildMatchedCandidate(candidate, top1.food(), DataSource.DB_FUZZY));
        }

        List<FoodAnalyzeCandidateDto> ambiguousCandidates = distinctMatches.stream()
                .limit(AMBIGUOUS_RESPONSE_LIMIT)
                .map(match -> buildMatchedCandidate(candidate, match.food(), DataSource.DB_FUZZY))
                .toList();

        log.info("{} 다중 후보 반환 aiLogId={}, candidateCount={}", branch, aiLogId, ambiguousCandidates.size());
        return ambiguousCandidates;
    }

    private List<FoodMatchService.MatchResult> deduplicateMatches(
            String branch,
            Long aiLogId,
            List<FoodMatchService.MatchResult> matches
    ) {
        Map<String, FoodMatchService.MatchResult> bestByDisplayName = new LinkedHashMap<>();
        List<String> removed = new ArrayList<>();

        for (FoodMatchService.MatchResult match : matches) {
            String displayName = match.food().getDisplayName();
            String dedupeKey = foodMatchService.normalizeAndApplySynonyms(displayName);
            if (!StringUtils.hasText(dedupeKey)) {
                dedupeKey = "food-id-" + match.food().getId();
            }

            if (!bestByDisplayName.containsKey(dedupeKey)) {
                bestByDisplayName.put(dedupeKey, match);
                continue;
            }

            FoodMatchService.MatchResult kept = bestByDisplayName.get(dedupeKey);
            removed.add(displayName + "#" + match.food().getId() + "(" + match.score() + ")");
            log.info("{} 후보 중복 제거 aiLogId={}, dedupeKey={}, keptFoodId={}, keptScore={}, removedFoodId={}, removedScore={}",
                    branch,
                    aiLogId,
                    dedupeKey,
                    kept.food().getId(),
                    kept.score(),
                    match.food().getId(),
                    match.score());
        }

        if (!removed.isEmpty()) {
            log.info("{} 후보 중복 제거 요약 aiLogId={}, removedCount={}, removed={}",
                    branch,
                    aiLogId,
                    removed.size(),
                    removed);
        }

        return new ArrayList<>(bestByDisplayName.values());
    }

    private String summarizeMatches(List<FoodMatchService.MatchResult> matches) {
        return matches.stream()
                .limit(5)
                .map(match -> match.food().getDisplayName()
                        + "#"
                        + match.food().getId()
                        + "("
                        + match.score()
                        + ","
                        + match.reason()
                        + ")")
                .reduce((left, right) -> left + " | " + right)
                .orElse("none");
    }

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

    private FoodAnalyzeCandidateDto buildMatchedCandidate(AiAnalysisCandidateDto candidate, Food food, DataSource dataSource) {
        String responseFoodName = food != null ? food.getDisplayName() : candidate.getFoodName();
        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .foodId(food != null ? food.getId() : null)
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(responseFoodName)
                .servingKcal(food != null ? food.getServingKcal() : null)
                .carbohydrate(food != null ? food.getCarbohydrate() : null)
                .protein(food != null ? food.getProtein() : null)
                .fat(food != null ? food.getFat() : null)
                .servingUnitLabel(resolveServingUnitLabel(candidate, food))
                .dataSource(dataSource)
                .build();
    }

    private FoodAnalyzeCandidateDto buildUnmatchedCandidate(
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            LlmFoodAnalysisResponseDto llmResponse,
            String failureReason
    ) {
        log.warn("음식 후보 DB 매칭 실패 candidateFoodName={}, failureReason={}", candidate.getFoodName(), failureReason);
        saveUnmatchedLog(aiLogId, candidate, llmResponse, failureReason);
        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(UNMATCHED_FOOD_NAME)
                .servingUnitLabel(candidate.getServingUnitLabel())
                .dataSource(DataSource.UNMATCHED)
                .build();
    }

    private void saveUnmatchedLog(
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            LlmFoodAnalysisResponseDto llmResponse,
            String failureReason
    ) {
        unmatchedFoodLogRepository.save(UnmatchedFoodLog.builder()
                .aiLogId(aiLogId)
                .originalFoodName(candidate != null ? candidate.getFoodName() : null)
                .confidenceScore(candidate != null ? candidate.getConfidenceScore() : null)
                .llmMainCategory(llmResponse != null ? llmResponse.getMainCategory() : null)
                .llmBaseFood(llmResponse != null ? llmResponse.getBaseFood() : null)
                .llmModifiers(joinModifiers(llmResponse != null ? llmResponse.getModifiers() : null))
                .failureReason(failureReason)
                .build());
    }

    private String joinModifiers(List<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return null;
        }
        return modifiers.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String resolveServingUnitLabel(AiAnalysisCandidateDto candidate, Food food) {
        if (food != null && food.getServingUnit() != null) {
            return food.getServingUnit().toDisplayLabel(food.getServingWeight());
        }
        if (StringUtils.hasText(candidate.getServingUnitLabel())) {
            return candidate.getServingUnitLabel();
        }
        return null;
    }
}
