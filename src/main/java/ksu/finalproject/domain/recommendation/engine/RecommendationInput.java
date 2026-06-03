package ksu.finalproject.domain.recommendation.engine;

import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine.Food;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine.Goal;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RecommendationInput(
        int userId,
        int goalId,
        String catalogVersion,
        UserProfile profile,
        Goal goal,
        List<Food> foods,
        Set<String> eatenGroupsLastSevenDays,
        Map<String, Double> preferenceWeights
) {
    public RecommendationInput {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (goalId <= 0) {
            throw new IllegalArgumentException("goalId must be positive");
        }
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("catalogVersion must not be blank");
        }
        Objects.requireNonNull(profile, "profile");
        if (profile.userId() != userId) {
            throw new IllegalArgumentException("profile.userId must match input userId");
        }
        Objects.requireNonNull(goal, "goal");
        foods = List.copyOf(Objects.requireNonNull(foods, "foods"));
        eatenGroupsLastSevenDays = Set.copyOf(Objects.requireNonNull(
                eatenGroupsLastSevenDays,
                "eatenGroupsLastSevenDays"
        ));
        preferenceWeights = Map.copyOf(Objects.requireNonNull(preferenceWeights, "preferenceWeights"));
    }
}
