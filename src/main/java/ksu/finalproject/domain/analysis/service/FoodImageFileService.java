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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType)
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            log.warn("음식 이미지 검증 실패 - 지원하지 않는 contentType={}", contentType);
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }

        String extension = getExtension(image.getOriginalFilename());
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("음식 이미지 검증 실패 - 지원하지 않는 확장자 filename={}, extension={}", image.getOriginalFilename(), extension);
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }
    }

    public SavedFoodImage save(Long aiLogId, MultipartFile image) throws CustomException, IOException {
        validate(image);

        String storageKey = generateStorageKey(image);
        Path path = resolvePath(storageKey);
        Files.createDirectories(path.getParent());
        image.transferTo(path.toFile());

        log.info("음식 이미지 저장 완료 aiLogId={}, storageKey={}, path={}, contentType={}",
                aiLogId, storageKey, path, image.getContentType());
        return new SavedFoodImage(storageKey, path, image.getContentType());
    }

    public LoadedFoodImage load(String storageKey) throws IOException {
        Path path = requireExistingPath(storageKey);
        return new LoadedFoodImage(
                Files.readAllBytes(path),
                detectContentType(path)
        );
    }

    public void delete(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }

        Path path = resolvePath(storageKey);
        try {
            if (Files.deleteIfExists(path)) {
                log.info("음식 이미지 파일 삭제 완료 storageKey={}, path={}", storageKey, path);
                deleteEmptyParents(path.getParent());
            }
        } catch (IOException e) {
            log.warn("음식 이미지 파일 삭제 실패 storageKey={}, path={}: {}", storageKey, path, e.getMessage());
        }
    }

    private Path resolvePath(String storageKey) {
        String normalizedKey = normalizeStorageKey(storageKey);
        String firstShard = normalizedKey.substring(0, 2);
        String secondShard = normalizedKey.substring(2, 4);

        Path path = storageDirectory()
                .resolve(firstShard)
                .resolve(secondShard)
                .resolve(normalizedKey)
                .toAbsolutePath()
                .normalize();

        if (!path.startsWith(storageDirectory())) {
            throw new IllegalArgumentException("invalid storage key path");
        }
        return path;
    }

    private Path requireExistingPath(String storageKey) throws IOException {
        Path normalized = resolvePath(storageKey);
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IOException("stored image file not found: " + normalized);
        }
        return normalized;
    }

    private String detectContentType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            if (StringUtils.hasText(detected)) {
                return detected;
            }
        } catch (IOException e) {
            log.debug("이미지 contentType 감지 실패 path={}: {}", path, e.getMessage());
        }

        String extension = getExtension(path.getFileName() != null ? path.getFileName().toString() : null);
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.IMAGE_JPEG_VALUE;
        };
    }

    private void deleteEmptyParents(Path directory) {
        Path root = storageDirectory();
        Path current = directory;

        while (current != null && !current.equals(root)) {
            try {
                if (Files.list(current).findAny().isPresent()) {
                    return;
                }
                Files.deleteIfExists(current);
            } catch (IOException e) {
                return;
            }
            current = current.getParent();
        }
    }

    private String generateStorageKey(MultipartFile image) {
        return UUID.randomUUID() + "." + canonicalExtension(image);
    }

    private String normalizeStorageKey(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new IllegalArgumentException("storageKey is required");
        }

        String normalized = storageKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new IllegalArgumentException("invalid storageKey");
        }
        if (normalized.length() < 6) {
            throw new IllegalArgumentException("storageKey is too short");
        }
        return normalized;
    }

    private Path storageDirectory() {
        return Paths.get(foodImageProperties.getStorageDir()).toAbsolutePath().normalize();
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

    private String getExtension(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : "";
    }

    public record SavedFoodImage(String storageKey, Path path, String contentType) {}

    public record LoadedFoodImage(byte[] data, String contentType) {}
}
