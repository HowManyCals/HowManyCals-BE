package ksu.finalproject.domain.recommendation.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public final class DietRecommendationEngine {
    private static final double KCAL_PER_KG = 7700.0;
    private static final int MAX_BRANCH_OPTIONS = 32;
    private static final int MAX_CANDIDATES_PER_ROOT_BRANCH = 3;
    private static final int MAX_PATTERN_CANDIDATES = 96;
    private static final int MAX_MEAL_CANDIDATES = 32;
    private static final int DEFAULT_SEARCH_NODES_PER_ROOT_BRANCH = 500;
    private static final int FALLBACK_SEARCH_NODES_PER_ROOT_BRANCH = 2_500;
    private static final int PRIMARY_GROUP_LIMIT_PER_MEAL = 2;
    private static final int ANY_GROUP_LIMIT_PER_MEAL = 6;
    private static final double MEAL_KCAL_TOLERANCE = 0.10;
    private static final ConcurrentMap<Food, Boolean> HEAVY_BREAKFAST_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Food, MealStyle> ONE_DISH_STYLE_CACHE = new ConcurrentHashMap<>();

    public DailyCaloriePlan calculateDailyCalories(
            UserProfile profile,
            Goal goal,
            LocalDate recommendationDate
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(recommendationDate, "recommendationDate");
        validateGoal(goal, recommendationDate);
        if (goal.dailyKcalGoal() != null) {
            return caloriePlanFromTarget(goal.dailyKcalGoal());
        }
        validateProfile(profile, recommendationDate);

        int age = Period.between(profile.birthDate(), recommendationDate).getYears();
        double rmr = calculateRmr(profile.gender(), goal.currentWeightKg(), profile.heightCm(), age);
        double maintenanceKcal = rmr * profile.activityLevel().factor();
        double dailyAdjustmentKcal = ((goal.targetWeightKg() - goal.currentWeightKg()) * KCAL_PER_KG)
                / goal.daysRemaining(recommendationDate);

        double rawTargetKcal = maintenanceKcal + dailyAdjustmentKcal;
        double lowerBound = Math.max(profile.gender().minimumKcal(), maintenanceKcal - 750.0);
        double upperBound = Math.min(3500.0, maintenanceKcal + 500.0);
        int targetDailyKcal = (int) Math.round(clamp(rawTargetKcal, lowerBound, upperBound));

        int breakfastTarget = (int) Math.round(targetDailyKcal * 0.30);
        int lunchTarget = (int) Math.round(targetDailyKcal * 0.35);
        int dinnerTarget = targetDailyKcal - breakfastTarget - lunchTarget;

        return new DailyCaloriePlan(
                targetDailyKcal,
                breakfastTarget,
                lunchTarget,
                dinnerTarget,
                maintenanceKcal,
                dailyAdjustmentKcal
        );
    }

    private static DailyCaloriePlan caloriePlanFromTarget(int targetDailyKcal) {
        int breakfastTarget = (int) Math.round(targetDailyKcal * 0.30);
        int lunchTarget = (int) Math.round(targetDailyKcal * 0.35);
        int dinnerTarget = targetDailyKcal - breakfastTarget - lunchTarget;
        return new DailyCaloriePlan(
                targetDailyKcal,
                breakfastTarget,
                lunchTarget,
                dinnerTarget,
                targetDailyKcal,
                0.0
        );
    }

    public DailyRecommendation recommend(
            UserProfile profile,
            Goal goal,
            List<Food> foods,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights,
            LocalDate recommendationDate
    ) {
        Objects.requireNonNull(foods, "foods");
        return recommend(profile, goal, FoodCatalogIndex.from(foods), eatenGroupsLastSevenDays, preferenceWeights, recommendationDate);
    }

    public DailyRecommendation recommend(
            UserProfile profile,
            Goal goal,
            FoodCatalogIndex foodIndex,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights,
            LocalDate recommendationDate
    ) {
        Objects.requireNonNull(foodIndex, "foodIndex");
        Objects.requireNonNull(eatenGroupsLastSevenDays, "eatenGroupsLastSevenDays");
        Objects.requireNonNull(preferenceWeights, "preferenceWeights");

        DailyCaloriePlan caloriePlan = calculateDailyCalories(profile, goal, recommendationDate);
        List<MealCandidate> breakfastCandidates = generateMealCandidates(
                MealType.BREAKFAST,
                caloriePlan.breakfastTargetKcal(),
                foodIndex,
                eatenGroupsLastSevenDays,
                preferenceWeights
        );
        List<MealCandidate> lunchCandidates = generateMealCandidates(
                MealType.LUNCH,
                caloriePlan.lunchTargetKcal(),
                foodIndex,
                eatenGroupsLastSevenDays,
                preferenceWeights
        );
        List<MealCandidate> dinnerCandidates = generateMealCandidates(
                MealType.DINNER,
                caloriePlan.dinnerTargetKcal(),
                foodIndex,
                eatenGroupsLastSevenDays,
                preferenceWeights
        );

        DailyCandidate best = chooseDailyCandidate(
                caloriePlan,
                breakfastCandidates,
                lunchCandidates,
                dinnerCandidates
        );

        return new DailyRecommendation(caloriePlan, List.of(
                best.breakfast().toRecommendation(MealType.BREAKFAST, caloriePlan.breakfastTargetKcal()),
                best.lunch().toRecommendation(MealType.LUNCH, caloriePlan.lunchTargetKcal()),
                best.dinner().toRecommendation(MealType.DINNER, caloriePlan.dinnerTargetKcal())
        ));
    }

    private List<MealCandidate> generateMealCandidates(
            MealType mealType,
            int targetKcal,
            FoodCatalogIndex foodIndex,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights
    ) {
        List<MealCandidate> ranked = generateMealCandidates(
                mealType,
                targetKcal,
                foodIndex,
                eatenGroupsLastSevenDays,
                preferenceWeights,
                DEFAULT_SEARCH_NODES_PER_ROOT_BRANCH
        );
        if (ranked.isEmpty()) {
            ranked = generateMealCandidates(
                    mealType,
                    targetKcal,
                    foodIndex,
                    eatenGroupsLastSevenDays,
                    preferenceWeights,
                    FALLBACK_SEARCH_NODES_PER_ROOT_BRANCH
            );
        }
        if (ranked.isEmpty()) {
            throw new NoValidRecommendationException("No valid meal candidate for " + mealType);
        }
        return ranked;
    }

    private List<MealCandidate> generateMealCandidates(
            MealType mealType,
            int targetKcal,
            FoodCatalogIndex foodIndex,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights,
            int rootBranchSearchBudget
    ) {
        List<MealCandidate> generated = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        for (MealPattern pattern : mealPatternsFor(mealType)) {
            List<MealCandidate> patternCandidates = new ArrayList<>();
            buildMealFromPattern(
                    pattern,
                    targetKcal,
                    foodIndex,
                    eatenGroupsLastSevenDays,
                    preferenceWeights,
                    patternCandidates,
                    signatures,
                    MAX_PATTERN_CANDIDATES,
                    rootBranchSearchBudget,
                    SearchBudget.unlimited(),
                    0,
                    new ArrayList<>()
            );
            patternCandidates.stream()
                    .sorted(Comparator.comparingDouble(MealCandidate::score).reversed())
                    .limit(MAX_PATTERN_CANDIDATES)
                    .forEach(generated::add);
        }

        List<MealCandidate> sorted = generated.stream()
                .sorted(Comparator.comparingDouble(MealCandidate::score).reversed())
                .toList();
        return rankMealCandidates(sorted);
    }

    private static List<MealCandidate> rankMealCandidates(List<MealCandidate> sorted) {
        List<MealCandidate> ranked = new ArrayList<>();
        Map<String, Integer> primaryGroupUse = new java.util.HashMap<>();
        Map<String, Integer> groupUse = new java.util.HashMap<>();

        for (MealCandidate candidate : sorted) {
            String primaryGroup = primaryGroup(candidate);
            int used = primaryGroupUse.getOrDefault(primaryGroup, 0);
            if (used >= PRIMARY_GROUP_LIMIT_PER_MEAL) {
                continue;
            }
            boolean overGroupLimit = candidate.foods().stream()
                    .anyMatch(food -> groupUse.getOrDefault(food.groupName(), 0) >= ANY_GROUP_LIMIT_PER_MEAL);
            if (overGroupLimit) {
                continue;
            }
            ranked.add(candidate);
            primaryGroupUse.put(primaryGroup, used + 1);
            for (Food food : candidate.foods()) {
                groupUse.put(food.groupName(), groupUse.getOrDefault(food.groupName(), 0) + 1);
            }
            if (ranked.size() >= MAX_MEAL_CANDIDATES) {
                return ranked;
            }
        }

        for (MealCandidate candidate : sorted) {
            if (!ranked.contains(candidate)) {
                ranked.add(candidate);
            }
            if (ranked.size() >= MAX_MEAL_CANDIDATES) {
                break;
            }
        }
        return ranked;
    }

    private static String primaryGroup(MealCandidate candidate) {
        return candidate.foods().stream()
                .filter(food -> food.role() == MealRole.ONE_DISH || food.role() == MealRole.MAIN)
                .findFirst()
                .or(() -> candidate.foods().stream().findFirst())
                .map(Food::groupName)
                .orElse("");
    }

    private static List<MealPattern> mealPatternsFor(MealType mealType) {
        if (mealType == MealType.BREAKFAST) {
            return List.of(
                    new MealPattern("BREAKFAST_RICE_MAIN", MealStyle.RICE_MAIN, List.of(
                            new MealBranch(List.of(MealRole.STAPLE), true, 0.42, FoodAcceptance.ANY),
                            new MealBranch(List.of(MealRole.MAIN), true, 0.46, FoodAcceptance.BREAKFAST_MAIN),
                            new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), false, 0.06, FoodAcceptance.LIGHT_SIDE)
                    )),
                    new MealPattern("BREAKFAST_KOREAN_SET", MealStyle.KOREAN_SET, List.of(
                            new MealBranch(List.of(MealRole.STAPLE), true, 0.38, FoodAcceptance.ANY),
                            new MealBranch(List.of(MealRole.SOUP), false, 0.16, FoodAcceptance.BREAKFAST_SOUP),
                            new MealBranch(List.of(MealRole.MAIN), true, 0.42, FoodAcceptance.BREAKFAST_MAIN),
                            new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), false, 0.05, FoodAcceptance.LIGHT_SIDE)
                    )),
                    new MealPattern("BREAKFAST_RICE_SOUP", MealStyle.LIGHT_MEAL, List.of(
                            new MealBranch(List.of(MealRole.STAPLE), true, 0.64, FoodAcceptance.ANY),
                            new MealBranch(List.of(MealRole.SOUP), true, 0.25, FoodAcceptance.BREAKFAST_SOUP),
                            new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), false, 0.10, FoodAcceptance.LIGHT_SIDE)
                    )),
                    new MealPattern("BREAKFAST_PORRIDGE", MealStyle.PORRIDGE, List.of(
                            new MealBranch(List.of(MealRole.ONE_DISH), true, 1.00, FoodAcceptance.PORRIDGE),
                            new MealBranch(List.of(MealRole.KIMCHI), false, 0.04, FoodAcceptance.LIGHT_SIDE)
                    )),
                    new MealPattern("BREAKFAST_CONVENIENCE", MealStyle.CONVENIENCE, List.of(
                            new MealBranch(List.of(MealRole.ONE_DISH), true, 1.00, FoodAcceptance.CONVENIENCE)
                    ))
            );
        }

        return List.of(
                new MealPattern("KOREAN_SET", MealStyle.KOREAN_SET, List.of(
                        new MealBranch(List.of(MealRole.STAPLE), true, 0.35, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.SOUP, MealRole.STEW), false, 0.18, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.MAIN), true, 0.42, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), true, 0.06, FoodAcceptance.LIGHT_SIDE)
                )),
                new MealPattern("RICE_MAIN", MealStyle.RICE_MAIN, List.of(
                        new MealBranch(List.of(MealRole.STAPLE), true, 0.40, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.MAIN), true, 0.50, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), false, 0.06, FoodAcceptance.LIGHT_SIDE)
                )),
                new MealPattern("ONE_DISH", MealStyle.ONE_DISH, List.of(
                        new MealBranch(List.of(MealRole.ONE_DISH), true, 1.00, FoodAcceptance.ONE_DISH),
                        new MealBranch(List.of(MealRole.KIMCHI), false, 0.05, FoodAcceptance.LIGHT_SIDE)
                )),
                new MealPattern("NOODLE", MealStyle.NOODLE, List.of(
                        new MealBranch(List.of(MealRole.ONE_DISH), true, 1.00, FoodAcceptance.NOODLE),
                        new MealBranch(List.of(MealRole.KIMCHI), false, 0.04, FoodAcceptance.LIGHT_SIDE)
                )),
                new MealPattern("SOUP_BOWL", MealStyle.SOUP_BOWL, List.of(
                        new MealBranch(List.of(MealRole.ONE_DISH), true, 1.00, FoodAcceptance.SOUP_BOWL),
                        new MealBranch(List.of(MealRole.KIMCHI), false, 0.04, FoodAcceptance.LIGHT_SIDE)
                )),
                new MealPattern("PROTEIN_PLATE", MealStyle.PROTEIN_PLATE, List.of(
                        new MealBranch(List.of(MealRole.MAIN), true, 0.72, FoodAcceptance.ANY),
                        new MealBranch(List.of(MealRole.SIDE), true, 0.18, FoodAcceptance.LIGHT_SIDE),
                        new MealBranch(List.of(MealRole.SIDE, MealRole.KIMCHI), false, 0.08, FoodAcceptance.LIGHT_SIDE)
                ))
        );
    }

    private DailyCandidate chooseDailyCandidate(
            DailyCaloriePlan caloriePlan,
            List<MealCandidate> breakfastCandidates,
            List<MealCandidate> lunchCandidates,
            List<MealCandidate> dinnerCandidates
    ) {
        DailyCandidate bestStrict = null;
        DailyCandidate bestRelaxed = null;
        for (MealCandidate breakfast : breakfastCandidates) {
            for (MealCandidate lunch : lunchCandidates) {
                for (MealCandidate dinner : dinnerCandidates) {
                    int totalKcal = breakfast.totalKcal() + lunch.totalKcal() + dinner.totalKcal();
                    double dailyCalorieErrorRatio = Math.abs(totalKcal - caloriePlan.targetDailyKcal())
                            / (double) caloriePlan.targetDailyKcal();
                    double stylePenalty = dailyStylePenalty(breakfast, lunch, dinner);
                    int duplicateGroups = dailyDuplicateGroupCount(breakfast, lunch, dinner);
                    double duplicatePenalty = duplicateGroups * 45.0;
                    double dailyScore = breakfast.score() + lunch.score() + dinner.score()
                            + (clamp(1.0 - dailyCalorieErrorRatio, 0.0, 1.0) * 35.0)
                            - stylePenalty
                            - duplicatePenalty;
                    DailyCandidate candidate = new DailyCandidate(breakfast, lunch, dinner, dailyScore);
                    if (duplicateGroups == 0) {
                        if (bestStrict == null || candidate.score() > bestStrict.score()) {
                            bestStrict = candidate;
                        }
                    } else if (bestRelaxed == null || candidate.score() > bestRelaxed.score()) {
                        bestRelaxed = candidate;
                    }
                }
            }
        }
        if (bestStrict != null) {
            return bestStrict;
        }
        if (bestRelaxed != null) {
            return bestRelaxed;
        }
        throw new NoValidRecommendationException("No valid daily recommendation");
    }

    private static int dailyDuplicateGroupCount(MealCandidate breakfast, MealCandidate lunch, MealCandidate dinner) {
        Set<String> uniqueGroups = new HashSet<>();
        int totalGroups = 0;
        for (MealCandidate meal : List.of(breakfast, lunch, dinner)) {
            for (Food food : meal.foods()) {
                totalGroups++;
                uniqueGroups.add(food.groupName());
            }
        }
        return totalGroups - uniqueGroups.size();
    }

    private static double dailyStylePenalty(MealCandidate breakfast, MealCandidate lunch, MealCandidate dinner) {
        List<MealStyle> styles = List.of(breakfast.style(), lunch.style(), dinner.style());
        long noodleCount = styles.stream().filter(style -> style == MealStyle.NOODLE).count();
        long heavySingleCount = styles.stream()
                .filter(style -> style == MealStyle.ONE_DISH
                        || style == MealStyle.NOODLE
                        || style == MealStyle.CONVENIENCE
                        || style == MealStyle.SOUP_BOWL)
                .count();
        long structuredCount = styles.stream()
                .filter(style -> style == MealStyle.KOREAN_SET
                        || style == MealStyle.RICE_MAIN
                        || style == MealStyle.PROTEIN_PLATE)
                .count();

        double penalty = 0.0;
        if (noodleCount > 1) {
            penalty += (noodleCount - 1) * 80.0;
        }
        if (heavySingleCount > 2) {
            penalty += (heavySingleCount - 2) * 70.0;
        }
        if (structuredCount == 0) {
            penalty += 90.0;
        }
        if (breakfast.style() == MealStyle.NOODLE) {
            penalty += 120.0;
        }
        if (isHeavySingleStyle(dinner.style())) {
            penalty += 25.0;
        }
        if (isHeavySingleStyle(lunch.style()) && isHeavySingleStyle(dinner.style())) {
            penalty += 45.0;
        }
        return penalty;
    }

    private static boolean isHeavySingleStyle(MealStyle style) {
        return style == MealStyle.ONE_DISH
                || style == MealStyle.NOODLE
                || style == MealStyle.SOUP_BOWL;
    }

    private void buildMealFromPattern(
            MealPattern pattern,
            int targetKcal,
            FoodCatalogIndex foodIndex,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights,
            List<MealCandidate> output,
            Set<String> signatures,
            int outputLimit,
            int rootBranchSearchBudget,
            SearchBudget budget,
            int branchIndex,
            List<Food> selected
    ) {
        if (output.size() >= outputLimit) {
            return;
        }
        if (!budget.tryUse()) {
            return;
        }
        if (branchIndex == pattern.branches().size()) {
            List<Food> foods = List.copyOf(selected);
            if (!foods.isEmpty() && hasUniqueGroups(foods) && isNormalMeal(foods)) {
                int totalKcal = foods.stream().mapToInt(Food::calories).sum();
                if (isWithinMealKcalRange(totalKcal, targetKcal) && isAllowedPatternMeal(pattern, foods)) {
                    String signature = foods.stream()
                            .map(Food::groupName)
                            .sorted()
                            .reduce((left, right) -> left + "|" + right)
                            .orElse("");
                    if (signatures.add(signature)) {
                        output.add(scoreCandidate(pattern, targetKcal, preferenceWeights, foods, totalKcal));
                    }
                }
            }
            return;
        }

        MealBranch branch = pattern.branches().get(branchIndex);
        List<Food> options = foodsForBranch(pattern, branch, targetKcal, foodIndex, eatenGroupsLastSevenDays, preferenceWeights, selected);

        if (branchIndex == 0) {
            for (Food food : options) {
                selected.add(food);
                List<MealCandidate> rootOutput = new ArrayList<>();
                SearchBudget rootBudget = new SearchBudget(rootBranchSearchBudget);
                buildMealFromPattern(
                        pattern,
                        targetKcal,
                        foodIndex,
                        eatenGroupsLastSevenDays,
                        preferenceWeights,
                        rootOutput,
                        signatures,
                        MAX_CANDIDATES_PER_ROOT_BRANCH,
                        rootBranchSearchBudget,
                        rootBudget,
                        branchIndex + 1,
                        selected
                );
                selected.remove(selected.size() - 1);
                output.addAll(rootOutput);
                if (output.size() >= outputLimit) {
                    return;
                }
            }
            return;
        }

        if (!branch.required()) {
            buildMealFromPattern(
                    pattern,
                    targetKcal,
                    foodIndex,
                    eatenGroupsLastSevenDays,
                    preferenceWeights,
                    output,
                    signatures,
                    outputLimit,
                    rootBranchSearchBudget,
                    budget,
                    branchIndex + 1,
                    selected
            );
        }

        for (Food food : options) {
            selected.add(food);
            buildMealFromPattern(
                    pattern,
                    targetKcal,
                    foodIndex,
                    eatenGroupsLastSevenDays,
                    preferenceWeights,
                    output,
                    signatures,
                    outputLimit,
                    rootBranchSearchBudget,
                    budget,
                    branchIndex + 1,
                    selected
            );
            selected.remove(selected.size() - 1);
            if (output.size() >= outputLimit || !budget.hasRemaining()) {
                return;
            }
        }
    }

    private List<Food> foodsForBranch(
            MealPattern pattern,
            MealBranch branch,
            int targetKcal,
            FoodCatalogIndex foodIndex,
            Set<String> eatenGroupsLastSevenDays,
            Map<String, Double> preferenceWeights,
            List<Food> selected
    ) {
        Set<String> usedGroups = new HashSet<>();
        int currentKcal = 0;
        for (Food food : selected) {
            usedGroups.add(food.groupName());
            currentKcal += food.calories();
        }
        int maxKcal = mealKcalMax(targetKcal);
        int currentKcalSnapshot = currentKcal;

        Comparator<FoodOption> bestFirst = Comparator
                .comparingDouble(FoodOption::score)
                .reversed()
                .thenComparingInt(FoodOption::order);
        PriorityQueue<FoodOption> bestOptions = new PriorityQueue<>(MAX_BRANCH_OPTIONS, bestFirst.reversed());
        List<Food> foods = foodIndex.foodsForRoles(branch.roles());
        for (int order = 0; order < foods.size(); order++) {
            Food food = foods.get(order);
            if (eatenGroupsLastSevenDays.contains(food.groupName())
                    || usedGroups.contains(food.groupName())
                    || currentKcalSnapshot + food.calories() > maxKcal
                    || !acceptsBranchFood(pattern, branch, food)) {
                continue;
            }

            FoodOption option = new FoodOption(
                    food,
                    branchFoodScore(pattern, branch, targetKcal, preferenceWeights, food),
                    order
            );
            if (bestOptions.size() < MAX_BRANCH_OPTIONS) {
                bestOptions.add(option);
            } else if (bestFirst.compare(option, bestOptions.peek()) < 0) {
                bestOptions.poll();
                bestOptions.add(option);
            }
        }

        return bestOptions.stream()
                .sorted(bestFirst)
                .map(FoodOption::food)
                .toList();
    }

    private static boolean acceptsBranchFood(MealPattern pattern, MealBranch branch, Food food) {
        if (pattern.id().startsWith("BREAKFAST") && isHeavyBreakfastFood(food)) {
            return false;
        }
        if (branch.acceptance() == FoodAcceptance.LIGHT_SIDE) {
            return isLightSide(food);
        }
        if (branch.acceptance() == FoodAcceptance.BREAKFAST_MAIN) {
            return food.role() == MealRole.MAIN && !isHeavyBreakfastFood(food);
        }
        if (branch.acceptance() == FoodAcceptance.BREAKFAST_SOUP) {
            return food.role() == MealRole.SOUP && !isHeavyBreakfastFood(food);
        }
        if (food.role() != MealRole.ONE_DISH) {
            return branch.acceptance() == FoodAcceptance.ANY;
        }

        MealStyle style = oneDishStyle(food);
        return switch (branch.acceptance()) {
            case ANY -> style != MealStyle.NOODLE || !containsAny(foodText(food), "라면", "짜장", "짬뽕", "볶음면");
            case ONE_DISH -> style == MealStyle.ONE_DISH;
            case NOODLE -> style == MealStyle.NOODLE;
            case SOUP_BOWL -> style == MealStyle.SOUP_BOWL;
            case PORRIDGE -> style == MealStyle.PORRIDGE;
            case CONVENIENCE -> style == MealStyle.CONVENIENCE;
            case LIGHT_SIDE, BREAKFAST_MAIN, BREAKFAST_SOUP -> false;
        };
    }

    private static double branchFoodScore(
            MealPattern pattern,
            MealBranch branch,
            int targetKcal,
            Map<String, Double> preferenceWeights,
            Food food
    ) {
        double branchTarget = Math.max(1.0, targetKcal * branch.calorieShare());
        double calorieErrorRatio = Math.abs(food.calories() - branchTarget) / branchTarget;
        double calorieScore = clamp(1.0 - calorieErrorRatio, 0.0, 1.0) * 20.0;
        double preferenceScore = preferenceWeight(food, preferenceWeights) * 4.0;
        double breakfastPenalty = pattern.id().startsWith("BREAKFAST") && food.role() == MealRole.ONE_DISH ? -6.0 : 0.0;
        return calorieScore + preferenceScore + breakfastPenalty;
    }

    private MealCandidate scoreCandidate(
            MealPattern pattern,
            int targetKcal,
            Map<String, Double> preferenceWeights,
            List<Food> foods,
            int totalKcal
    ) {
        double calorieErrorRatio = Math.abs(totalKcal - targetKcal) / (double) targetKcal;
        double calorieScore = clamp(1.0 - calorieErrorRatio, 0.0, 1.0) * 50.0;
        double preferenceScore = clamp(
                foods.stream().mapToDouble(food -> preferenceWeight(food, preferenceWeights)).sum(),
                -3.0,
                3.0
        ) * 5.0;
        double templateScore = styleScore(pattern.style(), foods);
        double breakfastOneDishPenalty = pattern.id().startsWith("BREAKFAST")
                && (pattern.style() == MealStyle.PORRIDGE || pattern.style() == MealStyle.CONVENIENCE)
                ? -10.0
                : 0.0;
        return new MealCandidate(
                List.copyOf(foods),
                totalKcal,
                calorieScore + preferenceScore + templateScore + breakfastOneDishPenalty,
                pattern.style()
        );
    }

    private static double styleScore(MealStyle style, List<Food> foods) {
        double baseScore = switch (style) {
            case KOREAN_SET -> 16.0;
            case ONE_DISH, NOODLE, SOUP_BOWL -> 18.0;
            case CONVENIENCE -> 17.0;
            case PORRIDGE -> 16.0;
            case PROTEIN_PLATE, RICE_MAIN -> 15.0;
            case LIGHT_MEAL -> 12.0;
        };
        boolean lightSideWithOneDish = foods.size() == 2
                && foods.stream().anyMatch(food -> food.role() == MealRole.ONE_DISH)
                && foods.stream().anyMatch(DietRecommendationEngine::isLightSide);
        return lightSideWithOneDish ? baseScore + 2.0 : baseScore;
    }

    private static boolean isAllowedPatternMeal(MealPattern pattern, List<Food> foods) {
        long oneDishCount = foods.stream().filter(food -> food.role() == MealRole.ONE_DISH).count();
        if (oneDishCount == 1) {
            Food oneDish = foods.stream()
                    .filter(food -> food.role() == MealRole.ONE_DISH)
                    .findFirst()
                    .orElseThrow();
            MealStyle style = oneDishStyle(oneDish);
            if (style == MealStyle.CONVENIENCE) {
                return foods.size() == 1;
            }
            return foods.stream().allMatch(food ->
                    food.role() == MealRole.ONE_DISH
                            || (food.role() == MealRole.KIMCHI && isLightSide(food) && allowsKimchiSideWithOneDish(oneDish))
            );
        }
        if (pattern.id().startsWith("BREAKFAST")) {
            return foods.stream().noneMatch(DietRecommendationEngine::isHeavyBreakfastFood);
        }
        return true;
    }

    private static MealStyle oneDishStyle(Food food) {
        return ONE_DISH_STYLE_CACHE.computeIfAbsent(food, DietRecommendationEngine::computeOneDishStyle);
    }

    private static MealStyle computeOneDishStyle(Food food) {
        String text = foodText(food);
        if (containsAny(text, "버거", "햄버거", "핫도그", "피자", "샌드위치", "김밥", "초밥", "롤")) {
            return MealStyle.CONVENIENCE;
        }
        if (containsAny(text, "죽", "스프")) {
            return MealStyle.PORRIDGE;
        }
        if (containsAny(text,
                "비빔밥", "덮밥", "볶음밥", "잡탕밥", "잡채밥", "돌솥밥",
                "오므라이스", "카레", "하이라이스", "회덮밥"
        )) {
            return MealStyle.ONE_DISH;
        }
        if (containsAny(text, "국밥", "해장국", "순대국", "돼지머리국", "설렁탕", "곰탕", "갈비탕", "감자탕", "육개장")) {
            return MealStyle.SOUP_BOWL;
        }
        if (containsAny(text, "국수", "냉면", "우동", "칼국수", "스파게티", "파스타", "면", "짜장", "짬뽕", "라면")) {
            return MealStyle.NOODLE;
        }
        return MealStyle.ONE_DISH;
    }

    private static boolean isLightSide(Food food) {
        String text = foodText(food);
        if (food.role() == MealRole.KIMCHI) {
            return food.calories() <= 90;
        }
        return food.role() == MealRole.SIDE
                && food.calories() <= 120
                && containsAny(text, "김치", "깍두기", "겉절이", "나물", "무침", "샐러드", "장아찌", "오이", "상추", "피클");
    }

    private static boolean allowsKimchiSideWithOneDish(Food food) {
        String text = foodText(food);
        if (containsAny(text,
                "스파게티", "파스타", "리조또", "리소토",
                "오므라이스", "카레", "하이라이스",
                "짜장", "자장", "짬뽕", "마라",
                "냉면", "우동", "모밀", "메밀", "쌀국수",
                "월남쌈"
        )) {
            return false;
        }
        return containsAny(text,
                "비빔밥", "덮밥", "볶음밥", "잡채밥", "돌솥밥",
                "국밥", "해장국", "국수", "칼국수", "수제비",
                "라면", "만두국", "떡만두국", "묵밥", "죽"
        );
    }

    private static boolean isHeavyBreakfastFood(Food food) {
        return HEAVY_BREAKFAST_CACHE.computeIfAbsent(food, DietRecommendationEngine::computeHeavyBreakfastFood);
    }

    private static boolean computeHeavyBreakfastFood(Food food) {
        return containsAny(foodText(food),
                "국밥", "해장국", "순대국", "돼지머리국", "설렁탕", "곰탕", "갈비탕", "감자탕",
                "육개장", "내장탕", "도가니탕", "추어탕", "매운탕",
                "스파게티", "파스타", "리조또", "리소토",
                "오므라이스", "카레", "하이라이스", "덮밥", "비빔밥", "볶음밥", "잡탕밥",
                "냉면", "우동", "쌀국수", "월남쌈",
                "라면", "짜장", "짬뽕", "마라", "떡볶이", "치즈오븐", "그라탕", "볶음면",
                "버거", "햄버거", "핫도그", "피자", "초밥", "롤",
                "닭다리", "통다리", "닭갈비", "닭갈탕", "닭발", "치킨", "불닭",
                "갈비", "등갈비", "삼겹살", "목살", "제육", "족발", "보쌈",
                "돈까스", "돈가스", "탕수육", "깐풍", "꿔바로우", "스테이크",
                "곱창", "막창", "대창", "순대볶음", "오돌뼈"
        );
    }

    private static String foodText(Food food) {
        return food.foodName() + " " + food.groupName();
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithinMealKcalRange(int totalKcal, int targetKcal) {
        return totalKcal >= mealKcalMin(targetKcal) && totalKcal <= mealKcalMax(targetKcal);
    }

    private static int mealKcalMin(int targetKcal) {
        return (int) Math.ceil(targetKcal * (1.0 - MEAL_KCAL_TOLERANCE));
    }

    private static int mealKcalMax(int targetKcal) {
        return (int) Math.floor(targetKcal * (1.0 + MEAL_KCAL_TOLERANCE));
    }

    private static double preferenceWeight(Food food, Map<String, Double> preferenceWeights) {
        return clamp(preferenceWeights.getOrDefault(food.groupName(), 0.0), -3.0, 3.0);
    }

    private static boolean hasUniqueGroups(List<Food> foods) {
        Set<String> groups = new HashSet<>();
        for (Food food : foods) {
            if (!groups.add(food.groupName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDisjointGroups(MealCandidate left, MealCandidate right) {
        Set<String> groups = new HashSet<>();
        for (Food food : left.foods()) {
            groups.add(food.groupName());
        }
        for (Food food : right.foods()) {
            if (groups.contains(food.groupName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNormalMeal(List<Food> foods) {
        int oneDishCount = 0;
        int stapleCount = 0;
        int soupLikeCount = 0;
        boolean containsMealItem = false;
        for (Food food : foods) {
            if (food.role() == MealRole.ONE_DISH) {
                oneDishCount++;
            } else if (food.role() == MealRole.STAPLE) {
                stapleCount++;
            } else if (food.role() == MealRole.SOUP || food.role() == MealRole.STEW) {
                soupLikeCount++;
            }
            if (food.role() != MealRole.BEVERAGE
                    && food.role() != MealRole.SNACK_DESSERT
                    && food.role() != MealRole.SAUCE) {
                containsMealItem = true;
            }
        }

        if (!containsMealItem || oneDishCount > 1 || soupLikeCount > 1) {
            return false;
        }
        return !(oneDishCount == 1 && stapleCount > 0);
    }

    private static double calculateRmr(Gender gender, double weightKg, double heightCm, int age) {
        double male = (9.99 * weightKg) + (6.25 * heightCm) - (4.92 * age) + 5.0;
        double female = (9.99 * weightKg) + (6.25 * heightCm) - (4.92 * age) - 161.0;
        return switch (gender) {
            case MALE -> male;
            case FEMALE -> female;
            case OTHER -> (male + female) / 2.0;
        };
    }

    private static void validateProfile(UserProfile profile, LocalDate recommendationDate) {
        if (profile.heightCm() <= 0) {
            throw new IllegalArgumentException("heightCm must be positive");
        }
        if (!profile.birthDate().isBefore(recommendationDate)) {
            throw new IllegalArgumentException("birthDate must be before recommendationDate");
        }
    }

    private static void validateGoal(Goal goal, LocalDate recommendationDate) {
        if (goal.currentWeightKg() <= 0 || goal.targetWeightKg() <= 0) {
            throw new IllegalArgumentException("weights must be positive");
        }
        if (goal.dailyKcalGoal() != null) {
            return;
        }
        if (!goal.targetDate().isAfter(recommendationDate)) {
            throw new IllegalArgumentException("targetDate must be after recommendationDate");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MealBranch(
            List<MealRole> roles,
            boolean required,
            double calorieShare,
            FoodAcceptance acceptance
    ) {
    }

    private record MealPattern(String id, MealStyle style, List<MealBranch> branches) {
    }

    private record MealCandidate(List<Food> foods, int totalKcal, double score, MealStyle style) {
        MealRecommendation toRecommendation(MealType mealType, int targetKcal) {
            return new MealRecommendation(mealType, targetKcal, totalKcal, score, foods);
        }
    }

    private record DailyCandidate(
            MealCandidate breakfast,
            MealCandidate lunch,
            MealCandidate dinner,
            double score
    ) {
    }

    private record FoodOption(Food food, double score, int order) {
    }

    private static final class SearchBudget {
        private int remaining;

        private SearchBudget(int remaining) {
            this.remaining = remaining;
        }

        private static SearchBudget unlimited() {
            return new SearchBudget(Integer.MAX_VALUE);
        }

        private boolean tryUse() {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }

        private boolean hasRemaining() {
            return remaining > 0;
        }
    }

    public static final class FoodCatalogIndex {
        private final Map<MealRole, List<Food>> foodsByRole;
        private final int indexedFoodCount;

        private FoodCatalogIndex(Map<MealRole, List<Food>> foodsByRole, int indexedFoodCount) {
            this.foodsByRole = foodsByRole;
            this.indexedFoodCount = indexedFoodCount;
        }

        public static FoodCatalogIndex from(List<Food> foods) {
            Objects.requireNonNull(foods, "foods");
            Map<MealRole, List<Food>> mutable = new EnumMap<>(MealRole.class);
            for (MealRole role : MealRole.values()) {
                mutable.put(role, new ArrayList<>());
            }
            int indexed = 0;
            for (Food food : foods) {
                if (food.calories() <= 0 || !food.recommendable()) {
                    continue;
                }
                mutable.get(food.role()).add(food);
                indexed++;
            }

            Map<MealRole, List<Food>> immutable = new EnumMap<>(MealRole.class);
            for (MealRole role : MealRole.values()) {
                List<Food> sorted = mutable.get(role).stream()
                        .sorted(Comparator.comparingInt(Food::calories)
                                .thenComparing(Food::groupName)
                                .thenComparing(Food::foodName))
                        .toList();
                immutable.put(role, sorted);
            }
            return new FoodCatalogIndex(immutable, indexed);
        }

        public int indexedFoodCount() {
            return indexedFoodCount;
        }

        List<Food> foodsForRoles(List<MealRole> roles) {
            if (roles.size() == 1) {
                return foodsByRole.getOrDefault(roles.get(0), List.of());
            }
            List<Food> foods = new ArrayList<>();
            for (MealRole role : roles) {
                foods.addAll(foodsByRole.getOrDefault(role, List.of()));
            }
            return foods;
        }
    }

    public static final class NoValidRecommendationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NoValidRecommendationException(String message) {
            super(message);
        }
    }

    public enum Gender {
        MALE(1500),
        FEMALE(1200),
        OTHER(1350);

        private final int minimumKcal;

        Gender(int minimumKcal) {
            this.minimumKcal = minimumKcal;
        }

        int minimumKcal() {
            return minimumKcal;
        }
    }

    public enum ActivityLevel {
        LEVEL_1(1.20),
        LEVEL_2(1.375),
        LEVEL_3(1.55),
        LEVEL_4(1.725),
        LEVEL_5(1.90);

        private final double factor;

        ActivityLevel(double factor) {
            this.factor = factor;
        }

        double factor() {
            return factor;
        }

        public static ActivityLevel fromValue(int value) {
            return switch (value) {
                case 1 -> LEVEL_1;
                case 2 -> LEVEL_2;
                case 3 -> LEVEL_3;
                case 4 -> LEVEL_4;
                case 5 -> LEVEL_5;
                default -> throw new IllegalArgumentException("activity level must be between 1 and 5");
            };
        }
    }

    public enum MealRole {
        STAPLE,
        SOUP,
        STEW,
        MAIN,
        SIDE,
        KIMCHI,
        ONE_DISH,
        SNACK_DESSERT,
        BEVERAGE,
        SAUCE
    }

    public enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }

    private enum MealStyle {
        KOREAN_SET,
        ONE_DISH,
        NOODLE,
        CONVENIENCE,
        SOUP_BOWL,
        PORRIDGE,
        PROTEIN_PLATE,
        RICE_MAIN,
        LIGHT_MEAL
    }

    private enum FoodAcceptance {
        ANY,
        LIGHT_SIDE,
        BREAKFAST_MAIN,
        BREAKFAST_SOUP,
        ONE_DISH,
        NOODLE,
        SOUP_BOWL,
        PORRIDGE,
        CONVENIENCE
    }

    public record UserProfile(
            int userId,
            LocalDate birthDate,
            Gender gender,
            double heightCm,
            ActivityLevel activityLevel
    ) {
        public UserProfile {
            Objects.requireNonNull(birthDate, "birthDate");
            Objects.requireNonNull(gender, "gender");
            Objects.requireNonNull(activityLevel, "activityLevel");
        }
    }

    public record Goal(
            double currentWeightKg,
            double targetWeightKg,
            LocalDate targetDate,
            Integer dailyKcalGoal
    ) {
        public Goal(double currentWeightKg, double targetWeightKg, LocalDate targetDate) {
            this(currentWeightKg, targetWeightKg, targetDate, null);
        }

        public Goal {
            if (dailyKcalGoal == null) {
                Objects.requireNonNull(targetDate, "targetDate");
            }
            if (dailyKcalGoal != null && dailyKcalGoal <= 0) {
                throw new IllegalArgumentException("dailyKcalGoal must be positive");
            }
        }

        int daysRemaining(LocalDate recommendationDate) {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(recommendationDate, targetDate);
        }
    }

    public record Food(
            int foodId,
            String foodName,
            String groupName,
            MealRole role,
            int calories,
            boolean recommendable
    ) {
        public Food {
            Objects.requireNonNull(foodName, "foodName");
            Objects.requireNonNull(groupName, "groupName");
            Objects.requireNonNull(role, "role");
        }

        public String displayName() {
            int underscore = foodName.indexOf('_');
            if (underscore <= 0 || underscore == foodName.length() - 1) {
                return foodName;
            }

            String baseName = foodName.substring(0, underscore).trim();
            String detailName = foodName.substring(underscore + 1).replace('_', ' ').trim();
            if (baseName.isEmpty() || detailName.isEmpty()) {
                return foodName;
            }
            if (detailName.contains(baseName)) {
                return detailName;
            }
            return detailName + " " + baseName;
        }
    }

    public record DailyCaloriePlan(
            int targetDailyKcal,
            int breakfastTargetKcal,
            int lunchTargetKcal,
            int dinnerTargetKcal,
            double estimatedMaintenanceKcal,
            double dailyAdjustmentKcal
    ) {
    }

    public record MealRecommendation(
            MealType mealType,
            int targetKcal,
            int totalKcal,
            double score,
            List<Food> foods
    ) {
        public MealRecommendation {
            foods = List.copyOf(foods);
        }

        Set<String> groupNames() {
            Set<String> groups = new HashSet<>();
            for (Food food : foods) {
                groups.add(food.groupName());
            }
            return groups;
        }
    }

    public record DailyRecommendation(
            DailyCaloriePlan caloriePlan,
            List<MealRecommendation> meals
    ) {
        public DailyRecommendation {
            meals = List.copyOf(meals);
        }
    }
}
