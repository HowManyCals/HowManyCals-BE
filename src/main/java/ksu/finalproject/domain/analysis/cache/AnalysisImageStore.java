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

    public String save(Long aiLogId, MultipartFile image) throws CustomException, IOException {
        FoodImageFileService.SavedFoodImage savedImage = foodImageFileService.save(aiLogId, image);
        log.info("이미지 저장 완료 aiLogId={}, storageKey={}, path={}", aiLogId, savedImage.storageKey(), savedImage.path());
        return savedImage.storageKey();
    }

    public CachedImage load(String imageKey) {
        try {
            FoodImageFileService.LoadedFoodImage loadedImage = foodImageFileService.load(imageKey);
            return new CachedImage(loadedImage.data(), loadedImage.contentType(), LocalDateTime.now());
        } catch (IOException e) {
            log.warn("저장 이미지 로드 실패 imageKey={}: {}", imageKey, e.getMessage());
            return null;
        }
    }

    public void delete(String imageKey) {
        foodImageFileService.delete(imageKey);
    }
}
