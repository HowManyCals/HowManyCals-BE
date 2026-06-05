package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.entity.AiAnalysisLog;
import ksu.finalproject.domain.analysis.repository.AiAnalysisLogRepository;
import ksu.finalproject.domain.foodrecord.repository.FoodRecordRepository;
import ksu.finalproject.global.config.FoodImageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodImageCleanupScheduler {

    private final AiAnalysisLogRepository aiAnalysisLogRepository;
    private final FoodRecordRepository foodRecordRepository;
    private final FoodImageFileService foodImageFileService;
    private final FoodImageProperties foodImageProperties;

    @Transactional
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void cleanupExpiredImages() {
        long retentionDays = foodImageProperties.getRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<AiAnalysisLog> expiredLogs = aiAnalysisLogRepository.findByCreatedAtBeforeAndImageKeyIsNotNull(cutoff);

        int deletedCount = 0;
        int skippedCount = 0;

        for (AiAnalysisLog log : expiredLogs) {
            if (!StringUtils.hasText(log.getImageKey())) {
                continue;
            }

            if (foodRecordRepository.existsByAiAnalysisLogIdAndIsActiveTrue(log.getId())) {
                skippedCount++;
                continue;
            }

            foodImageFileService.delete(log.getImageKey());
            log.clearImageKey();
            aiAnalysisLogRepository.save(log);
            deletedCount++;
        }

        log.info("만료 이미지 정리 완료 cutoff={}, scanned={}, deleted={}, skipped={}",
                cutoff, expiredLogs.size(), deletedCount, skippedCount);
    }
}
