package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.FoodAlias;
import ksu.finalproject.domain.food.repository.FoodAliasRepository;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodAliasSyncService {

    private final FoodRepository foodRepository;
    private final FoodAliasRepository foodAliasRepository;

    @Transactional
    public void syncIfNeeded() {
        long activeFoodCount = foodRepository.countByIsActiveTrue();
        long activeAliasCount = foodAliasRepository.count();

        if (activeFoodCount == 0 || activeAliasCount >= activeFoodCount) {
            return;
        }

        List<Long> existingFoodIds = foodAliasRepository.findAllActiveFoodIds();
        Set<Long> existingFoodIdSet = new HashSet<>(existingFoodIds);
        List<Food> activeFoods = foodRepository.findAllByIsActiveTrue();

        List<FoodAlias> aliasesToSave = new ArrayList<>();
        for (Food food : activeFoods) {
            if (existingFoodIdSet.contains(food.getId())) {
                continue;
            }

            String canonicalName = food.getCanonicalName();
            String normalizedCanonicalName = food.getNormalizedCanonicalName();
            if (normalizedCanonicalName == null || normalizedCanonicalName.isBlank()) {
                continue;
            }

            aliasesToSave.add(FoodAlias.builder()
                    .food(food)
                    .aliasName(canonicalName)
                    .normalizedAlias(normalizedCanonicalName)
                    .build());
        }

        if (aliasesToSave.isEmpty()) {
            return;
        }

        foodAliasRepository.saveAll(aliasesToSave);
        log.info("음식 alias 동기화 완료 count={}", aliasesToSave.size());
    }
}


