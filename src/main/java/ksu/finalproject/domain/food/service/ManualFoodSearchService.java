package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.analysis.entity.DataSource;
import ksu.finalproject.domain.food.dto.ManualFoodSearchItemDto;
import ksu.finalproject.domain.food.dto.ManualFoodSearchResponseDto;
import ksu.finalproject.domain.food.entity.Food;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualFoodSearchService {

    private static final int SEARCH_LIMIT = 20;

    private final FoodMatchService foodMatchService;

    public ManualFoodSearchResponseDto search(String query) {
        String normalizedQuery = foodMatchService.normalizeAndApplySynonyms(query);
        if (!StringUtils.hasText(normalizedQuery) || normalizedQuery.length() < 2) {
            return ManualFoodSearchResponseDto.builder()
                    .query(query)
                    .items(List.of())
                    .build();
        }

        List<ManualFoodSearchItemDto> items = foodMatchService.searchFoods(query, SEARCH_LIMIT).stream()
                .map(this::toItem)
                .toList();

        return ManualFoodSearchResponseDto.builder()
                .query(query)
                .items(items)
                .build();
    }

    private ManualFoodSearchItemDto toItem(Food food) {
        return ManualFoodSearchItemDto.builder()
                .foodId(food.getId())
                .foodName(food.getDisplayName())
                .servingKcal(food.getServingKcal())
                .carbohydrate(food.getCarbohydrate())
                .protein(food.getProtein())
                .fat(food.getFat())
                .servingUnitLabel(food.getServingUnit() != null ? food.getServingUnit().toDisplayLabel(food.getServingWeight()) : null)
                .dataSource(DataSource.DB_EXACT)
                .build();
    }
}
