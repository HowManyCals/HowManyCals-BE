package ksu.finalproject.domain.food.init;

import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodSeedInitializer implements ApplicationRunner {

    private static final String FOOD_SEED_PATH = "seed/3modified_db.csv";
    private static final String EXPECTED_HEADER =
            "food_name,main_category,sub_category,detail_category," +
                    "base_weight,base_unit,base_kcal,carbs,protein,fat," +
                    "serving_weight,serving_unit,serving_kcal";
    private static final int EXPECTED_COLUMN_COUNT = 13;

    private final FoodRepository foodRepository;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        if (foodRepository.count() > 0) {
            log.info("이미 음식 시드가 있어요.");
            return;
        }

        List<Food> foods = loadFoods();
        if (foods.isEmpty()) {
            log.warn("음식 시드가 비어있어요. path={}", FOOD_SEED_PATH);
            return;
        }

        foodRepository.saveAll(foods);
        log.info("음식 시드 저장 완료. count={}", foods.size());
    }

    private List<Food> loadFoods() {
        ClassPathResource resource = new ClassPathResource(FOOD_SEED_PATH);
        if (!resource.exists()) {
            log.warn("음식 시드 파일이 없어요. path={}", FOOD_SEED_PATH);
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return parseCsv(reader);
        } catch (IOException e) {
            throw new IllegalStateException("음식 시드 파일 접근 실패: " + FOOD_SEED_PATH, e);
        }
    }

    private List<Food> parseCsv(BufferedReader reader) throws IOException {
        List<Food> foods = new ArrayList<>();
        String line;
        boolean headerChecked = false;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();

            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) continue;

            if (!headerChecked) {
                validateHeader(trimmed);
                headerChecked = true;
                continue;
            }

            List<String> columns = splitCsvLine(line);
            if (columns.size() != EXPECTED_COLUMN_COUNT) {
                throw new IllegalStateException(
                        "컬럼 수가 " + EXPECTED_COLUMN_COUNT + "개가 아니에요. line=" + lineNumber
                                + " actual=" + columns.size());
            }

            foods.add(toFood(columns, lineNumber));
        }

        return foods;
    }

    private void validateHeader(String header) {
        String normalized = header.startsWith("\uFEFF") ? header.substring(1) : header;
        if (!EXPECTED_HEADER.equals(normalized.trim()))
            throw new IllegalStateException("음식 시드 헤더가 올바르지 않아요.");
    }

    private Food toFood(List<String> columns, int lineNumber) {
        try {
            return Food.builder()
                    .foodName(columns.get(0).trim())
                    .mainCategory(columns.get(1).trim())
                    .subCategory(columns.get(2).trim())
                    .detailCategory(columns.get(3).trim())
                    .baseWeight(Double.parseDouble(columns.get(4).trim()))
                    .baseUnit(ServingUnit.from(columns.get(5).trim()))
                    .baseKcal(Double.parseDouble(columns.get(6).trim()))
                    .carbohydrate(parseNullableDouble(columns.get(7).trim()))  // null 가능
                    .protein(parseNullableDouble(columns.get(8).trim()))
                    .fat(parseNullableDouble(columns.get(9).trim()))           // null 가능
                    .servingWeight(Double.parseDouble(columns.get(10).trim()))
                    .servingUnit(ServingUnit.from(columns.get(11).trim()))
                    .servingKcal(Double.parseDouble(columns.get(12).trim()))
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException("음식 시드 변환 실패. line=" + lineNumber, e);
        }
    }

    private Double parseNullableDouble(String value) {
        if (!StringUtils.hasText(value)) return null;
        return Double.parseDouble(value);
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("닫히지 않은 따옴표가 포함된 CSV이에요.");
        }

        values.add(current.toString());
        return values;
    }
}

