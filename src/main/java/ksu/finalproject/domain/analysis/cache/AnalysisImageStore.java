package ksu.finalproject.domain.analysis.cache;

import ksu.finalproject.domain.analysis.service.FoodImageFileService;
import ksu.finalproject.global.common.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class AnalysisImageStore {
    public record CachedImage(byte[] data, String contentType, LocalDateTime savedAt) {}

    private final FoodImageFileService foodImageFileService;

    public AnalysisImageStore(FoodImageFileService foodImageFileService) {
        this.foodImageFileService = foodImageFileService;
    }

    public void save(Long aiLogId, MultipartFile image) throws CustomException, IOException {
        FoodImageFileService.SavedFoodImage savedImage = foodImageFileService.save(aiLogId, image);
        log.info("이미지 임시 파일 저장 완료 aiLogId={}, path={}", aiLogId, savedImage.path());
    }

    public CachedImage consume(Long aiLogId) {
        try {
            CachedImage cachedImage = new CachedImage(
                    foodImageFileService.readBytes(aiLogId),
                    foodImageFileService.detectContentType(aiLogId),
                    LocalDateTime.now()
            );
            foodImageFileService.deleteByAiLogId(aiLogId);
            return cachedImage;
        } catch (IOException e) {
            log.warn("임시 이미지 로드 실패 aiLogId={}: {}", aiLogId, e.getMessage());
            return null;
        }
    }

    public void delete(Long aiLogId) {
        foodImageFileService.deleteByAiLogId(aiLogId);
    }
}
