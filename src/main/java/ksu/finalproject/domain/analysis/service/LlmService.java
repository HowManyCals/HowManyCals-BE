package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.cache.AnalysisImageStore;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisRequestDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
import ksu.finalproject.global.config.GoogleGeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    private final RestTemplate restTemplate;
    private final AnalysisImageStore analysisImageStore;
    private final ObjectMapper objectMapper;
    private final GoogleGeminiProperties googleGeminiProperties;

    public LlmFoodAnalysisResponseDto analyzeFromLlm(LlmFoodAnalysisRequestDto request) {
        if (request == null || request.getAiLogId() == null) {
            return null;
        }
        if (!StringUtils.hasText(googleGeminiProperties.getApiKey())) {
            log.warn("Gemini API 키가 비어 있어 LLM fallback 을 건너뜁니다. aiLogId={}", request.getAiLogId());
            return null;
        }

        AnalysisImageStore.CachedImage cachedImage = analysisImageStore.consume(request.getAiLogId());
        if (cachedImage == null || cachedImage.data() == null || cachedImage.data().length == 0) {
            log.warn("LLM fallback 실패 - 캐시된 이미지가 없습니다. aiLogId={}", request.getAiLogId());
            return null;
        }

        String rawText = null;

        try {
            Map<String, Object> requestBody = buildGeminiRequest(cachedImage);
            String requestUrl = googleGeminiProperties.getUrlTemplate().formatted(
                    googleGeminiProperties.getModel(),
                    googleGeminiProperties.getApiKey()
            );
            Object responseObject = restTemplate.postForObject(requestUrl, requestBody, Map.class);
            if (!(responseObject instanceof Map<?, ?> rawResponse)) {
                log.warn("LLM fallback 실패 - Gemini 응답 형식이 올바르지 않습니다. aiLogId={}", request.getAiLogId());
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rawResponse;

            rawText = extractResponseText(response);
            if (!StringUtils.hasText(rawText)) {
                log.warn("LLM fallback 실패 - Gemini 응답 본문이 비어 있습니다. aiLogId={}", request.getAiLogId());
                return null;
            }

            LlmFoodAnalysisResponseDto parsed = parseResponse(rawText, objectMapper);
            if (parsed == null || (!StringUtils.hasText(parsed.getRecognizedName()) && !StringUtils.hasText(parsed.getBaseFood()))) {
                log.warn("LLM fallback 실패 - Gemini 응답 파싱 결과가 비어 있습니다. aiLogId={}, rawTextPreview={}",
                        request.getAiLogId(),
                        preview(rawText));
                return null;
            }

            log.info("LLM fallback 성공 aiLogId={}, originalFoodName={}, recognizedName={}, llmMainCategory={}, llmBaseFood={}, modifierCount={}, searchTermCount={}",
                    request.getAiLogId(),
                    request.getOriginalFoodName(),
                    parsed.getRecognizedName(),
                    parsed.getMainCategory(),
                    parsed.getBaseFood(),
                    parsed.getModifiers() != null ? parsed.getModifiers().size() : 0,
                    parsed.getSearchTerms() != null ? parsed.getSearchTerms().size() : 0);
            return parsed;
        } catch (RestClientException e) {
            log.error("LLM fallback 실패 - Gemini 호출 예외 aiLogId={}", request.getAiLogId(), e);
            return null;
        } catch (Exception e) {
            log.error("LLM fallback 실패 - Gemini 응답 처리 예외 aiLogId={}, rawTextPreview={}",
                    request.getAiLogId(),
                    preview(rawText),
                    e);
            return null;
        }
    }

    private Map<String, Object> buildGeminiRequest(AnalysisImageStore.CachedImage cachedImage) {
        String mimeType = StringUtils.hasText(cachedImage.contentType()) ? cachedImage.contentType() : "image/jpeg";
        String base64Image = Base64.getEncoder().encodeToString(cachedImage.data());

        // 1. 유저 파트에는 순수하게 이미지와 분석 요청 텍스트만 전달
        Map<String, Object> textPart = Map.of("text", "이 음식 이미지를 분석해서 규칙에 맞는 JSON 데이터로 추출해줘.");
        Map<String, Object> imagePart = Map.of(
                "inlineData", Map.of(
                        "mimeType", mimeType,
                        "data", base64Image
                )
        );

        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(textPart, imagePart)
        );

        // 2. Generation Config 설정
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 1.0);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 768); // 768~1024
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

        // 3. 요청 바디 구성 (System Instruction을 별도 필드로 분리)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);

        // 프롬프트 규칙들을 systemInstruction 필드로 바인딩
        requestBody.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", buildPrompt()))
        ));

        return requestBody;
    }

    private String buildPrompt() {
        return """
                너는 음식 이미지에서 우리 서비스의 DB 검색용 구조화 정보를 추출하는 모델이다.

                목표는 메인 음식 1개에 대해 아래 5개 값을 안정적으로 반환하는 것이다.
                1) 사용자에게 보여줄 이름
                2) DB 후보군을 줄이기 위한 분류 값
                3) DB 검색 확장용 보조 키워드

                반드시 아래 규칙을 지켜라.

                [출력 규칙]
                1. 반드시 JSON 객체 1개만 반환한다.
                2. 키는 정확히 다음 5개만 사용한다.
                   - recognized_name
                   - main_category
                   - base_food
                   - modifiers
                   - search_terms
                3. 설명, 이유, 마크다운, 코드블록 없이 JSON만 반환한다.
                4. modifiers는 없으면 빈 배열 []로 반환한다.
                5. search_terms는 없으면 빈 배열 []로 반환한다.
                6. modifiers는 최대 2개까지만 반환한다.
                7. search_terms는 최대 6개까지만 반환한다.
                8. search_terms의 첫 번째 값은 반드시 recognized_name과 동일하게 반환한다.

                [main_category 고정 enum]
                main_category는 아래 25개 값 중 정확히 하나만 반환해야 한다.
                문자, 공백, 구두점까지 그대로 사용한다.

                - 곡류, 서류 제품
                - 과일류
                - 구이류
                - 국 및 탕류
                - 김치류
                - 나물·숙채류
                - 두류, 견과 및 종실류
                - 면 및 만두류
                - 밥류
                - 볶음류
                - 빵 및 과자류
                - 생채·무침류
                - 수·조·어·육류
                - 유제품류 및 빙과류
                - 음료 및 차류
                - 장류, 양념류
                - 장아찌·절임류
                - 전·적 및 부침류
                - 젓갈류
                - 조림류
                - 죽 및 스프류
                - 찌개 및 전골류
                - 찜류
                - 채소, 해조류
                - 튀김류

                [recognized_name 규칙]
                recognized_name은 사용자가 봤을 때 가장 자연스러운 음식 이름이어야 한다.
                여러 음식이 있으면 가장 크고 중심인 메인 음식 1개만 선택한다.
                세트, 사이즈, 수량, 컵 크기 같은 부가 표현은 빼되,
                실제 제품명/메뉴명을 구분하는 핵심 표현은 유지한다.

                좋은 예:
                - 후라이드 치킨
                - 양념 치킨
                - 카페라떼
                - 닭가슴살 직화통살구이
                - 더 건강한 닭가슴살 직화통살구이
                - 바지락 칼국수
                - 새우 감자 피자

                [main_category 판단 기준]
                아래 기준으로 가장 가까운 카테고리 1개를 선택한다.

                - 곡류, 서류 제품: 고구마, 감자범벅, 팝콘처럼 곡물/서류 단품 또는 단순 가공품
                - 과일류: 과일 단품, 과일컵, 과일 기반 식품 중 음식보다 과일 자체가 중심인 경우
                - 구이류: 구이, 불고기, 스테이크, 꼬치구이, 생선구이처럼 굽기가 중심
                - 국 및 탕류: 맑거나 일반적인 국물 요리
                - 김치류: 배추김치, 깍두기, 동치미, 물김치 등 김치류
                - 나물·숙채류: 익히거나 데친 채소/나물 반찬
                - 두류, 견과 및 종실류: 견과, 콩류, 종실류 단품
                - 면 및 만두류: 칼국수, 국수, 냉면, 우동, 짬뽕, 라면, 스파게티, 만두, 떡국
                - 밥류: 국밥, 김밥, 덮밥, 볶음밥, 비빔밥, 초밥, 오므라이스, 카레라이스
                - 볶음류: 떡볶이, 잡채, 김치볶음, 감자볶음, 고기볶음
                - 빵 및 과자류: 피자, 케이크, 버거, 도넛, 샌드위치, 와플, 베이글, 파이/만주, 떡류
                - 생채·무침류: 생채, 겉절이, 샐러드, 초무침
                - 수·조·어·육류: 달걀, 회, 육회, 소시지, 닭가슴살 제품처럼 동물성 식재료/가공식품 중심
                - 유제품류 및 빙과류: 아이스크림, 빙수, 요구르트, 우유, 치즈, 샤베트
                - 음료 및 차류: 커피, 라떼, 스무디, 에이드, 밀크티/버블티, 과·채주스, 허브차, 홍차
                - 장류, 양념류: 소스, 드레싱, 장, 초장, 간장, 카레소스, 짜장소스
                - 장아찌·절임류: 장아찌, 오이지, 단무지, 치킨무
                - 전·적 및 부침류: 전, 부침, 달걀말이, 파전, 김치전, 두부부침
                - 젓갈류: 새우젓, 오징어젓, 게장, 꽃게장, 식해
                - 조림류: 장조림, 고등어조림, 코다리조림, 두부조림
                - 죽 및 스프류: 죽, 미음, 전복죽, 팥죽, 스프
                - 찌개 및 전골류: 찌개, 전골, 매운탕, 감자탕, 부대찌개, 순두부찌개
                - 찜류: 찜, 달걀찜, 갈비찜, 아귀찜, 꼬막찜
                - 채소, 해조류: 채소/해조류 단품 또는 매우 단순한 형태
                - 튀김류: 치킨, 감자튀김, 새우튀김, 오징어튀김, 치즈스틱, 치즈볼, 돈가스, 탕수육

                [base_food 규칙]
                base_food는 recognized_name에서 핵심 음식 타입만 추출한 값이다.
                너무 넓은 카테고리로 일반화하면 안 되고, 전체 메뉴명을 그대로 반복해도 안 된다.
                DB 내부 대표명으로 강제 치환하지 말고, 자연스러운 음식 타입으로 반환한다.

                좋은 예:
                - 바지락 칼국수 -> 칼국수
                - 미트볼 스파게티 -> 스파게티
                - 카페라떼 -> 라떼
                - 새우 감자 피자 -> 피자
                - 후라이드 치킨 -> 치킨
                - 양념 치킨 -> 치킨
                - 닭가슴살 직화통살구이 -> 닭가슴살

                금지 예:
                - 후라이드 치킨 -> 닭튀김
                - 양념 치킨 -> 닭튀김
                - 카페라떼 -> 커피
                - 새우 감자 피자 -> 빵 및 과자류

                [modifiers 규칙]
                modifiers는 base_food를 더 구체화하는 핵심 표현만 반환한다.
                브랜드명, 매장명, 용량, 세트명, 사이즈는 넣지 않는다.
                가능하면 검색에 유리한 구문 단위로 반환한다.

                좋은 예:
                - 바지락 칼국수 -> ["바지락"]
                - 미트볼 스파게티 -> ["미트볼"]
                - 카페라떼 -> ["카페"]
                - 후라이드 치킨 -> ["후라이드"]
                - 양념 치킨 -> ["양념"]
                - 닭가슴살 직화통살구이 -> ["직화통살구이"]
                - 새우 감자 피자 -> ["새우", "감자"]

                [search_terms 규칙]
                search_terms는 DB 검색 확장용 보조 키워드다.
                recognized_name, base_food, modifiers의 의미를 바꾸지 말고,
                같은 메뉴를 다른 표기/언어/띄어쓰기 형태로 찾기 위한 검색어만 넣는다.

                허용:
                - 띄어쓰기/붙여쓰기 변형
                - 한글/영문 표기 변형
                - 제품명에서 브랜드/수식어를 일부 제거한 핵심 검색형

                금지:
                - 더 넓은 카테고리로 일반화
                - 다른 음식으로 의미 변경
                - DB 내부 대표명으로 강제 치환

                좋은 예:
                - 후라이드 치킨 -> ["후라이드 치킨", "후라이드치킨", "프라이드 치킨", "프라이드치킨", "fried chicken"]
                - 카페라떼 -> ["카페라떼", "카페 라떼", "cafe latte"]
                - 새우 감자 피자 -> ["새우 감자 피자", "새우피자", "감자피자", "shrimp pizza", "potato pizza"]
                - 닭가슴살 직화통살구이 -> ["닭가슴살 직화통살구이", "닭가슴살직화통살구이", "직화 닭가슴살", "grilled chicken breast"]
                - 더 건강한 닭가슴살 직화통살구이 -> ["더 건강한 닭가슴살 직화통살구이", "닭가슴살 직화통살구이", "닭가슴살직화통살구이", "직화통살구이", "grilled chicken breast"]

                금지 예:
                - 후라이드 치킨 -> ["닭튀김"]
                - 양념 치킨 -> ["닭튀김"]
                - 카페라떼 -> ["커피"]
                - 새우 감자 피자 -> ["빵", "과자"]

                [불확실성 규칙]
                - main_category는 반드시 1개를 선택한다.
                - recognized_name은 가장 보수적이고 자연스러운 음식명으로 쓴다.
                - base_food는 recognized_name의 핵심 음식 타입만 남긴다.
                - modifiers는 보이는 것만 쓴다.
                - search_terms는 보수적으로 작성한다. 확실하지 않으면 적게 넣는다.

                [출력 예시]
                {"recognized_name":"후라이드 치킨","main_category":"튀김류","base_food":"치킨","modifiers":["후라이드"],"search_terms":["후라이드 치킨","후라이드치킨","프라이드 치킨","프라이드치킨","fried chicken"]}
                {"recognized_name":"카페라떼","main_category":"음료 및 차류","base_food":"라떼","modifiers":["카페"],"search_terms":["카페라떼","카페 라떼","cafe latte"]}
                {"recognized_name":"닭가슴살 직화통살구이","main_category":"수·조·어·육류","base_food":"닭가슴살","modifiers":["직화통살구이"],"search_terms":["닭가슴살 직화통살구이","닭가슴살직화통살구이","직화 닭가슴살","grilled chicken breast"]}
                {"recognized_name":"바지락 칼국수","main_category":"면 및 만두류","base_food":"칼국수","modifiers":["바지락"],"search_terms":["바지락 칼국수","바지락칼국수","clam kalguksu"]}
                {"recognized_name":"새우 감자 피자","main_category":"빵 및 과자류","base_food":"피자","modifiers":["새우","감자"],"search_terms":["새우 감자 피자","새우피자","감자피자","shrimp pizza","potato pizza"]}
                """;
    }

    private String extractResponseText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }

        Object candidatesObject = response.get("candidates");
        if (!(candidatesObject instanceof List<?> candidates) || candidates.isEmpty()) {
            return null;
        }

        Object firstCandidate = candidates.get(0);
        if (!(firstCandidate instanceof Map<?, ?> candidateMap)) {
            return null;
        }

        Object contentObject = candidateMap.get("content");
        if (!(contentObject instanceof Map<?, ?> contentMap)) {
            return null;
        }

        Object partsObject = contentMap.get("parts");
        if (!(partsObject instanceof List<?> parts) || parts.isEmpty()) {
            return null;
        }

        Object firstPart = parts.get(0);
        if (!(firstPart instanceof Map<?, ?> partMap)) {
            return null;
        }

        Object textObject = partMap.get("text");
        return textObject instanceof String text ? text : null;
    }

    static LlmFoodAnalysisResponseDto parseResponse(String rawText, ObjectMapper objectMapper) throws Exception {
        String sanitized = rawText.trim();
        if (sanitized.contains("```")) {
            sanitized = sanitized.replace("```json", "")
                    .replace("```JSON", "")
                    .replace("```", "")
                    .trim();
        }

        String jsonBody = extractFirstJsonObject(sanitized);
        if (!StringUtils.hasText(jsonBody)) {
            return null;
        }

        JsonNode root = objectMapper.readTree(jsonBody.getBytes(StandardCharsets.UTF_8));
        String recognizedName = objectMapper.convertValue(root.path("recognized_name"), String.class);
        String mainCategory = objectMapper.convertValue(root.path("main_category"), String.class);
        String baseFood = objectMapper.convertValue(root.path("base_food"), String.class);
        List<String> modifiers = objectMapper.convertValue(
                root.path("modifiers"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );
        List<String> searchTerms = objectMapper.convertValue(
                root.path("search_terms"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        if (!StringUtils.hasText(recognizedName) && !StringUtils.hasText(baseFood)) {
            return null;
        }

        String normalizedRecognizedName = StringUtils.hasText(recognizedName) ? recognizedName.trim() : null;
        String normalizedBaseFood = StringUtils.hasText(baseFood) ? baseFood.trim() : null;

        LinkedHashSet<String> normalizedSearchTerms = new LinkedHashSet<>();
        if (StringUtils.hasText(normalizedRecognizedName)) {
            normalizedSearchTerms.add(normalizedRecognizedName);
        }
        if (searchTerms != null) {
            searchTerms.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(normalizedSearchTerms::add);
        }

        return LlmFoodAnalysisResponseDto.builder()
                .recognizedName(StringUtils.hasText(normalizedRecognizedName) ? normalizedRecognizedName : normalizedBaseFood)
                .mainCategory(StringUtils.hasText(mainCategory) ? mainCategory.trim() : null)
                .baseFood(normalizedBaseFood)
                .modifiers(modifiers == null ? List.of() : modifiers.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList())
                .searchTerms(normalizedSearchTerms.stream().toList())
                .build();
    }

    static String extractFirstJsonObject(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (current == '{') {
                depth++;
                continue;
            }

            if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private static String preview(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }

        String sanitized = rawText.replaceAll("\\s+", " ").trim();
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }
}
