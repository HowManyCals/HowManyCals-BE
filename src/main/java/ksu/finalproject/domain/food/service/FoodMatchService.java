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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodMatchService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 200;
    private static final int MANUAL_SEARCH_LIMIT = 20;
    private static final int MIN_BASE_CANDIDATE_POOL = 8;
    private static final int MAIN_CATEGORY_EXACT_BONUS = 20;
    private static final int MAIN_CATEGORY_MISMATCH_PENALTY = 18;

    private static final Map<String, String> TEXT_SYNONYM_REPLACEMENTS = Map.ofEntries(
            Map.entry("후라이드", "프라이드"),
            Map.entry("짜장", "자장"),
            Map.entry("까르보나라", "카르보나라"),
            Map.entry("라테", "라떼"),
            Map.entry("애플 파이", "애플파이")
    );

    private static final Map<String, String> BASE_FOOD_ALIASES = Map.ofEntries(
            Map.entry("치킨", "닭튀김"),
            Map.entry("후라이드치킨", "닭튀김"),
            Map.entry("프라이드치킨", "닭튀김"),
            Map.entry("양념치킨", "닭튀김"),
            Map.entry("치킨윙", "닭튀김"),
            Map.entry("닭가슴살구이", "닭구이"),
            Map.entry("닭가슴살", "닭구이"),
            Map.entry("닭다리구이", "닭구이"),
            Map.entry("닭다리", "닭구이"),
            Map.entry("치킨스테이크", "닭구이"),
            Map.entry("아메리카노", "커피"),
            Map.entry("콜드브루", "커피"),
            Map.entry("드립커피", "커피"),
            Map.entry("에스프레소", "커피"),
            Map.entry("카페라떼", "라떼"),
            Map.entry("애플파이", "파이/만주"),
            Map.entry("미트볼스파게티", "스파게티"),
            Map.entry("바지락칼국수", "칼국수"),
            Map.entry("순대국밥", "국밥")
    );

    private static final Set<String> MODIFIER_NOISE_TERMS = Set.of(
            "세트", "콤보", "단품", "대", "중", "소",
            "라지", "레귤러", "스몰", "미디엄",
            "set", "combo", "single", "large", "regular", "small", "medium",
            "hot", "ice", "iced", "tall", "grande", "venti"
    );

    private static final Pattern MODIFIER_MEASUREMENT_PATTERN = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:g|kg|ml|l|oz|pcs|pc|ea|인분|인|개입|개)\\b"
    );
    private static final Pattern NON_SEARCHABLE_PATTERN = Pattern.compile("[^0-9a-zA-Z가-힣/\\s]");
    private static final Pattern MODIFIER_SPLIT_PATTERN = Pattern.compile("[\\s/_,-]+");

    private final FoodRepository foodRepository;

    public record MatchResult(Food food, int score, String reason) {
    }

    public List<String> getMainCategories() {
        return foodRepository.findDistinctActiveMainCategories();
    }

    public List<MatchResult> matchFoods(String mainCategory, String baseFood, List<String> modifiers, int limit) {
        String normalizedMainCategory = normalizeText(mainCategory);
        String normalizedRawBaseFood = normalizeText(baseFood);
        String normalizedBaseFood = normalizeBaseFood(baseFood);
        List<String> normalizedModifiers = normalizeModifiers(baseFood, modifiers);

        if (!StringUtils.hasText(normalizedBaseFood)) {
            log.info("food-match skip - empty baseFood mainCategory={}, rawBaseFood={}, modifiers={}",
                    mainCategory,
                    baseFood,
                    modifiers);
            return List.of();
        }

        log.info("food-match start mainCategory={}, normalizedMainCategory={}, rawBaseFood={}, normalizedRawBaseFood={}, normalizedBaseFood={}, modifiers={}, normalizedModifiers={}",
                mainCategory,
                normalizedMainCategory,
                baseFood,
                normalizedRawBaseFood,
                normalizedBaseFood,
                modifiers,
                normalizedModifiers);

        Set<Food> candidates = new LinkedHashSet<>();
        List<String> stageLogs = new ArrayList<>();

        int beforeExact = candidates.size();
        candidates.addAll(foodRepository.findAllActiveByNormalizedSubCategoryExact(normalizedBaseFood));
        stageLogs.add("sub_exact +" + (candidates.size() - beforeExact) + " => " + candidates.size());

        addKeywordSearchResults(candidates, stageLogs, "base_keyword_canonical", List.of(normalizedBaseFood));

        if (StringUtils.hasText(normalizedRawBaseFood) && !normalizedRawBaseFood.equals(normalizedBaseFood)) {
            addKeywordSearchResults(candidates, stageLogs, "base_keyword_raw", List.of(normalizedRawBaseFood));
        }

        if (candidates.size() < MIN_BASE_CANDIDATE_POOL && !normalizedModifiers.isEmpty()) {
            addKeywordSearchResults(candidates, stageLogs, "modifier_keyword", normalizedModifiers);
        }

        log.info("food-match candidate-pool mainCategory={}, normalizedBaseFood={}, stageLogs={}",
                normalizedMainCategory,
                normalizedBaseFood,
                stageLogs);

        List<MatchResult> ranked = candidates.stream()
                .map(food -> score(food, normalizedMainCategory, normalizedBaseFood, normalizedModifiers))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(MatchResult::score).reversed()
                        .thenComparing(result -> result.food().getId()))
                .limit(limit)
                .toList();

        log.info("food-match ranked normalizedBaseFood={}, resultCount={}, preview={}",
                normalizedBaseFood,
                ranked.size(),
                summarizeResults(ranked));

        return ranked;
    }

    public List<Food> searchFoods(String query, int limit) {
        String normalizedQuery = normalizeBaseFood(query);
        if (!StringUtils.hasText(normalizedQuery) || normalizedQuery.length() < 2) {
            return List.of();
        }

        LinkedHashSet<Food> results = new LinkedHashSet<>();
        results.addAll(foodRepository.findAllActiveByNormalizedSubCategoryExact(normalizedQuery));
        results.addAll(foodRepository.searchActiveFoodsByNormalizedKeyword(normalizedQuery, PageRequest.of(0, Math.max(limit, MANUAL_SEARCH_LIMIT))));
        return results.stream().limit(limit).toList();
    }

    public String normalizeAndApplySynonyms(String value) {
        return normalizeText(value);
    }

    public String normalizeBaseFood(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        for (Map.Entry<String, String> entry : BASE_FOOD_ALIASES.entrySet()) {
            String alias = normalizeText(entry.getKey());
            if (normalized.equals(alias)) {
                return normalizeText(entry.getValue());
            }
        }

        return normalized;
    }

    private void addKeywordSearchResults(Set<Food> candidates, List<String> stageLogs, String stage, Collection<String> baseKeywords) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String keyword : baseKeywords) {
            addKeywordVariants(keywords, keyword);
        }

        int before = candidates.size();
        for (String keyword : keywords) {
            candidates.addAll(foodRepository.searchActiveFoodsByNormalizedKeyword(keyword, PageRequest.of(0, DEFAULT_CANDIDATE_LIMIT)));
        }
        stageLogs.add(stage + " keywords=" + keywords + " +" + (candidates.size() - before) + " => " + candidates.size());
    }

    private void addKeywordVariants(Set<String> keywords, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        keywords.add(value);
        if (value.length() >= 2) {
            keywords.add(value.substring(0, Math.min(4, value.length())));
        }
    }

    private List<String> normalizeModifiers(String baseFood, Collection<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return List.of();
        }

        String normalizedBaseFood = normalizeBaseFood(baseFood);
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();

        for (String modifier : modifiers) {
            String sanitizedModifier = sanitizeModifier(modifier);
            if (!StringUtils.hasText(sanitizedModifier)) {
                continue;
            }

            addNormalizedModifier(normalizedValues, sanitizedModifier);

            String reducedModifier = stripBaseFoodFromModifier(sanitizedModifier, normalizedBaseFood);
            if (StringUtils.hasText(reducedModifier) && !reducedModifier.equals(sanitizedModifier)) {
                addNormalizedModifier(normalizedValues, reducedModifier);
            }

            for (String token : tokenizeModifier(sanitizedModifier, normalizedBaseFood)) {
                addNormalizedModifier(normalizedValues, token);
            }
        }

        return normalizedValues.stream().toList();
    }

    private void addNormalizedModifier(Set<String> normalizedValues, String value) {
        String normalized = normalizeText(value);
        if (StringUtils.hasText(normalized)) {
            normalizedValues.add(normalized);
        }
    }

    private MatchResult score(Food food, String normalizedMainCategory, String normalizedBaseFood, List<String> normalizedModifiers) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String normalizedSubCategory = normalizeBaseFood(food.getSubCategory());
        String normalizedDetailCategory = normalizeText(food.getDetailCategory());
        String normalizedFoodName = normalizeText(food.getFoodName());
        String normalizedSuffix = normalizeText(extractSuffix(food));
        String normalizedMain = normalizeText(food.getMainCategory());

        if (normalizedBaseFood.equals(normalizedSubCategory)) {
            score += 70;
            reasons.add("sub_category_exact");
        } else if (StringUtils.hasText(normalizedSubCategory) && normalizedSubCategory.contains(normalizedBaseFood)) {
            score += 40;
            reasons.add("sub_category_contains");
        } else if (StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(normalizedBaseFood)) {
            score += 20;
            reasons.add("food_name_contains_base");
        }

        for (String modifier : normalizedModifiers) {
            if (modifier.equals(normalizedDetailCategory)) {
                score += 30;
                reasons.add("detail_category_exact:" + modifier);
            } else if (StringUtils.hasText(normalizedDetailCategory) && normalizedDetailCategory.contains(modifier)) {
                score += 20;
                reasons.add("detail_category_contains:" + modifier);
            }

            if (StringUtils.hasText(normalizedSuffix) && normalizedSuffix.contains(modifier)) {
                score += 25;
                reasons.add("suffix_match:" + modifier);
            } else if (StringUtils.hasText(normalizedFoodName) && normalizedFoodName.contains(modifier)) {
                score += 12;
                reasons.add("food_name_modifier_match:" + modifier);
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

        score -= levenshteinPenalty(normalizedBaseFood, normalizedSubCategory);
        return new MatchResult(food, score, String.join(",", reasons));
    }

    private int levenshteinPenalty(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return 0;
        }
        int distance = levenshtein(source, target);
        return Math.min(distance * 3, 30);
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

    private String sanitizeModifier(String modifier) {
        if (!StringUtils.hasText(modifier)) {
            return null;
        }

        String cleaned = Normalizer.normalize(modifier, Normalizer.Form.NFKC);
        cleaned = MODIFIER_MEASUREMENT_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = NON_SEARCHABLE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = collapseWhitespace(cleaned);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        List<String> filteredTerms = new ArrayList<>();
        for (String term : cleaned.split("\\s+")) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            String lowered = term.toLowerCase(Locale.ROOT);
            if (MODIFIER_NOISE_TERMS.contains(lowered)) {
                continue;
            }
            filteredTerms.add(term);
        }

        return collapseWhitespace(String.join(" ", filteredTerms));
    }

    private String stripBaseFoodFromModifier(String modifier, String normalizedBaseFood) {
        if (!StringUtils.hasText(modifier) || !StringUtils.hasText(normalizedBaseFood)) {
            return modifier;
        }

        String normalizedModifier = normalizeText(modifier);
        if (!StringUtils.hasText(normalizedModifier) || !normalizedModifier.contains(normalizedBaseFood)) {
            return modifier;
        }

        String stripped = normalizedModifier.replace(normalizedBaseFood, "");
        return stripped.length() >= 2 ? stripped : modifier;
    }

    private List<String> tokenizeModifier(String modifier, String normalizedBaseFood) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : MODIFIER_SPLIT_PATTERN.split(modifier)) {
            String normalizedToken = normalizeText(token);
            if (!StringUtils.hasText(normalizedToken)) {
                continue;
            }
            if (normalizedToken.length() < 2) {
                continue;
            }
            if (normalizedToken.equals(normalizedBaseFood)) {
                continue;
            }
            tokens.add(normalizedToken);
        }
        return tokens.stream().toList();
    }

    private String collapseWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        for (Map.Entry<String, String> entry : TEXT_SYNONYM_REPLACEMENTS.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }

        normalized = normalized
                .replace('_', ' ')
                .replaceAll("[^0-9a-zA-Z가-힣/\\s]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();

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
