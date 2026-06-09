package ksu.finalproject.domain.recommendation.service;

import ksu.finalproject.domain.recommendation.entity.DailyMealRecommendation;
import ksu.finalproject.domain.recommendation.entity.MealRecommendationItem;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine.DailyRecommendation;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine.Food;
import ksu.finalproject.domain.recommendation.engine.RecommendationInput;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobResult;
import ksu.finalproject.domain.recommendation.repository.DailyMealRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationApplicationService {
    private final DietRecommendationEngine engine;
    private final RecommendationInputService recommendationInputService;
    private final RecommendationPersistenceService recommendationPersistenceService;
    private final DailyMealRecommendationRepository dailyMealRecommendationRepository;

    public RecommendationJobResult generateForUser(int userId, LocalDate recommendationDate) {
        Objects.requireNonNull(recommendationDate, "recommendationDate");
        Instant startedAt = Instant.now();
        RecommendationInput input = null;
        try {
            input = recommendationInputService.loadInput(userId, recommendationDate);
            validateInputOwner(userId, input);
            input = excludePreviousRecommendation(input, recommendationDate);
            DailyRecommendation recommendation = engine.recommend(
                    input.profile(),
                    input.goal(),
                    input.foods(),
                    input.eatenGroupsLastSevenDays(),
                    input.preferenceWeights(),
                    recommendationDate
            );
            recommendationPersistenceService.saveSuccess(input, recommendationDate, recommendation);
            return RecommendationJobResult.success(userId, elapsedSince(startedAt));
        } catch (RuntimeException ex) {
            String reason = failureReason(ex);
            recommendationPersistenceService.saveFailure(userId, recommendationDate, input == null ? null : input.goalId(), reason);
            return RecommendationJobResult.failure(userId, reason, elapsedSince(startedAt));
        }
    }

    public List<RecommendationJobResult> generateForUsers(List<Integer> userIds, LocalDate recommendationDate) {
        Objects.requireNonNull(userIds, "userIds");
        List<RecommendationJobResult> results = new ArrayList<>(userIds.size());
        for (Integer userId : userIds) {
            results.add(generateForUser(userId, recommendationDate));
        }
        return List.copyOf(results);
    }

    public List<RecommendationJobResult> generateTomorrowForUsers(List<Integer> userIds, ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        LocalDate recommendationDate = LocalDate.now(zoneId).plusDays(1);
        return generateForUsers(userIds, recommendationDate);
    }

    private Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static void validateInputOwner(int requestedUserId, RecommendationInput input) {
        if (input.userId() != requestedUserId) {
            throw new IllegalArgumentException("loaded recommendation input userId does not match requested userId");
        }
    }

    private RecommendationInput excludePreviousRecommendation(RecommendationInput input, LocalDate recommendationDate) {
        return dailyMealRecommendationRepository
                .findWithItemsByUserIdAndRecommendationDate((long) input.userId(), recommendationDate)
                .map(previousRecommendation -> applyPreviousRecommendationExclusion(input, previousRecommendation))
                .orElse(input);
    }

    private RecommendationInput applyPreviousRecommendationExclusion(
            RecommendationInput input,
            DailyMealRecommendation previousRecommendation
    ) {
        Set<Integer> excludedFoodIds = previousRecommendation.getItems().stream()
                .map(MealRecommendationItem::getFood)
                .filter(Objects::nonNull)
                .map(food -> Math.toIntExact(food.getId()))
                .collect(Collectors.toUnmodifiableSet());

        if (excludedFoodIds.isEmpty()) {
            return input;
        }

        List<Food> filteredFoods = input.foods().stream()
                .filter(food -> !excludedFoodIds.contains(food.foodId()))
                .toList();

        if (filteredFoods.isEmpty()) {
            throw new IllegalArgumentException("no recommendable foods remain after excluding previous recommendation");
        }

        return new RecommendationInput(
                input.userId(),
                input.goalId(),
                input.catalogVersion(),
                input.profile(),
                input.goal(),
                filteredFoods,
                input.eatenGroupsLastSevenDays(),
                input.preferenceWeights()
        );
    }

    private static String failureReason(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}


