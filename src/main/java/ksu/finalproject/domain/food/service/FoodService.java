package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.analysis.cache.AnalysisImageStore;
import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeCandidateDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeResponseDto;
import ksu.finalproject.domain.analysis.entity.AiAnalysisLog;
import ksu.finalproject.domain.analysis.repository.AiAnalysisLogRepository;
import ksu.finalproject.domain.analysis.service.AiServerRequestService;
import ksu.finalproject.domain.analysis.service.AnalysisSseService;
import ksu.finalproject.domain.analysis.service.FoodAnalysisResultProcessorService;
import ksu.finalproject.domain.analysis.service.FoodImageFileService;
import ksu.finalproject.domain.food.entity.enums.AnalysisStatus;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {

    private final AiServerRequestService aiServerRequestService;
    private final FoodImageFileService foodImageFileService;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisSseService analysisSseService;
    private final FoodAnalysisResultProcessorService resultProcessorService;
    private final AnalysisImageStore analysisImageStore;

    public FoodAnalyzeResponseDto analyzeFoodImage(MultipartFile image, Long userId) throws CustomException {
        log.info("음식 이미지 분석 요청 userId={}, originalFilename={}, size={}",
                userId,
                image != null ? image.getOriginalFilename() : null,
                image != null ? image.getSize() : null);

        foodImageFileService.validate(image);

        AiAnalysisLog analysisLog = createAnalysisLog(userId);
        String imageKey = null;

        try {
            imageKey = analysisImageStore.save(analysisLog.getId(), image);
            analysisLog.updateImageKey(imageKey);
            aiAnalysisLogRepository.save(analysisLog);

            FoodAnalyzeResponseDto response = aiServerRequestService.requestAnalysis(image, analysisLog.getId());
            markSuccessed(analysisLog);

            return FoodAnalyzeResponseDto.builder()
                    .status(response.getStatus())
                    .aiLogId(response.getAiLogId())
                    .imageKey(imageKey)
                    .build();
        } catch (CustomException e) {
            analysisImageStore.delete(imageKey);
            analysisLog.clearImageKey();
            markFailed(analysisLog);
            FoodService.log.warn("음식 이미지 분석 요청 실패 userId={}, aiLogId={}, responseCode={}", userId, analysisLog.getId(), e.getStatus(), e);
            throw e;
        } catch (IOException e) {
            analysisImageStore.delete(imageKey);
            analysisLog.clearImageKey();
            markFailed(analysisLog);
            FoodService.log.error("이미지 저장 실패 userId={}, aiLogId={}", userId, analysisLog.getId(), e);
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }
    }

    public FoodAnalysisResultDto getAnalysisResult(Long aiLogId, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 결과 조회 실패 - 분석 로그 없음 aiLogId={}, userId={}", aiLogId, userId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });

        FoodAnalysisResultDto response = toResultDto(log);
        logFePayload("GET_RESULT", response);
        return response;
    }

    public void saveAnalysisResult(AiAnalysisCallbackDto result) throws CustomException {
        if (result == null || result.getAiLogId() == null) {
            log.warn("AI 콜백 수신 실패 - result 또는 aiLogId 누락");
            throw new CustomException(ResponseCode.BAD_REQUEST);
        }

        log.info(
                "AI 콜백 수신 aiLogId={}, status={}, modelVersion={}, inferenceTimeMs={}, candidateCount={}",
                result.getAiLogId(),
                result.getAnalysisStatus(),
                result.getModelVersion(),
                result.getInferenceTimeMs(),
                result.getCandidates() != null ? result.getCandidates().size() : 0
        );

        AiAnalysisLog analysisLog = aiAnalysisLogRepository.findById(result.getAiLogId())
                .orElseThrow(() -> {
                    log.warn("AI 콜백 저장 실패 - aiLogId={} 에 해당하는 분석 로그 없음", result.getAiLogId());
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });

        FoodAnalysisResultDto processedResult = resultProcessorService.process(result)
                .toBuilder()
                .imageKey(analysisLog.getImageKey())
                .build();

        analysisLog.updateAnalysisResult(
                processedResult.getModelVersion(),
                toJson(processedResult),
                processedResult.getInferenceTimeMs(),
                processedResult.getAnalysisStatus()
        );

        aiAnalysisLogRepository.save(analysisLog);

        log.info(
                "AI 콜백 결과 저장 완료 aiLogId={}, savedStatus={}, modelVersion={}, inferenceTimeMs={}",
                analysisLog.getId(),
                analysisLog.getAnalysisStatus(),
                analysisLog.getModelVersion(),
                analysisLog.getInferenceTimeMs()
        );

        logFePayload("SSE_PUSH", processedResult);
        log.info("AI 콜백 SSE 전송 시도 aiLogId={}", analysisLog.getId());
        analysisSseService.emit(analysisLog.getId(), processedResult);
    }

    public SseEmitter subscribeToResult(Long aiLogId, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 결과 SSE 구독 실패 - 분석 로그 없음 aiLogId={}, userId={}", aiLogId, userId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });

        if (log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            FoodService.log.info("분석 결과 SSE 즉시 응답 aiLogId={}, userId={}, status={}", aiLogId, userId, log.getAnalysisStatus());
            SseEmitter emitter = new SseEmitter();
            try {
                FoodAnalysisResultDto response = toResultDto(log);
                logFePayload("SSE_IMMEDIATE", response);
                emitter.send(
                        SseEmitter.event()
                                .name("analysis-complete")
                                .data(response, MediaType.APPLICATION_JSON)
                );
                emitter.complete();
            } catch (IOException e) {
                FoodService.log.warn("분석 결과 SSE 즉시 응답 실패 aiLogId={}, userId={}: {}", aiLogId, userId, e.getMessage());
                emitter.completeWithError(e);
            }
            return emitter;
        }

        FoodService.log.info("분석 결과 SSE 구독 등록 aiLogId={}, userId={}", aiLogId, userId);
        return analysisSseService.register(aiLogId);
    }

    public StoredFoodImage getFoodImage(String imageKey, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByImageKeyAndUserId(imageKey, userId)
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS));

        try {
            FoodImageFileService.LoadedFoodImage image = foodImageFileService.load(log.getImageKey());
            return new StoredFoodImage(image.data(), image.contentType());
        } catch (IOException e) {
            FoodService.log.warn("음식 이미지 조회 실패 imageKey={}, userId={}", imageKey, userId, e);
            throw new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
        }
    }

    private AiAnalysisLog createAnalysisLog(Long userId) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 로그 생성 실패 - 사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });

        AiAnalysisLog savedLog = aiAnalysisLogRepository.save(
                AiAnalysisLog.builder()
                        .user(user)
                        .analysisStatus(AnalysisStatus.PROCESSING)
                        .build()
        );

        FoodService.log.info("분석 로그 생성 완료 userId={}, aiLogId={}", userId, savedLog.getId());
        return savedLog;
    }

    private void markSuccessed(AiAnalysisLog log) {
        log.success();
        aiAnalysisLogRepository.save(log);
        FoodService.log.info("음식 이미지 분석 요청 접수 완료 aiLogId={}", log.getId());
    }

    private void markFailed(AiAnalysisLog log) {
        log.fail();
        aiAnalysisLogRepository.save(log);
        FoodService.log.warn("분석 로그 상태 실패 처리 aiLogId={}", log.getId());
    }

    private FoodAnalysisResultDto toResultDto(AiAnalysisLog log) throws CustomException {
        String raw = log.getRawOutput();

        if (StringUtils.hasText(raw) && log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            try {
                FoodAnalysisResultDto response = objectMapper.readValue(raw, FoodAnalysisResultDto.class);
                if (!StringUtils.hasText(response.getImageKey()) && StringUtils.hasText(log.getImageKey())) {
                    return response.toBuilder()
                            .imageKey(log.getImageKey())
                            .build();
                }
                return response;
            } catch (Exception e) {
                FoodService.log.error("분석 결과 역직렬화 실패 aiLogId={}, status={}", log.getId(), log.getAnalysisStatus(), e);
                throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
            }
        }

        return FoodAnalysisResultDto.builder()
                .analysisStatus(log.getAnalysisStatus())
                .modelVersion(log.getModelVersion())
                .inferenceTimeMs(log.getInferenceTimeMs())
                .aiLogId(log.getId())
                .imageKey(log.getImageKey())
                .build();
    }

    private void logFePayload(String route, FoodAnalysisResultDto response) {
        if (response == null) {
            log.info("FE 응답 payload route={}, aiLogId=null, analysisStatus=null, candidateCount=0, candidates=none", route);
            return;
        }

        int candidateCount = response.getCandidates() != null ? response.getCandidates().size() : 0;
        log.info("FE 응답 payload route={}, aiLogId={}, analysisStatus={}, candidateCount={}, candidates={}",
                route,
                response.getAiLogId(),
                response.getAnalysisStatus(),
                candidateCount,
                summarizeResponseCandidates(response));
    }

    private String summarizeResponseCandidates(FoodAnalysisResultDto response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(response.getCandidates().size(), 5);
        for (int i = 0; i < limit; i++) {
            FoodAnalyzeCandidateDto candidate = response.getCandidates().get(i);
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append("{foodId=")
                    .append(candidate.getFoodId())
                    .append(",recognizedName=")
                    .append(candidate.getRecognizedName())
                    .append(",matchedFoodName=")
                    .append(candidate.getMatchedFoodName())
                    .append(",servingKcal=")
                    .append(candidate.getServingKcal())
                    .append(",servingUnitLabel=")
                    .append(candidate.getServingUnitLabel())
                    .append(",dataSource=")
                    .append(candidate.getDataSource())
                    .append("}");
        }
        return builder.toString();
    }

    private String toJson(Object value) throws CustomException {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            FoodService.log.error("JSON 직렬화 실패 valueType={}", value != null ? value.getClass().getSimpleName() : null, e);
            throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
        }
    }

    public record StoredFoodImage(byte[] data, String contentType) {}
}
