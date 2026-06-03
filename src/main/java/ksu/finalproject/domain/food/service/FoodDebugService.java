package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.analysis.cache.AnalysisImageStore;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisRequestDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
import ksu.finalproject.domain.analysis.service.FoodAnalysisResultProcessorService;
import ksu.finalproject.domain.analysis.service.LlmService;
import ksu.finalproject.domain.food.dto.debug.FoodDebugE2EResponseDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugLlmResponseDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugMatchCandidateDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugMatchResponseDto;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.global.common.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodDebugService {

    private static final AtomicLong DEBUG_AI_LOG_SEQUENCE = new AtomicLong(System.currentTimeMillis());
    private static final int DEFAULT_MATCH_LIMIT = 15;

    private final AnalysisImageStore analysisImageStore;
    private final LlmService llmService;
    private final FoodMatchService foodMatchService;
    private final FoodAnalysisResultProcessorService resultProcessorService;

    public FoodDebugLlmResponseDto debugLlm(MultipartFile image) throws CustomException, IOException {
        long aiLogId = nextDebugAiLogId();
        try {
            analysisImageStore.save(aiLogId, image);
            LlmFoodAnalysisResponseDto llmResponse = llmService.analyzeFromLlm(
                    LlmFoodAnalysisRequestDto.builder()
                            .aiLogId(aiLogId)
                            .originalFoodName(image != null ? image.getOriginalFilename() : null)
                            .confidenceScore(0.0)
                            .build()
            );
            return toLlmDebugResponse(aiLogId, llmResponse);
        } finally {
            analysisImageStore.delete(aiLogId);
        }
    }

    public FoodDebugMatchResponseDto debugMatch(LlmFoodAnalysisResponseDto request, Integer limit) {
        int resolvedLimit = (limit != null && limit > 0) ? limit : DEFAULT_MATCH_LIMIT;
        FoodDebugLlmResponseDto debugRequest = toLlmDebugResponse(null, request);
        List<FoodMatchService.MatchResult> matches = foodMatchService.matchFoods(
                request != null ? request.getMainCategory() : null,
                request != null ? request.getBaseFood() : null,
                request != null ? request.getRecognizedName() : null,
                request != null ? request.getModifiers() : List.of(),
                request != null ? request.getSearchTerms() : List.of(),
                resolvedLimit
        );
        return FoodDebugMatchResponseDto.builder()
                .request(debugRequest)
                .candidateCount(matches.size())
                .candidates(matches.stream().map(this::toMatchCandidate).toList())
                .build();
    }

    public FoodDebugE2EResponseDto debugE2e(MultipartFile image, Double confidenceScore) throws CustomException, IOException {
        long aiLogId = nextDebugAiLogId();
        try {
            analysisImageStore.save(aiLogId, image);
            LlmFoodAnalysisResponseDto llmResponse = llmService.analyzeFromLlm(
                    LlmFoodAnalysisRequestDto.builder()
                            .aiLogId(aiLogId)
                            .originalFoodName(image != null ? image.getOriginalFilename() : null)
                            .confidenceScore(confidenceScore)
                            .build()
            );

            FoodDebugLlmResponseDto llmDebug = toLlmDebugResponse(aiLogId, llmResponse);
            FoodDebugMatchResponseDto matchPreview = llmResponse != null
                    ? debugMatch(llmResponse, DEFAULT_MATCH_LIMIT)
                    : FoodDebugMatchResponseDto.builder()
                    .request(llmDebug)
                    .candidateCount(0)
                    .candidates(List.of())
                    .build();

            List<FoodAnalyzeCandidateDto> finalCandidates = llmResponse != null
                    ? resultProcessorService.processDebugLlmBranch(aiLogId, confidenceScore, llmResponse)
                    : List.of();

            return FoodDebugE2EResponseDto.builder()
                    .aiLogId(aiLogId)
                    .llm(llmDebug)
                    .matchPreview(matchPreview)
                    .finalCandidates(finalCandidates)
                    .build();
        } finally {
            analysisImageStore.delete(aiLogId);
        }
    }

    private long nextDebugAiLogId() {
        return DEBUG_AI_LOG_SEQUENCE.incrementAndGet();
    }

    private FoodDebugLlmResponseDto toLlmDebugResponse(Long aiLogId, LlmFoodAnalysisResponseDto llmResponse) {
        if (llmResponse == null) {
            return FoodDebugLlmResponseDto.builder()
                    .aiLogId(aiLogId)
                    .modifiers(List.of())
                    .searchTerms(List.of())
                    .normalizedModifiers(List.of())
                    .normalizedSearchTerms(List.of())
                    .build();
        }

        List<String> modifiers = llmResponse.getModifiers() != null ? llmResponse.getModifiers() : List.of();
        List<String> searchTerms = llmResponse.getSearchTerms() != null ? llmResponse.getSearchTerms() : List.of();

        return FoodDebugLlmResponseDto.builder()
                .aiLogId(aiLogId)
                .recognizedName(llmResponse.getRecognizedName())
                .mainCategory(llmResponse.getMainCategory())
                .baseFood(llmResponse.getBaseFood())
                .modifiers(modifiers)
                .searchTerms(searchTerms)
                .normalizedRecognizedName(foodMatchService.normalizeAndApplySynonyms(llmResponse.getRecognizedName()))
                .normalizedMainCategory(foodMatchService.normalizeAndApplySynonyms(llmResponse.getMainCategory()))
                .normalizedBaseFood(foodMatchService.normalizeAndApplySynonyms(llmResponse.getBaseFood()))
                .normalizedModifiers(modifiers.stream()
                        .map(foodMatchService::normalizeAndApplySynonyms)
                        .filter(StringUtils::hasText)
                        .toList())
                .normalizedSearchTerms(searchTerms.stream()
                        .map(foodMatchService::normalizeAndApplySynonyms)
                        .filter(StringUtils::hasText)
                        .toList())
                .build();
    }

    private FoodDebugMatchCandidateDto toMatchCandidate(FoodMatchService.MatchResult match) {
        Food food = match.food();
        return FoodDebugMatchCandidateDto.builder()
                .foodId(food.getId())
                .displayName(food.getDisplayName())
                .foodName(food.getFoodName())
                .mainCategory(food.getMainCategory())
                .subCategory(food.getSubCategory())
                .detailCategory(food.getDetailCategory())
                .score(match.score())
                .reason(match.reason())
                .servingKcal(food.getServingKcal())
                .servingUnitLabel(food.getServingUnit() != null ? food.getServingUnit().toDisplayLabel(food.getServingWeight()) : null)
                .build();
    }
}
