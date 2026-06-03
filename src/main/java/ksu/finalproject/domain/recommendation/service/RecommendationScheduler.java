package ksu.finalproject.domain.recommendation.service;

import ksu.finalproject.domain.goal.repository.CaloriesGoalRepository;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private final CaloriesGoalRepository caloriesGoalRepository;
    private final RecommendationApplicationService recommendationApplicationService;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void generateTomorrowRecommendations() {
        List<Integer> userIds = caloriesGoalRepository.findDistinctUserIds().stream()
                .map(Math::toIntExact)
                .toList();
        List<RecommendationJobResult> results = recommendationApplicationService.generateTomorrowForUsers(
                userIds,
                ZONE_ID
        );
        long successCount = results.stream().filter(RecommendationJobResult::success).count();
        log.info("추천 식단 배치 완료 total={}, success={}, failed={}",
                results.size(),
                successCount,
                results.size() - successCount);
    }
}
