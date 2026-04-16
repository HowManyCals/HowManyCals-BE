package ksu.finalproject.domain.food.init;

import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
import ksu.finalproject.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.NonNull;
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

    private static final String FOOD_SEED_PATH = "seed/foods.csv";
    private static final String EXPECTED_HEADER = "food_name,serving_unit,serving_unit_label,serving_weight_g,calories,carbohydrate,protein,fat,is_active";

    private final FoodRepository foodRepository;

    @Override
    @Transactional // 에러나면 rollback + 성공하면 commit
    public void run(@NonNull ApplicationArguments args) {
        if (foodRepository.count() > 0) {
            log.info("이미 음식 시드가 있어요.");
            return;
        }

        List<Food> foods = loadFoods();
        if (foods.isEmpty()) {
            log.warn("음식 시드가 비어있어요. 데이터 파일을 다시 한 번 확인해주세요.", FOOD_SEED_PATH);
            return;
        }

        foodRepository.saveAll(foods);
        log.info("성공적으로 음식 시드를 저장했어요.", foods.size());
    }

    private List<Food> loadFoods() {
        ClassPathResource resource = new ClassPathResource(FOOD_SEED_PATH);
        if (!resource.exists()) {
            log.warn("음식 시드 파일이 존재하지 않아요.", FOOD_SEED_PATH);
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))){
            return parseCsv(reader);
        }
        catch (IOException e) {
            throw new IllegalStateException("음식 시드 파일에 접근할 수 없어요 : " + FOOD_SEED_PATH, e);
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

            // 공백 예외
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                continue;
            }

            // 헤더 예외
            if (!headerChecked) {
                validateHeader(trimmed);
                headerChecked = true;
                continue;
            }

            List<String> columns = splitCsvLine(line);
            if (columns.size() != 9) throw new IllegalStateException("음식 시드 파일의 컬럼 수가 9개가 아니에요. - line " + lineNumber);

            foods.add(toFood(columns, lineNumber));
        }

        return foods;
    }

    private void validateHeader(String header) {
        String normalizedHeader = header.startsWith("\uFEFF") ? header.substring(1) : header;
        if (!EXPECTED_HEADER.equals(normalizedHeader)) throw new IllegalStateException("음식 시드 헤더가 올바르지 않아요.");
    }

    private Food toFood(List<String> columns, int lineNumber) {
        try {
            ServingUnit servingUnit = ServingUnit.extractUnit(columns.get(1).trim());
            String servingAmount = columns.get(2);

            if (!StringUtils.hasText(servingAmount)) throw new IllegalArgumentException("serving_unit_label 값이 비어 있어요.");

            return Food.builder()
                    .foodName(columns.get(0).trim())
                    .servingAmount(servingAmount)
                    .servingUnit(servingUnit)
                    .servingWeightG(Double.parseDouble(columns.get(3).trim()))
                    .calories(Double.parseDouble(columns.get(4).trim()))
                    .carbohydrate(Double.parseDouble(columns.get(5).trim()))
                    .protein(Double.parseDouble(columns.get(6).trim()))
                    .fat(Double.parseDouble(columns.get(7).trim()))
                    .isActive(parseBoolean(columns.get(8).trim()))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("음식 시드 변환 과정에 문제가 생겼어요. - line " + lineNumber, e);
        }
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) return true;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("is_active 값은 true 또는 false여야해요.");
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

