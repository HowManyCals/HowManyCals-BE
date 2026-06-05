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
    private static final int TOP_CANDIDATE_LIMIT = 15;
    private static final int AMBIGUOUS_RESPONSE_LIMIT = 3;
    private static final int AUTO_CONFIRM_SCORE_THRESHOLD = 85;
    private static final int AUTO_CONFIRM_GAP_THRESHOLD = 15;
    private static final int WEAK_MATCH_UNMATCHED_SCORE_THRESHOLD = 25;

    private final FoodMatchService foodMatchService;
    private final LlmService llmService;
    private final UnmatchedFoodLogRepository unmatchedFoodLogRepository;

    // AI 서버에서 콜백으로 넘어온 분석 결과를 받아서 후처리 파이프라인의 진입점 역할을 합니다.
    // 후보가 없으면 빈 리스트로 바로 반환하고, 있으면 첫 번째 후보만 처리합니다. (실질적으로 AI 모델이 1개만 넘깁니다)
    public FoodAnalysisResultDto process(AiAnalysisCallbackDto result) {
        List<AiAnalysisCandidateDto> candidates = result.getCandidates();
        log.info("음식 후보 후처리 시작 aiLogId={}, candidateCount={}",
                result.getAiLogId(),
                candidates != null ? candidates.size() : 0);

        if (candidates == null || candidates.isEmpty()) {
            log.info("음식 후보 후처리 종료 - 후보 없음 aiLogId={}", result.getAiLogId());
            return toResponseDto(result, Collections.emptyList());
        }

        AiAnalysisCandidateDto primaryCandidate = candidates.get(0); // 가장 처음의 후보만 받음. 실질적으로는 1개임
        List<FoodAnalyzeCandidateDto> processedCandidates = processPrimaryCandidate(result.getAiLogId(), primaryCandidate);

        log.info("음식 후보 후처리 완료 aiLogId={}, processedCandidateCount={}",
                result.getAiLogId(),
                processedCandidates.size());

        return toResponseDto(result, processedCandidates);
    }

    // 전달받은 음식 후보(1개)에 대하여 신뢰도 60% 기준으로 LLM 분기를 할지말지 결정합니다.
    // 신뢰도 >= 0.6이면 foodName을 그대로 matchFoods에 넘겨 정규화 -> Levenshtein 매핑을 시도합니다.
    // 신뢰도 < 0.6이면 AI가 뭘 봤는지 불확실한 거니까 LLM한테 한 번 더 물어보는 방향으로 분기합니다.
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
                candidate.getFoodName(),
                List.of(),
                List.of(candidate.getFoodName()),
                TOP_CANDIDATE_LIMIT
        );
        if (matches.isEmpty()) {
            log.warn("AI 후보 food 직접 매칭 실패 aiLogId={}, foodName={}", aiLogId, candidate.getFoodName());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, null, "AI_DIRECT_NO_MATCH"));
        }

        return selectResponseCandidates(aiLogId, candidate, null, matches, "AI_DIRECT");
    }

    // 신뢰도가 낮아서 AI가 뭘 인식했는지 불확실한 경우 LLM에게 음식명을 재추론 요청합니다.
    // LLM이 반환한 recognizedName, baseFood, modifiers, searchTerms를 분해해서 matchFoods에 넘깁니다.
    // LLM 응답 자체가 비었거나 구조가 이상하면 그냥 UNMATCHED로 처리합니다.
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

        if (llmResponse == null || (!StringUtils.hasText(llmResponse.getRecognizedName()) && !StringUtils.hasText(llmResponse.getBaseFood()))) {
            log.warn("LLM branch 실패 - 구조화 응답 없음 aiLogId={}, foodName={}", aiLogId, candidate.getFoodName());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, null, "LLM_RESPONSE_EMPTY"));
        }

        log.info("LLM branch 응답 aiLogId={}, recognizedName={}, mainCategory={}, baseFood={}, modifiers={}, searchTerms={}",
                aiLogId,
                llmResponse.getRecognizedName(),
                llmResponse.getMainCategory(),
                llmResponse.getBaseFood(),
                llmResponse.getModifiers(),
                llmResponse.getSearchTerms());

        List<FoodMatchService.MatchResult> matches = foodMatchService.matchFoods(
                llmResponse.getMainCategory(),
                llmResponse.getBaseFood(),
                llmResponse.getRecognizedName(),
                llmResponse.getModifiers(),
                llmResponse.getSearchTerms(),
                TOP_CANDIDATE_LIMIT
        );
        if (matches.isEmpty()) {
            log.warn("LLM branch food 매칭 실패 aiLogId={}, recognizedName={}, baseFood={}, modifiers={}, searchTerms={}",
                    aiLogId,
                    llmResponse.getRecognizedName(),
                    llmResponse.getBaseFood(),
                    llmResponse.getModifiers(),
                    llmResponse.getSearchTerms());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, "LLM_BRANCH_NO_MATCH"));
        }

        AiAnalysisCandidateDto llmCandidate = AiAnalysisCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .foodName(StringUtils.hasText(llmResponse.getRecognizedName()) ? llmResponse.getRecognizedName() : llmResponse.getBaseFood())
                .servingUnitLabel(candidate.getServingUnitLabel())
                .build();
        return selectResponseCandidates(aiLogId, llmCandidate, llmResponse, matches, "LLM_BRANCH");
    }

    // matchFoods로 받은 후보 리스트를 정제해서 최종적으로 FE에 내려줄 후보 목록을 결정합니다.
    // 약한 후보 필터링 -> 중복 제거 -> 자동확정 / 단일 distinct / 다중 후보 순으로 판단합니다.
    // 어떤 경로로 왔든(AI_DIRECT / LLM_BRANCH) 매칭 후 공통으로 이 메서드를 거칩니다.
    private List<FoodAnalyzeCandidateDto> selectResponseCandidates(
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            LlmFoodAnalysisResponseDto llmResponse,
            List<FoodMatchService.MatchResult> matches,
            String branch
    ) {
        List<FoodMatchService.MatchResult> filteredMatches = filterWeakProcessedFoodCandidates(branch, aiLogId, candidate, llmResponse, matches);

        log.info("{} raw 후보 aiLogId={}, rawCount={}, preview={}",
                branch,
                aiLogId,
                filteredMatches.size(),
                summarizeMatches(filteredMatches));

        List<FoodMatchService.MatchResult> distinctMatches = deduplicateMatches(branch, aiLogId, filteredMatches);
        if (distinctMatches.isEmpty()) {
            log.warn("{} 중복 제거 후 후보가 비었습니다. aiLogId={}", branch, aiLogId);
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, branch + "_DEDUP_EMPTY"));
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

        if (shouldTreatAsUnmatched(top1)) {
            log.warn("{} 약한 후보만 존재 - UNMATCHED aiLogId={}, top1FoodId={}, top1Score={}, top1Reason={}, distinctCandidateCount={}",
                    branch,
                    aiLogId,
                    top1.food().getId(),
                    top1.score(),
                    top1.reason(),
                    distinctMatches.size());
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, branch + "_WEAK_MATCH"));
        }

        if (shouldTreatProcessedFoodFuzzyAsUnmatched(llmResponse, distinctMatches)) {
            log.warn("{} 브랜드형 가공식품 weak fuzzy - UNMATCHED aiLogId={}, recognizedName={}, distinctCount={}, preview={}",
                    branch,
                    aiLogId,
                    candidate != null ? candidate.getFoodName() : null,
                    distinctMatches.size(),
                    summarizeMatches(distinctMatches));
            return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, branch + "_PROCESSED_WEAK_FUZZY"));
        }

        if (canAutoConfirm(top1, gap)) {
            DataSource source = resolveDataSource(top1);
            log.info("{} 자동 확정 aiLogId={}, foodId={}, score={}, reason={}, dataSource={}",
                    branch,
                    aiLogId,
                    top1.food().getId(),
                    top1.score(),
                    top1.reason(),
                    source);
            return buildMatchedResponse(branch, aiLogId, "AUTO_CONFIRM", candidate, List.of(top1));
        }

        if (distinctMatches.size() == 1) {
            if (!hasStrongMatchReason(top1.reason())) {
                log.warn("{} 단일 distinct지만 strong match 없음 - UNMATCHED aiLogId={}, top1FoodId={}, top1Score={}, top1Reason={}",
                        branch,
                        aiLogId,
                        top1.food().getId(),
                        top1.score(),
                        top1.reason());
                return List.of(buildUnmatchedCandidate(aiLogId, candidate, llmResponse, branch + "_SINGLE_DISTINCT_WEAK"));
            }

            DataSource source = resolveDataSource(top1);
            log.info("{} 단일 distinct 후보 반환 aiLogId={}, foodId={}, score={}, reason={}, dataSource={}",
                    branch,
                    aiLogId,
                    top1.food().getId(),
                    top1.score(),
                    top1.reason(),
                    source);
            return buildMatchedResponse(branch, aiLogId, "SINGLE_DISTINCT", candidate, List.of(top1));
        }

        List<FoodMatchService.MatchResult> selectedMatches = distinctMatches.stream()
                .limit(AMBIGUOUS_RESPONSE_LIMIT)
                .toList();

        log.info("{} 다중 후보 반환 aiLogId={}, candidateCount={}", branch, aiLogId, selectedMatches.size());
        return buildMatchedResponse(branch, aiLogId, "AMBIGUOUS_TOP" + selectedMatches.size(), candidate, selectedMatches);
    }

    // 매칭된 Food 엔티티의 reason을 보고 exact 매칭인지 fuzzy 매칭인지 구분해서 DataSource를 결정합니다.
    private DataSource resolveDataSource(FoodMatchService.MatchResult match) {
        String reason = match.reason();
        if (reason.contains("recognized_name_food_exact")
                || reason.contains("recognized_name_canonical_exact")
                || reason.contains("recognized_name_suffix_exact")) {
            return DataSource.DB_EXACT;
        }
        return DataSource.DB_FUZZY;
    }

    // 1등 후보가 너무 약해서 아예 반환할 가치가 없는지 판단합니다.
    // 점수가 25 미만이고 strong match도 없으면 UNMATCHED 처리, main_category_mismatch가 붙은 경우도 마찬가지입니다.
    private boolean shouldTreatAsUnmatched(FoodMatchService.MatchResult topMatch) {
        if (topMatch == null) {
            return true;
        }

        boolean strongMatch = hasStrongMatchReason(topMatch.reason());
        if (topMatch.score() < WEAK_MATCH_UNMATCHED_SCORE_THRESHOLD && !strongMatch) {
            return true;
        }

        return !strongMatch && topMatch.reason() != null && topMatch.reason().contains("main_category_mismatch");
    }

    // 브랜드형 가공식품(빵/과자류) 케이스에서 weak fuzzy 후보들을 미리 걸러냅니다.
    // subCategory가 baseFood나 recognizedName과 연관이 없으면 그냥 제거합니다.
    // strong match 이유가 있는 후보는 절대 제거하지 않습니다.
    private List<FoodMatchService.MatchResult> filterWeakProcessedFoodCandidates(
            String branch,
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            LlmFoodAnalysisResponseDto llmResponse,
            List<FoodMatchService.MatchResult> matches
    ) {
        if (matches == null || matches.isEmpty() || !isProcessedBakeryCase(llmResponse)) {
            return matches;
        }

        String normalizedRecognizedName = foodMatchService.normalizeAndApplySynonyms(candidate != null ? candidate.getFoodName() : null);
        String normalizedBaseFood = foodMatchService.normalizeAndApplySynonyms(llmResponse != null ? llmResponse.getBaseFood() : null);

        List<FoodMatchService.MatchResult> filtered = new ArrayList<>();
        for (FoodMatchService.MatchResult match : matches) {
            if (shouldDropWeakProcessedFoodCandidate(normalizedRecognizedName, normalizedBaseFood, match)) {
                log.info("{} 브랜드형 가공식품 후보 제거 aiLogId={}, foodId={}, dbFoodName={}, subCategory={}, score={}, reason={}",
                        branch,
                        aiLogId,
                        match.food().getId(),
                        match.food().getFoodName(),
                        match.food().getSubCategory(),
                        match.score(),
                        match.reason());
                continue;
            }
            filtered.add(match);
        }

        return filtered;
    }

    // 후보 하나가 제거 대상인지 판단하는 세부 로직입니다. filterWeakProcessedFoodCandidates에서 루프 돌면서 호출합니다.
    private boolean shouldDropWeakProcessedFoodCandidate(
            String normalizedRecognizedName,
            String normalizedBaseFood,
            FoodMatchService.MatchResult match
    ) {
        if (match == null || match.food() == null) {
            return false;
        }

        if (hasStrongMatchReason(match.reason())) {
            return false;
        }

        String normalizedSubCategory = foodMatchService.normalizeAndApplySynonyms(match.food().getSubCategory());
        if (!StringUtils.hasText(normalizedSubCategory)) {
            return false;
        }

        if (StringUtils.hasText(normalizedBaseFood) && normalizedSubCategory.contains(normalizedBaseFood)) {
            return false;
        }

        if (StringUtils.hasText(normalizedRecognizedName)
                && (normalizedRecognizedName.contains(normalizedSubCategory) || normalizedSubCategory.contains(normalizedRecognizedName))) {
            return false;
        }

        return true;
    }

    // 브랜드형 가공식품인데 필터링 후에도 남은 후보가 전부 weak fuzzy뿐이면 UNMATCHED로 떨굽니다.
    // strong match 하나라도 있으면 통과시킵니다.
    private boolean shouldTreatProcessedFoodFuzzyAsUnmatched(
            LlmFoodAnalysisResponseDto llmResponse,
            List<FoodMatchService.MatchResult> distinctMatches
    ) {
        if (!isProcessedBakeryCase(llmResponse) || distinctMatches == null || distinctMatches.isEmpty()) {
            return false;
        }

        return distinctMatches.stream().noneMatch(match -> hasStrongMatchReason(match.reason()));
    }

    // LLM 응답의 mainCategory가 "빵및과자류"인지 확인합니다.
    // 이 케이스는 브랜드명 기반 가공식품이 많아서 weak fuzzy 매칭을 더 엄격하게 처리해야 합니다.
    private boolean isProcessedBakeryCase(LlmFoodAnalysisResponseDto llmResponse) {
        if (llmResponse == null) {
            return false;
        }

        String normalizedMainCategory = foodMatchService.normalizeAndApplySynonyms(llmResponse.getMainCategory());
        return "빵및과자류".equals(normalizedMainCategory);
    }

    // reason에 exact 계열 키워드가 하나라도 있으면 strong match로 봅니다.
    // 이 기준을 통과해야 단일 distinct 반환이나 자동 확정이 가능합니다.
    private boolean hasStrongMatchReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }

        return reason.contains("recognized_name_food_exact")
                || reason.contains("recognized_name_canonical_exact")
                || reason.contains("recognized_name_suffix_exact")
                || reason.contains("search_term_exact")
                || reason.contains("base_food_sub_exact")
                || reason.contains("modifier_detail_exact");
    }

    // 1등 후보를 FE에 단독으로 자동 확정해도 되는지 판단합니다.
    // 점수 85 이상 + 2등과 점수 차 15 이상 + strong match + main_category_mismatch 없어야 통과합니다.
    private boolean canAutoConfirm(FoodMatchService.MatchResult topMatch, int gap) {
        if (topMatch == null) {
            return false;
        }

        if (topMatch.score() < AUTO_CONFIRM_SCORE_THRESHOLD || gap < AUTO_CONFIRM_GAP_THRESHOLD) {
            return false;
        }

        if (!hasStrongMatchReason(topMatch.reason())) {
            return false;
        }

        return topMatch.reason() == null || !topMatch.reason().contains("main_category_mismatch");
    }

    // 매칭 결과에서 표시명 기준으로 중복된 후보를 제거합니다.
    // strong match 후보는 displayName 기준, 일반 후보는 foodName 기준으로 dedupeKey를 잡습니다.
    // 같은 key면 먼저 들어온 것(점수 높은 것)을 유지합니다.
    private List<FoodMatchService.MatchResult> deduplicateMatches(
            String branch,
            Long aiLogId,
            List<FoodMatchService.MatchResult> matches
    ) {
        Map<String, FoodMatchService.MatchResult> bestByDisplayName = new LinkedHashMap<>();
        List<String> removed = new ArrayList<>();

        for (FoodMatchService.MatchResult match : matches) {
            String displayName = match.food().getDisplayName();
            String dedupeKey = resolveDedupeKey(match);
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

    // 중복 제거 키를 결정합니다. strong match면 displayName 기준, 아니면 foodName 기준입니다.
    private String resolveDedupeKey(FoodMatchService.MatchResult match) {
        if (match == null || match.food() == null) {
            return null;
        }

        if (hasStrongMatchReason(match.reason())) {
            return foodMatchService.normalizeAndApplySynonyms(match.food().getDisplayName());
        }

        String foodNameKey = foodMatchService.normalizeAndApplySynonyms(match.food().getFoodName());
        if (StringUtils.hasText(foodNameKey)) {
            return foodNameKey;
        }
        return foodMatchService.normalizeAndApplySynonyms(match.food().getDisplayName());
    }

    // 로그에 찍을 요약 문자열을 만드는 헬퍼입니다. 최대 5개까지만 찍습니다.
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

    // 선택된 매칭 결과를 FoodAnalyzeCandidateDto 리스트로 변환하고 로그를 남깁니다.
    private List<FoodAnalyzeCandidateDto> buildMatchedResponse(
            String branch,
            Long aiLogId,
            String selectionType,
            AiAnalysisCandidateDto candidate,
            List<FoodMatchService.MatchResult> selectedMatches
    ) {
        List<FoodAnalyzeCandidateDto> responseCandidates = selectedMatches.stream()
                .map(match -> buildMatchedCandidate(candidate, match.food(), resolveDataSource(match)))
                .toList();
        logDbMappingResult(branch, aiLogId, selectionType, candidate, selectedMatches, responseCandidates);
        return responseCandidates;
    }

    // DB 매핑 결과를 로그로 남기는 헬퍼입니다.
    private void logDbMappingResult(
            String branch,
            Long aiLogId,
            String selectionType,
            AiAnalysisCandidateDto candidate,
            List<FoodMatchService.MatchResult> selectedMatches,
            List<FoodAnalyzeCandidateDto> responseCandidates
    ) {
        log.info("{} DB 매핑 결과 aiLogId={}, selectionType={}, recognizedName={}, mappedCount={}, mapped={}",
                branch,
                aiLogId,
                selectionType,
                candidate != null ? candidate.getFoodName() : null,
                selectedMatches != null ? selectedMatches.size() : 0,
                summarizeMappedResults(selectedMatches, responseCandidates));
    }

    // 로그에 찍을 매핑 결과 요약 문자열을 만드는 헬퍼입니다.
    private String summarizeMappedResults(
            List<FoodMatchService.MatchResult> matches,
            List<FoodAnalyzeCandidateDto> responseCandidates
    ) {
        if (matches == null || matches.isEmpty()) {
            return "none";
        }

        List<String> summaries = new ArrayList<>();
        int size = Math.min(matches.size(), responseCandidates != null ? responseCandidates.size() : 0);
        for (int i = 0; i < size; i++) {
            FoodMatchService.MatchResult match = matches.get(i);
            FoodAnalyzeCandidateDto responseCandidate = responseCandidates.get(i);
            Food food = match.food();
            summaries.add("{foodId="
                    + food.getId()
                    + ",recognizedName="
                    + responseCandidate.getRecognizedName()
                    + ",matchedFoodName="
                    + responseCandidate.getMatchedFoodName()
                    + ",dbFoodName="
                    + food.getFoodName()
                    + ",mainCategory="
                    + food.getMainCategory()
                    + ",subCategory="
                    + food.getSubCategory()
                    + ",detailCategory="
                    + food.getDetailCategory()
                    + ",servingKcal="
                    + food.getServingKcal()
                    + ",dataSource="
                    + responseCandidate.getDataSource()
                    + ",score="
                    + match.score()
                    + ",reason="
                    + match.reason()
                    + "}");
        }
        return String.join(" | ", summaries);
    }

    // AiAnalysisCallbackDto를 FE에 내려줄 FoodAnalysisResultDto로 변환합니다.
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

    // 매칭 성공 시 Food 엔티티의 영양정보, sourceType, dataSource를 DTO에 담아 반환합니다.
    private FoodAnalyzeCandidateDto buildMatchedCandidate(AiAnalysisCandidateDto candidate, Food food, DataSource dataSource) {
        String recognizedName = candidate != null ? candidate.getFoodName() : null;
        String matchedFoodName = food != null ? food.getDisplayName() : recognizedName;

        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .foodId(food != null ? food.getId() : null)
                .confidenceScore(candidate.getConfidenceScore())
                .recognizedName(recognizedName)
                .matchedFoodName(matchedFoodName)
                .servingKcal(food != null ? food.getServingKcal() : null)
                .carbohydrate(food != null ? food.getCarbohydrate() : null)
                .protein(food != null ? food.getProtein() : null)
                .fat(food != null ? food.getFat() : null)
                .servingUnitLabel(resolveServingUnitLabel(candidate, food))
                .sourceType(food != null ? food.getSourceType() : null)
                .dataSource(dataSource)
                .build();
    }

    // 매칭 실패 시 영양정보 없이 recognizedName만 담은 UNMATCHED 후보를 반환합니다.
    // 동시에 UnmatchedFoodLog에 기록을 남겨 나중에 DB 확장에 활용할 수 있도록 합니다.
    private FoodAnalyzeCandidateDto buildUnmatchedCandidate(
            Long aiLogId,
            AiAnalysisCandidateDto candidate,
            LlmFoodAnalysisResponseDto llmResponse,
            String failureReason
    ) {
        log.warn("음식 후보 DB 매칭 실패 aiLogId={}, originalFoodName={}, recognizedName={}, llmMainCategory={}, llmBaseFood={}, llmModifiers={}, llmSearchTerms={}, failureReason={}",
                aiLogId,
                candidate != null ? candidate.getFoodName() : null,
                llmResponse != null && StringUtils.hasText(llmResponse.getRecognizedName()) ? llmResponse.getRecognizedName() : (candidate != null ? candidate.getFoodName() : null),
                llmResponse != null ? llmResponse.getMainCategory() : null,
                llmResponse != null ? llmResponse.getBaseFood() : null,
                llmResponse != null ? llmResponse.getModifiers() : null,
                llmResponse != null ? llmResponse.getSearchTerms() : null,
                failureReason);
        saveUnmatchedLog(aiLogId, candidate, llmResponse, failureReason);
        String responseRecognizedName = llmResponse != null && StringUtils.hasText(llmResponse.getRecognizedName())
                ? llmResponse.getRecognizedName()
                : (candidate != null ? candidate.getFoodName() : null);
        return FoodAnalyzeCandidateDto.builder()
                .aiModelIndex(candidate.getAiModelIndex())
                .confidenceScore(candidate.getConfidenceScore())
                .recognizedName(responseRecognizedName)
                .matchedFoodName(null)
                .servingUnitLabel(candidate.getServingUnitLabel())
                .sourceType(null)
                .dataSource(DataSource.UNMATCHED)
                .build();
    }

    // 매칭 실패 정보를 UnmatchedFoodLog 엔티티로 저장합니다. 나중에 DB 품질 개선에 활용합니다.
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

    // modifier 리스트를 콤마로 이어붙여 단일 문자열로 만듭니다. DB 저장용입니다.
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

    // FE에 내려줄 serving_unit_label을 결정합니다.
    // Food 엔티티에 servingUnit이 있으면 그걸 우선 쓰고, 없으면 AI가 넘겨준 라벨을 그대로 씁니다.
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
