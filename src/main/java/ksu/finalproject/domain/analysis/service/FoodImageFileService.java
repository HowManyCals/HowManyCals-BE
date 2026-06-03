package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.FoodImageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodImageFileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final FoodImageProperties foodImageProperties;

    public void validate(MultipartFile image) throws CustomException {
        if (image == null || image.isEmpty()) {
            log.warn("음식 이미지 검증 실패 - 빈 파일 요청");
            throw new CustomException(ResponseCode.EMPTY_FOOD_IMAGE);
        }
        if (image.getSize() > foodImageProperties.getMaxFileSize()) {
            log.warn("음식 이미지 검증 실패 - 파일 크기 초과 size={}, maxSize={}", image.getSize(), foodImageProperties.getMaxFileSize());
            throw new CustomException(ResponseCode.FOOD_IMAGE_SIZE_EXCEEDED);
        }

        // 아래 검증 방법들은 완전 검증은 아님.
        // SIGNATURE 또는 Magic Number 기반으로 파싱하는 게 필요할 수도 있음

        // HTTP 헤더 값 파싱 (image/png) 후 검증
        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType)
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            log.warn("음식 이미지 검증 실패 - 허용되지 않은 contentType={}", contentType);
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }

        // 확장자 검증
        String extension = getExtension(image.getOriginalFilename());
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("음식 이미지 검증 실패 - 허용되지 않은 확장자 filename={}, extension={}", image.getOriginalFilename(), extension);
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }
    }

    private String getExtension(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : "";
    }

    public SavedFoodImage save(Long aiLogId, MultipartFile image) throws CustomException, IOException {
        validate(image);

        Path directory = tempDirectory();
        Files.createDirectories(directory);

        deleteByAiLogId(aiLogId);

        String extension = canonicalExtension(image);
        Path path = directory.resolve(aiLogId + "." + extension).toAbsolutePath().normalize();
        image.transferTo(path.toFile());

        log.info("음식 이미지 임시 저장 완료 aiLogId={}, path={}, contentType={}", aiLogId, path, image.getContentType());
        return new SavedFoodImage(path, image.getContentType());
    }

    public byte[] readBytes(Long aiLogId) throws IOException {
        return Files.readAllBytes(requireExistingPath(aiLogId));
    }

    public String detectContentType(Long aiLogId) throws IOException {
        Path normalized = requireExistingPath(aiLogId);
        try {
            String detected = Files.probeContentType(normalized);
            if (StringUtils.hasText(detected)) {
                return detected;
            }
        } catch (IOException e) {
            log.debug("임시 이미지 contentType 감지 실패 path={}: {}", normalized, e.getMessage());
        }

        String extension = getExtension(normalized.getFileName() != null ? normalized.getFileName().toString() : null);
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "webp" -> "image/webp";
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            default -> MediaType.IMAGE_JPEG_VALUE;
        };
    }

    public void deleteByAiLogId(Long aiLogId) {
        if (aiLogId == null) {
            return;
        }

        for (String extension : ALLOWED_EXTENSIONS) {
            Path candidate = tempDirectory().resolve(aiLogId + "." + extension).toAbsolutePath().normalize();
            try {
                if (Files.deleteIfExists(candidate)) {
                    log.info("음식 이미지 임시 파일 정리 완료 aiLogId={}, path={}", aiLogId, candidate);
                }
            } catch (IOException e) {
                log.warn("음식 이미지 임시 파일 정리 실패 aiLogId={}, path={}: {}", aiLogId, candidate, e.getMessage());
            }
        }
    }

    private Path tempDirectory() {
        return Paths.get(foodImageProperties.getTempDir()).toAbsolutePath().normalize();
    }

    private Path requireExistingPath(Long aiLogId) throws IOException {
        Path normalized = resolveExistingPath(aiLogId);
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IOException("temporary image file not found: " + normalized);
        }
        return normalized;
    }

    private Path resolveExistingPath(Long aiLogId) throws IOException {
        if (aiLogId == null) {
            throw new IOException("aiLogId is required");
        }

        for (String extension : ALLOWED_EXTENSIONS) {
            Path candidate = tempDirectory().resolve(aiLogId + "." + extension).toAbsolutePath().normalize();
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException("temporary image file not found for aiLogId=" + aiLogId + ", supportedExtensions=" + Arrays.toString(ALLOWED_EXTENSIONS.toArray()));
    }

    private String canonicalExtension(MultipartFile image) {
        String contentType = image.getContentType();
        if (StringUtils.hasText(contentType)) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case MediaType.IMAGE_PNG_VALUE -> "png";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
        }

        String extension = getExtension(image.getOriginalFilename());
        return StringUtils.hasText(extension) ? extension : "jpg";
    }

    public record SavedFoodImage(Path path, String contentType) {}
}


