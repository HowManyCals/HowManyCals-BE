package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodImageAnalyzeResponseDto;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.AiServerProperties;
import ksu.finalproject.global.config.FoodImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final RestTemplate restTemplate;
    private final AiServerProperties aiServerProperties;
    private final FoodImageProperties foodImageProperties;

    public FoodImageAnalyzeResponseDto analyzeFoodImage(MultipartFile image) throws CustomException {
        validateImage(image);

        Path tempFile = null;
        try {
            tempFile = storeTemporarily(image);
            Map<String, Object> analysisResult = requestAiAnalysis(tempFile, image.getContentType());

            return FoodImageAnalyzeResponseDto.builder()
                    .originalFileName(image.getOriginalFilename())
                    .contentType(image.getContentType())
                    .fileSize(image.getSize())
                    .analysisResult(analysisResult)
                    .build();
        } catch (IOException e) {
            throw new CustomException(ResponseCode.FOOD_IMAGE_UPLOAD_FAILED);
        } finally {
            deleteIfExists(tempFile);
        }
    }

    private void validateImage(MultipartFile image) throws CustomException {
        if (image == null || image.isEmpty()) {
            throw new CustomException(ResponseCode.EMPTY_FOOD_IMAGE);
        }

        if (image.getSize() > foodImageProperties.getMaxFileSize()) {
            throw new CustomException(ResponseCode.FOOD_IMAGE_SIZE_EXCEEDED);
        }

        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }

        String extension = resolveExtension(image.getOriginalFilename());
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE);
        }
    }

    private Path storeTemporarily(MultipartFile image) throws IOException {
        Path tempDir = Paths.get(foodImageProperties.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(tempDir);

        String extension = resolveExtension(image.getOriginalFilename());
        Path tempFile = tempDir.resolve(UUID.randomUUID() + "." + extension);
        image.transferTo(tempFile.toFile());
        return tempFile;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestAiAnalysis(Path tempFile, String contentType) throws CustomException {
        if (!StringUtils.hasText(aiServerProperties.getAnalyzeUrl())) {
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }

        HttpHeaders fileHeaders = new HttpHeaders();
        if (StringUtils.hasText(contentType)) {
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        }

        HttpEntity<FileSystemResource> filePart = new HttpEntity<>(new FileSystemResource(tempFile.toFile()), fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServerProperties.getAnalyzeUrl(),
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
            }

            return response.getBody();
        } catch (RestClientException e) {
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }
    }

    private String resolveExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private void deleteIfExists(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }
}

