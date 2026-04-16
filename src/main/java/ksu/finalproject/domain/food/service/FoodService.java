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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

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
            return response;
        } catch (CustomException e) {
            markFailed(log);
            throw e;
        } catch (IOException e) {
            markFailed(log);
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
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS));

        return toResultDto(log);
    }

    /**
     * AI 서버 콜백으로 수신된 분석 결과를 로그에 저장하고, 구독 중인 FE에 즉시 푸시합니다.
     */
    public void saveAnalysisResult(FoodAnalysisResultDto result) throws CustomException {
        if (result == null || result.getAiLogId() == null) {
            throw new CustomException(ResponseCode.BAD_REQUEST);
        }

        AiAnalysisLog log = aiAnalysisLogRepository.findById(result.getAiLogId())
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS));

        log.updateAnalysisResult(
                result.getModelVersion(),
                toJson(result),
                result.getInferenceTimeMs(),
                result.getAnalysisStatus()
        );
        aiAnalysisLogRepository.save(log);

        // 구독 중인 FE에 결과 푸시
        analysisSseService.emit(log.getId(), toResultDto(log));
    }

    /**
     * FE의 분석 결과 SSE 구독을 등록합니다.
     * 이미 분석이 완료된 경우 즉시 결과를 전송하고 연결을 종료합니다.
     */
    public SseEmitter subscribeToResult(Long aiLogId, Long userId) throws CustomException {
        AiAnalysisLog log = aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS));

        // 이미 결과가 있으면 즉시 응답
        if (log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("analysis-complete")
                                .data(toResultDto(log), MediaType.APPLICATION_JSON)
                );
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        return analysisSseService.register(aiLogId);
    }

    private AiAnalysisLog createAnalysisLog(Long userId) throws CustomException {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ResponseCode.NOT_FOUND_USER));

        return aiAnalysisLogRepository.save(
                AiAnalysisLog.builder().user(user).analysisStatus(AnalysisStatus.PROCESSING).build()
        );
    }

    private void markFailed(AiAnalysisLog log) {
        log.fail();
        aiAnalysisLogRepository.save(log);
    }

    private FoodAnalysisResultDto toResultDto(AiAnalysisLog log) throws CustomException {
        String raw = log.getRawOutput();

        // 콜백으로 최종 결과가 저장된 경우 역직렬화해서 반환
        if (StringUtils.hasText(raw) && log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            try {
                return resultProcessorService.process(objectMapper.readValue(raw, FoodAnalysisResultDto.class));
            } catch (Exception e) {
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
            throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
        }
    }
}
