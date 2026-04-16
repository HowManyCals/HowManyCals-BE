package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodAnalyzeResponseDto;
import ksu.finalproject.domain.food.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.food.entity.AiAnalysisLog;
import ksu.finalproject.domain.food.entity.enums.AnalysisStatus;
import ksu.finalproject.domain.food.repository.AiAnalysisLogRepository;
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

    /**
     * 음식 이미지를 AI 서버에 비동기 분석 요청합니다.
     * 분석 요청이 정상 접수되면 202와 함께 ai_log_id를 반환합니다.
     */
    public FoodAnalyzeResponseDto analyzeFoodImage(MultipartFile image, Long userId) throws CustomException {
        log.info("음식 이미지 분석 요청 userId={}, originalFilename={}, size={}",
                userId,
                image != null ? image.getOriginalFilename() : null,
                image != null ? image.getSize() : null);

        foodImageFileService.validate(image);

        AiAnalysisLog log = createAnalysisLog(userId);
        FoodImageFileService.SavedFoodImage savedImage = null;

        try {
            savedImage = foodImageFileService.save(image);
            FoodAnalyzeResponseDto response = aiServerRequestService.requestAnalysis(
                    savedImage.path(),
                    savedImage.contentType(),
                    log.getId()
            );
            log.success(toJson(response));
            aiAnalysisLogRepository.save(log);
            FoodService.log.info("음식 이미지 분석 요청 접수 완료 userId={}, aiLogId={}, status={}", userId, log.getId(), response.getStatus());
            return response;
        } catch (CustomException e) {
            markFailed(log);
            FoodService.log.warn("음식 이미지 분석 요청 실패 userId={}, aiLogId={}, responseCode={}", userId, log.getId(), e.getStatus(), e);
            throw e;
        } catch (IOException e) {
            markFailed(log);
            FoodService.log.error("음식 이미지 임시 파일 처리 실패 userId={}, aiLogId={}", userId, log.getId(), e);
            throw new CustomException(ResponseCode.FOOD_IMAGE_UPLOAD_FAILED);
        } finally {
            foodImageFileService.deleteFile(savedImage != null ? savedImage.path() : null);
        }
    }

    /**
     * AI 분석 결과(또는 진행 상태)를 조회합니다.
     * 본인 요청 건만 조회 가능합니다.
     */
    public FoodAnalysisResultDto getAnalysisResult(Long aiLogId, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 결과 조회 실패 - 분석 로그 없음 aiLogId={}, userId={}", aiLogId, userId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });

        return toResultDto(log);
    }

    /**
     * AI 서버 콜백으로 수신된 분석 결과를 로그에 저장하고, 구독 중인 FE에 즉시 푸시합니다.
     */
    public void saveAnalysisResult(FoodAnalysisResultDto result) throws CustomException {
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

        analysisLog.updateAnalysisResult(
                result.getModelVersion(),
                toJson(result),
                result.getInferenceTimeMs(),
                result.getAnalysisStatus()
        );
        aiAnalysisLogRepository.save(analysisLog);

        log.info(
                "AI 콜백 결과 저장 완료 aiLogId={}, savedStatus={}, modelVersion={}, inferenceTimeMs={}",
                analysisLog.getId(),
                analysisLog.getAnalysisStatus(),
                analysisLog.getModelVersion(),
                analysisLog.getInferenceTimeMs()
        );

        // 구독 중인 FE에 결과 푸시
        log.info("AI 콜백 SSE 전송 시도 aiLogId={}", analysisLog.getId());
        analysisSseService.emit(analysisLog.getId(), toResultDto(analysisLog));
    }

    /**
     * FE의 분석 결과 SSE 구독을 등록합니다.
     * 이미 분석이 완료된 경우 즉시 결과를 전송하고 연결을 종료합니다.
     */
    public SseEmitter subscribeToResult(Long aiLogId, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 결과 SSE 구독 실패 - 분석 로그 없음 aiLogId={}, userId={}", aiLogId, userId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });

        // 이미 결과가 있으면 즉시 응답
        if (log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            FoodService.log.info("분석 결과 SSE 즉시 응답 aiLogId={}, userId={}, status={}", aiLogId, userId, log.getAnalysisStatus());
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("analysis-complete")
                                .data(toResultDto(log), MediaType.APPLICATION_JSON)
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

    private AiAnalysisLog createAnalysisLog(Long userId) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    FoodService.log.warn("분석 로그 생성 실패 - 사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });

        AiAnalysisLog savedLog = aiAnalysisLogRepository.save(
                AiAnalysisLog.builder().user(user).analysisStatus(AnalysisStatus.PROCESSING).build()
        );

        FoodService.log.info("분석 로그 생성 완료 userId={}, aiLogId={}", userId, savedLog.getId());
        return savedLog;
    }

    private void markFailed(AiAnalysisLog log) {
        log.fail();
        aiAnalysisLogRepository.save(log);
        FoodService.log.warn("분석 로그 상태 실패 처리 aiLogId={}", log.getId());
    }

    private FoodAnalysisResultDto toResultDto(AiAnalysisLog log) throws CustomException {
        String raw = log.getRawOutput();

        // 콜백으로 최종 결과가 저장된 경우 역직렬화해서 반환
        if (StringUtils.hasText(raw) && log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            try {
                return resultProcessorService.process(objectMapper.readValue(raw, FoodAnalysisResultDto.class));
            } catch (Exception e) {
                FoodService.log.error("분석 결과 역직렬화 실패 aiLogId={}, status={}", log.getId(), log.getAnalysisStatus(), e);
                throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
            }
        }

        // 아직 처리 중인 경우 현재 상태 반환
        return FoodAnalysisResultDto.builder()
                .analysisStatus(log.getAnalysisStatus())
                .modelVersion(log.getModelVersion())
                .inferenceTimeMs(log.getInferenceTimeMs())
                .aiLogId(log.getId())
                .build();
    }

    private String toJson(Object value) throws CustomException {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            FoodService.log.error("JSON 직렬화 실패 valueType={}", value != null ? value.getClass().getSimpleName() : null, e);
            throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
        }
    }
}
