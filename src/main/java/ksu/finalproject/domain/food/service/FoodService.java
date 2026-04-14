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
import ksu.finalproject.global.config.FoodImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final AiServerClient aiServerClient;
    private final FoodImageProperties foodImageProperties;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisEventEmitter analysisEventEmitter;

    /**
     * 음식 이미지를 AI 서버에 비동기 분석 요청합니다.
     * 분석 요청이 정상 접수되면 202와 함께 ai_log_id를 반환합니다.
     */
    public FoodAnalyzeResponseDto analyzeFoodImage(MultipartFile image, Long userId) throws CustomException {
        validateImage(image);

        AiAnalysisLog log = createAnalysisLog(userId);
        Path tempFile = null;

        try {
            tempFile = storeTemporarily(image);
            FoodAnalyzeResponseDto response = aiServerClient.requestAnalysis(tempFile, image.getContentType(), log.getId());
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
            deleteIfExists(tempFile);
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
                parseStatus(result.getAnalysisStatus())
        );
        aiAnalysisLogRepository.save(log);

        // 구독 중인 FE에 결과 푸시
        analysisEventEmitter.emit(log.getId(), toResultDto(log));
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

        return analysisEventEmitter.register(aiLogId);
    }

    private void validateImage(MultipartFile image) throws CustomException {
        if (image == null || image.isEmpty()) {
            throw new CustomException(ResponseCode.EMPTY_FOOD_IMAGE);
        }
        if (image.getSize() > foodImageProperties.getMaxFileSize()) {
            throw new CustomException(ResponseCode.FOOD_IMAGE_SIZE_EXCEEDED);
        }

        // 파일 타입 검증
        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }

        // 확장자 검증
        String ext = getExtension(image.getOriginalFilename());
        if (!StringUtils.hasText(ext) || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }
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

    private Path storeTemporarily(MultipartFile image) throws IOException {
        Path dir = Paths.get(foodImageProperties.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve(UUID.randomUUID() + "." + getExtension(image.getOriginalFilename()));
        image.transferTo(file.toFile());
        return file;
    }

    private void deleteIfExists(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    private FoodAnalysisResultDto toResultDto(AiAnalysisLog log) throws CustomException {
        String raw = log.getRawOutput();

        // 콜백으로 최종 결과가 저장된 경우 역직렬화해서 반환
        if (StringUtils.hasText(raw) && log.getAnalysisStatus() != AnalysisStatus.PROCESSING) {
            try {
                return objectMapper.readValue(raw, FoodAnalysisResultDto.class);
            } catch (Exception e) {
                throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
            }
        }

        // 아직 처리 중인 경우 현재 상태 반환
        return FoodAnalysisResultDto.builder()
                .analysisStatus(log.getAnalysisStatus().name())
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

    private AnalysisStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) return AnalysisStatus.FAILED;
        try {
            return AnalysisStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AnalysisStatus.FAILED;
        }
    }

    private String getExtension(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return StringUtils.hasText(ext) ? ext.toLowerCase(Locale.ROOT) : "";
    }
}
