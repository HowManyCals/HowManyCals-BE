package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodMatchService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 200;
    private static final int MANUAL_SEARCH_LIMIT = 20;
    private static final int MIN_FILTERED_CANDIDATE_POOL = 20;
    private static final int MAIN_CATEGORY_EXACT_BONUS = 8;
    private static final int MAIN_CATEGORY_MISMATCH_PENALTY = 8;

    private static final int RECOGNIZED_FOOD_NAME_EXACT_SCORE = 140;
    private static final int RECOGNIZED_CANONICAL_EXACT_SCORE = 130;
    private static final int RECOGNIZED_SUFFIX_EXACT_SCORE = 125;
    private static final int RECOGNIZED_FOOD_NAME_CONTAINS_SCORE = 95;
    private static final int RECOGNIZED_CANONICAL_CONTAINS_SCORE = 85;
    private static final int RECOGNIZED_SUFFIX_CONTAINS_SCORE = 80;
    private static final int SEARCH_TERM_EXACT_SCORE = 75;
    private static final int SEARCH_TERM_CONTAINS_SCORE = 40;
    private static final int BASE_FOOD_SUB_EXACT_SCORE = 55;
    private static final int BASE_FOOD_SUB_CONTAINS_SCORE = 35;
    private static final int BASE_FOOD_FOOD_NAME_CONTAINS_SCORE = 18;
    private static final int MODIFIER_DETAIL_EXACT_SCORE = 24;
    private static final int MODIFIER_SUFFIX_CONTAINS_SCORE = 18;
    private static final int MODIFIER_FOOD_NAME_CONTAINS_SCORE = 12;

    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MEASUREMENT_PATTERN = Pattern.compile("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:g|kg|ml|l|oz|pcs|pc|ea|인분|인|개입|개)\\b");

    private final FoodRepository foodRepository;

    public record MatchResult(Food food, int score, String reason) {
    }

    public List<String> getMainCategories() {
        return foodRepository.findDistinctActiveMainCategories();
    }

    public List<MatchResult> matchFoods(
            String mainCategory,
            String baseFood,
            String recognizedName,
            List<String> modifiers,
            List<String> searchTerms,
            int limit
    ) {
        String normalizedMainCategory = normalizeKey(mainCategory);
        String normalizedBaseFood = normalizeKey(baseFood);
        String normalizedRecognizedName = normalizeKey(recognizedName);
        List<String> normalizedModifiers = normalizeTerms(modifiers, null);
        List<String> normalizedSearchTerms = normalizeTerms(searchTerms, normalizedRecognizedName);

        if (!StringUtils.hasText(normalizedRecognizedName) && !StringUtils.hasText(normalizedBaseFood)) {
            log.info("food-match skip - empty recognized/base mainCategory={}, recognizedName={}, baseFood={}",
                    mainCategory,
                    recognizedName,
                    baseFood);
            return List.of();
        }

        log.info("food-match start mainCategory={}, normalizedMainCategory={}, baseFood={}, normalizedBaseFood={}, recognizedName={}, normalizedRecognizedName={}, modifiers={}, normalizedModifiers={}, searchTerms={}, normalizedSearchTerms={}",
                mainCategory,
                normalizedMainCategory,
                baseFood,
                normalizedBaseFood,
                recognizedName,
                normalizedRecognizedName,
                modifiers,
                normalizedModifiers,
                searchTerms,
                normalizedSearchTerms);

        LinkedHashSet<Food> candidates = new LinkedHashSet<>();
        List<String> stageLogs = new ArrayList<>();

        collectFilteredCandidates(candidates, stageLogs, normalizedMainCategory, normalizedBaseFood);
        collectKeywordCandidates(candidates, stageLogs, normalizedMainCategory, normalizedRecognizedName, normalizedSearchTerms, normalizedBaseFood);

        if (candidates.isEmpty() && StringUtils.hasText(normalizedBaseFood)) {
            int beforeExact = candidates.size();
            candidates.addAll(foodRepository.findAllActiveByNormalizedSubCategoryExact(normalizedBaseFood));
            stageLogs.add("global_sub_exact +" + (candidates.size() - beforeExact) + " => " + candidates.size());
        }

        if (candidates.isEmpty()) {
            addGlobalKeywordSearchResults(candidates, stageLogs, "global_keyword_recognized", List.of(normalizedRecognizedName));
            addGlobalKeywordSearchResults(candidates, stageLogs, "global_keyword_search_terms", normalizedSearchTerms);
        }

        log.info("food-match candidate-pool stageLogs={}, candidateCount={}", stageLogs, candidates.size());

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Food> exactFoodNameMatches = filterFoodNameExact(candidates, normalizedRecognizedName);
        if (!exactFoodNameMatches.isEmpty()) {
            log.info("food-match recognized food_name exact hit count={}", exactFoodNameMatches.size());
            return rankSubset(
                    exactFoodNameMatches,
                    normalizedMainCategory,
                    normalizedBaseFood,
                    normalizedRecognizedName,
                    normalizedModifiers,
                    normalizedSearchTerms,
                    limit
            );
        }

        List<Food> exactCanonicalMatches = filterCanonicalExact(candidates, normalizedRecognizedName);
        if (!exactCanonicalMatches.isEmpty()) {
            log.info("food-match recognized canonical exact hit count={}", exactCanonicalMatches.size());
            return rankSubset(
                    exactCanonicalMatches,
                    normalizedMainCategory,
                    normalizedBaseFood,
                    normalizedRecognizedName,
                    normalizedModifiers,
                    normalizedSearchTerms,
                    limit
            );
        }

        List<Food> exactSearchTermMatches = filterSearchTermExact(candidates, normalizedSearchTerms);
        if (!exactSearchTermMatches.isEmpty()) {
            log.info("food-match search-term exact hit count={}", exactSearchTermMatches.size());
            return rankSubset(
                    exactSearchTermMatches,
                    normalizedMainCategory,
                    normalizedBaseFood,
                    normalizedRecognizedName,
                    normalizedModifiers,
                    normalizedSearchTerms,
                    limit
            );
        }

        List<MatchResult> ranked = rankSubset(
                new ArrayList<>(candidates),
                normalizedMainCategory,
                normalizedBaseFood,
                normalizedRecognizedName,
                normalizedModifiers,
                normalizedSearchTerms,
                limit
        );
        log.info("food-match ranked resultCount={}, preview={}", ranked.size(), summarizeResults(ranked));
        return ranked;
    }

    public List<Food> searchFoods(String query, int limit) {
        String normalizedQuery = normalizeKey(query);
        if (!StringUtils.hasText(normalizedQuery) || normalizedQuery.length() < 2) {
            return List.of();
        }

        LinkedHashSet<Food> results = new LinkedHashSet<>();
        results.addAll(foodRepository.findAllActiveByNormalizedSubCategoryExact(normalizedQuery));
        results.addAll(foodRepository.searchActiveFoodsByNormalizedKeyword(
                normalizedQuery,
                PageRequest.of(0, Math.max(limit, MANUAL_SEARCH_LIMIT))
        ));
        return results.stream().limit(limit).toList();
    }

    public String normalizeAndApplySynonyms(String value) {
        return normalizeKey(value);
    }

    private void collectFilteredCandidates(
            Set<Food> candidates,
            List<String> stageLogs,
            String normalizedMainCategory,
            String normalizedBaseFood
    ) {
        if (!StringUtils.hasText(normalizedMainCategory) || !StringUtils.hasText(normalizedBaseFood)) {
            return;
        }

        int beforeExact = candidates.size();
        candidates.addAll(foodRepository.findAllActiveByNormalizedMainCategoryAndNormalizedSubCategoryExact(
                normalizedMainCategory,
                normalizedBaseFood
        ));
        stageLogs.add("main_sub_exact +" + (candidates.size() - beforeExact) + " => " + candidates.size());

        if (candidates.size() < MIN_FILTERED_CANDIDATE_POOL) {
            int beforeContains = candidates.size();
            candidates.addAll(foodRepository.searchActiveFoodsByNormalizedMainCategoryAndNormalizedSubCategoryContains(
                    normalizedMainCategory,
                    normalizedBaseFood,
                    PageRequest.of(0, DEFAULT_CANDIDATE_LIMIT)
            ));
            stageLogs.add("main_sub_contains +" + (candidates.size() - beforeContains) + " => " + candidates.size());
        }
    }

    private void collectKeywordCandidates(
            Set<Food> candidates,
            List<String> stageLogs,
            String normalizedMainCategory,
            String normalizedRecognizedName,
            List<String> normalizedSearchTerms,
            String normalizedBaseFood
    ) {
        if (StringUtils.hasText(normalizedMainCategory)) {
            addMainKeywordSearchResults(candidates, stageLogs, "main_keyword_recognized", normalizedMainCategory, List.of(normalizedRecognizedName));
            if (candidates.size() < MIN_FILTERED_CANDIDATE_POOL) {
                addMainKeywordSearchResults(candidates, stageLogs, "main_keyword_search_terms", normalizedMainCategory, normalizedSearchTerms);
            }
        }

        if (candidates.isEmpty()) {
            addGlobalKeywordSearchResults(candidates, stageLogs, "global_keyword_recognized", List.of(normalizedRecognizedName));
            addGlobalKeywordSearchResults(candidates, stageLogs, "global_keyword_search_terms", normalizedSearchTerms);
        }

        if (candidates.isEmpty() && StringUtils.hasText(normalizedBaseFood)) {
            addGlobalKeywordSearchResults(candidates, stageLogs, "global_keyword_base_food", List.of(normalizedBaseFood));
        }
    }

    private void addMainKeywordSearchResults(
            Set<Food> candidates,
            List<String> stageLogs,
            String stage,
            String normalizedMainCategory,
            Collection<String> keywords
    ) {
        List<String> normalizedKeywords = compactKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return;
        }

        int before = candidates.size();
        for (String keyword : normalizedKeywords) {
            candidates.addAll(foodRepository.searchActiveFoodsByNormalizedMainCategoryAndNormalizedKeyword(
                    normalizedMainCategory,
                    keyword,
                    PageRequest.of(0, DEFAULT_CANDIDATE_LIMIT)
            ));
        }
        stageLogs.add(stage + " keywords=" + normalizedKeywords + " +" + (candidates.size() - before) + " => " + candidates.size());
    }

    private void addGlobalKeywordSearchResults(
            Set<Food> candidates,
            List<String> stageLogs,
            String stage,
            Collection<String> keywords
    ) {
        List<String> normalizedKeywords = compactKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return;
        }

        int before = candidates.size();
        for (String keyword : normalizedKeywords) {
            candidates.addAll(foodRepository.searchActiveFoodsByNormalizedKeyword(
                    keyword,
                    PageRequest.of(0, DEFAULT_CANDIDATE_LIMIT)
            ));
        }
        stageLogs.add(stage + " keywords=" + normalizedKeywords + " +" + (candidates.size() - before) + " => " + candidates.size());
    }

    private List<String> compactKeywords(Collection<String> keywords) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (keywords == null) {
            return List.of();
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword)) {
                normalized.add(keyword);
            }
        }
        return normalized.stream().toList();
    }

    private List<Food> filterFoodNameExact(Collection<Food> foods, String normalizedRecognizedName) {
        if (!StringUtils.hasText(normalizedRecognizedName)) {
            return List.of();
        }
        return foods.stream()
                .filter(food -> normalizedRecognizedName.equals(normalizeKey(food.getFoodName())))
                .toList();
    }

    private List<Food> filterCanonicalExact(Collection<Food> foods, String normalizedRecognizedName) {
        if (!StringUtils.hasText(normalizedRecognizedName)) {
            return List.of();
        }
        return foods.stream()
                .filter(food -> normalizedRecognizedName.equals(normalizeKey(food.getCanonicalName()))
                        || normalizedRecognizedName.equals(normalizeKey(extractSuffix(food))))
                .toList();
    }

    private List<Food> filterSearchTermExact(Collection<Food> foods, List<String> normalizedSearchTerms) {
        if (normalizedSearchTerms == null || normalizedSearchTerms.isEmpty()) {
            return List.of();
        }

        return foods.stream()
                .filter(food -> {
                    String normalizedFoodName = normalizeKey(food.getFoodName());
                    String normalizedCanonicalName = normalizeKey(food.getCanonicalName());
                    String normalizedSuffix = normalizeKey(extractSuffix(food));
                    return normalizedSearchTerms.stream().anyMatch(term ->
                            term.equals(normalizedFoodName)
                                    || term.equals(normalizedCanonicalName)
                                    || term.equals(normalizedSuffix));
                })
                .toList();
    }

    private List<MatchResult> rankSubset(
            Collection<Food> foods,
            String normalizedMainCategory,
            String normalizedBaseFood,
            String normalizedRecognizedName,
            List<String> normalizedModifiers,
            List<String> normalizedSearchTerms,
            int limit
    ) {
        return foods.stream()
                .map(food -> score(
                        food,
                        normalizedMainCategory,
                        normalizedBaseFood,
                        normalizedRecognizedName,
                        normalizedModifiers,
                        normalizedSearchTerms
                ))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(MatchResult::score).reversed()
                        .thenComparing(result -> result.food().getId()))
                .limit(limit)
                .toList();
    }

    private MatchResult score(
            Food food,
            String normalizedMainCategory,
            String normalizedBaseFood,
            String normalizedRecognizedName,
            List<String> normalizedModifiers,
            List<String> normalizedSearchTerms
    ) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String normalizedFoodName = normalizeKey(food.getFoodName());
        String normalizedCanonicalName = normalizeKey(food.getCanonicalName());
        String normalizedSubCategory = normalizeKey(food.getSubCategory());
        String normalizedDetailCategory = normalizeKey(food.getDetailCategory());
        String normalizedSuffix = normalizeKey(extractSuffix(food));
        String normalizedMain = normalizeKey(food.getMainCategory());

        if (StringUtils.hasText(normalizedRecognizedName)) {
            if (normalizedRecognizedName.equals(normalizedFoodName)) {
                score += RECOGNIZED_FOOD_NAME_EXACT_SCORE;
                reasons.add("recognized_name_food_exact");
            } else if (normalizedRecognizedName.equals(normalizedCanonicalName)) {
                score += RECOGNIZED_CANONICAL_EXACT_SCORE;
                reasons.add("recognized_name_canonical_exact");
            } else if (normalizedRecognizedName.equals(normalizedSuffix)) {
                score += RECOGNIZED_SUFFIX_EXACT_SCORE;
                reasons.add("recognized_name_suffix_exact");
            } else if (StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(normalizedRecognizedName)) {
                score += RECOGNIZED_FOOD_NAME_CONTAINS_SCORE;
                reasons.add("recognized_name_food_contains");
            } else if (StringUtils.hasText(normalizedCanonicalName) && normalizedCanonicalName.contains(normalizedRecognizedName)) {
                score += RECOGNIZED_CANONICAL_CONTAINS_SCORE;
                reasons.add("recognized_name_canonical_contains");
            } else if (StringUtils.hasText(normalizedSuffix) && normalizedSuffix.contains(normalizedRecognizedName)) {
                score += RECOGNIZED_SUFFIX_CONTAINS_SCORE;
                reasons.add("recognized_name_suffix_contains");
            }
        }

        for (String searchTerm : normalizedSearchTerms) {
            if (searchTerm.equals(normalizedFoodName) || searchTerm.equals(normalizedCanonicalName) || searchTerm.equals(normalizedSuffix)) {
                score += SEARCH_TERM_EXACT_SCORE;
                reasons.add("search_term_exact:" + searchTerm);
                continue;
            }

            if ((StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(searchTerm))
                    || (StringUtils.hasText(normalizedCanonicalName) && normalizedCanonicalName.contains(searchTerm))
                    || (StringUtils.hasText(normalizedSuffix) && normalizedSuffix.contains(searchTerm))) {
                score += SEARCH_TERM_CONTAINS_SCORE;
                reasons.add("search_term_contains:" + searchTerm);
            }
        }

        if (StringUtils.hasText(normalizedBaseFood)) {
            if (normalizedBaseFood.equals(normalizedSubCategory)) {
                score += BASE_FOOD_SUB_EXACT_SCORE;
                reasons.add("base_food_sub_exact");
            } else if (StringUtils.hasText(normalizedSubCategory) && normalizedSubCategory.contains(normalizedBaseFood)) {
                score += BASE_FOOD_SUB_CONTAINS_SCORE;
                reasons.add("base_food_sub_contains");
            } else if (StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(normalizedBaseFood)) {
                score += BASE_FOOD_FOOD_NAME_CONTAINS_SCORE;
                reasons.add("base_food_food_name_contains");
            }
        }

        for (String modifier : normalizedModifiers) {
            if (modifier.equals(normalizedDetailCategory)) {
                score += MODIFIER_DETAIL_EXACT_SCORE;
                reasons.add("modifier_detail_exact:" + modifier);
            } else if (StringUtils.hasText(normalizedSuffix) && normalizedSuffix.contains(modifier)) {
                score += MODIFIER_SUFFIX_CONTAINS_SCORE;
                reasons.add("modifier_suffix_contains:" + modifier);
            } else if (StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(modifier)) {
                score += MODIFIER_FOOD_NAME_CONTAINS_SCORE;
                reasons.add("modifier_food_name_contains:" + modifier);
            }
        }

        if (StringUtils.hasText(normalizedMainCategory)) {
            if (normalizedMainCategory.equals(normalizedMain)) {
                score += MAIN_CATEGORY_EXACT_BONUS;
                reasons.add("main_category_exact");
            } else if (StringUtils.hasText(normalizedMain)) {
                score -= MAIN_CATEGORY_MISMATCH_PENALTY;
                reasons.add("main_category_mismatch");
            }
        }

        if (score == 0) {
            return new MatchResult(food, 0, "no_match");
        }

        score -= levenshteinPenalty(normalizedRecognizedName, normalizedCanonicalName);
        return new MatchResult(food, score, String.join(",", reasons));
    }

    private int levenshteinPenalty(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return 0;
        }
        int distance = levenshtein(source, target);
        return Math.min(distance * 2, 20);
    }

    private int levenshtein(String source, String target) {
        int[][] dp = new int[source.length() + 1][target.length() + 1];
        for (int i = 0; i <= source.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= target.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= source.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                int substitutionCost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + substitutionCost
                );
            }
        }
        return dp[source.length()][target.length()];
    }

    private String extractSuffix(Food food) {
        if (!StringUtils.hasText(food.getFoodName()) || !StringUtils.hasText(food.getSubCategory())) {
            return food.getFoodName();
        }

        int underscore = food.getFoodName().indexOf('_');
        if (underscore >= 0 && underscore + 1 < food.getFoodName().length()) {
            return food.getFoodName().substring(underscore + 1);
        }

        String foodName = food.getFoodName();
        String subCategory = food.getSubCategory();
        int subCategoryIndex = foodName.indexOf(subCategory);
        if (subCategoryIndex >= 0) {
            String prefix = foodName.substring(0, subCategoryIndex);
            String suffix = foodName.substring(subCategoryIndex + subCategory.length());
            return (prefix + " " + suffix).trim();
        }

        return foodName;
    }

    private List<String> normalizeTerms(Collection<String> values, String excludedNormalizedValue) {
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        if (values == null) {
            return List.of();
        }

        for (String value : values) {
            String normalized = normalizeKey(sanitizeTerm(value));
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (StringUtils.hasText(excludedNormalizedValue) && excludedNormalizedValue.equals(normalized)) {
                continue;
            }
            normalizedValues.add(normalized);
        }

        return normalizedValues.stream().toList();
    }

    private String sanitizeTerm(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = MEASUREMENT_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        normalized = MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        normalized = normalized.replace(" ", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String summarizeResults(List<MatchResult> results) {
        return results.stream()
                .limit(5)
                .map(result -> result.food().getDisplayName()
                        + "#"
                        + result.food().getId()
                        + "("
                        + result.score()
                        + ","
                        + result.reason()
                        + ")")
                .reduce((left, right) -> left + " | " + right)
                .orElse("none");
    }
}
