package ksu.finalproject.domain.recommendation.engine;

import java.time.Duration;

public record RecommendationJobResult(
        int userId,
        RecommendationJobStatus status,
        String failureReason,
        Duration elapsed
) {
    public static RecommendationJobResult success(int userId, Duration elapsed) {
        return new RecommendationJobResult(userId, RecommendationJobStatus.SUCCESS, null, elapsed);
    }

    public static RecommendationJobResult failure(int userId, String failureReason, Duration elapsed) {
        return new RecommendationJobResult(userId, RecommendationJobStatus.FAILED, failureReason, elapsed);
    }

    public boolean success() {
        return status == RecommendationJobStatus.SUCCESS;
    }
}
