package ksu.finalproject.domain.food.service;

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

    public record SavedFoodImage(Path path, String contentType) {}

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final FoodImageProperties foodImageProperties;

    public SavedFoodImage save(MultipartFile image) throws CustomException, IOException {
        Path directory = Paths.get(foodImageProperties.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String extension = getExtension(image.getOriginalFilename());
        Path path = directory.resolve(UUID.randomUUID() + "." + extension);
        image.transferTo(path.toFile());

        log.info("음식 이미지 임시 저장 완료 path={}, contentType={}", path, image.getContentType());

        return new SavedFoodImage(path, image.getContentType());
    }

    public void deleteFile(Path file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file);
            log.info("음식 이미지 임시 파일 정리 완료 path={}", file);
        } catch (IOException e) {
            log.warn("음식 이미지 임시 파일 정리 실패 path={}: {}", file, e.getMessage());
        }
    }

    // validateImage open 용도
    public void validate(MultipartFile image) throws CustomException {
        validateImage(image);
    }

    private void validateImage(MultipartFile image) throws CustomException {
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
            log.warn("음식 이미지 검증 실패 - 허용되지 않은 contentType={}", contentType);
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }

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


}


