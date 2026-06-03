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
            if (parsed == null || !StringUtils.hasText(parsed.getBaseFood())) {
                log.warn("LLM fallback 실패 - Gemini 응답 파싱 결과가 비어 있습니다. aiLogId={}, rawTextPreview={}",
                        request.getAiLogId(),
                        preview(rawText));
                return null;
            }

            log.info("LLM fallback 성공 aiLogId={}, originalFoodName={}, llmMainCategory={}, llmBaseFood={}, modifierCount={}",
                    request.getAiLogId(),
                    request.getOriginalFoodName(),
                    parsed.getMainCategory(),
                    parsed.getBaseFood(),
                    parsed.getModifiers() != null ? parsed.getModifiers().size() : 0);
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

        Map<String, Object> textPart = Map.of("text", buildPrompt());
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

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("topP", 0.8);
        generationConfig.put("maxOutputTokens", 256);
        generationConfig.put("responseMimeType", "application/json");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private String buildPrompt() {
        return """
                너는 음식 이미지에서 우리 서비스 DB 검색용 구조화 정보를 추출하는 모델이다.
                목표는 자연스러운 음식명을 말하는 것이 아니라, DB 검색에 유리한 3개 필드
                `main_category`, `base_food`, `modifiers`
                를 안정적으로 반환하는 것이다.

                반드시 아래 규칙을 지켜라.

                [출력 규칙]
                1. 반드시 JSON 객체 1개만 반환한다.
                2. 키는 정확히 다음 3개만 사용한다.
                   - main_category
                   - base_food
                   - modifiers
                3. 설명, 이유, 마크다운, 코드블록 없이 JSON만 반환한다.
                4. modifiers는 없으면 빈 배열 []로 반환한다.

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

                [main_category 판단 기준]
                아래 기준으로 가장 가까운 카테고리 1개를 선택한다.

                - 곡류, 서류 제품: 고구마, 감자범벅, 팝콘처럼 곡물/서류 단품 또는 단순 가공품
                - 과일류: 과일 단품, 과일컵, 과일 기반 식품 중 음식보다 과일 자체가 중심인 경우
                - 구이류: 구이, 불고기, 스테이크, 꼬치구이, 생선구이처럼 굽기가 중심
                - 국 및 탕류: 맑거나 일반적인 국물 요리. 예: 된장국, 무국, 미역국, 곰탕, 갈비탕
                - 김치류: 배추김치, 깍두기, 동치미, 물김치 등 김치류
                - 나물·숙채류: 익히거나 데친 채소/나물 반찬. 예: 시금치나물, 숙주나물, 콩나물무침
                - 두류, 견과 및 종실류: 견과, 콩류, 종실류 단품
                - 면 및 만두류: 칼국수, 국수, 냉면, 우동, 짬뽕, 라면, 스파게티, 만두, 떡국, 떡만두국
                - 밥류: 밥이 정체성의 중심인 요리. 예: 국밥, 김밥, 덮밥, 볶음밥, 비빔밥, 초밥, 주먹밥, 오므라이스, 카레라이스
                - 볶음류: 볶음이 중심인 요리. 예: 떡볶이, 잡채, 김치볶음, 감자볶음, 고기볶음
                - 빵 및 과자류: 피자, 케이크, 버거, 도넛, 샌드위치, 와플, 마카롱, 베이글, 파이/만주, 떡류, 제과류
                - 생채·무침류: 생채, 겉절이, 샐러드, 초무침처럼 생채소/해산물 무침이 중심
                - 수·조·어·육류: 달걀, 회, 육회, 소시지, 대구포처럼 동물성 식재료 단품/단순 식품
                - 유제품류 및 빙과류: 아이스크림, 빙수, 요구르트, 우유, 치즈, 샤베트
                - 음료 및 차류: 커피, 라떼, 스무디, 에이드, 밀크티/버블티, 과ㆍ채주스, 허브차, 홍차
                - 장류, 양념류: 소스, 드레싱, 장, 초장, 간장, 카레소스, 짜장소스 등 양념/소스 제품
                - 장아찌·절임류: 장아찌, 오이지, 단무지, 치킨무 등 절임류
                - 전·적 및 부침류: 전, 부침, 달걀말이, 파전, 김치전, 두부부침처럼 팬에 부친 음식
                - 젓갈류: 새우젓, 오징어젓, 게장, 꽃게장, 식해 등
                - 조림류: 간장/양념/국물에 졸이거나 조린 음식. 예: 장조림, 고등어조림, 코다리조림, 두부조림
                - 죽 및 스프류: 죽, 미음, 전복죽, 팥죽, 스프
                - 찌개 및 전골류: 찌개, 전골, 매운탕, 감자탕, 부대찌개, 순두부찌개처럼 걸쭉하거나 강한 국물의 한 냄비 요리
                - 찜류: 찜, 달걀찜, 갈비찜, 아귀찜, 꼬막찜처럼 찌는 조리법이 중심
                - 채소, 해조류: 채소/해조류 단품 또는 매우 단순한 형태
                - 튀김류: 닭튀김, 감자튀김, 새우튀김, 오징어튀김, 치즈스틱, 치즈볼, 돈가스, 탕수육처럼 튀김이 중심

                [중요한 경계 규칙]
                1. 국물 음식 경계
                - 면이 중심이면 무조건 `면 및 만두류`
                - 밥이 중심이면 무조건 `밥류`
                - 죽/미음/스프면 `죽 및 스프류`
                - 찌개/전골/매운탕/감자탕처럼 진하거나 전골형이면 `찌개 및 전골류`
                - 그 외 일반 국/탕은 `국 및 탕류`

                2. 밥류 우선 규칙
                - 국밥, 덮밥, 볶음밥, 비빔밥, 김밥, 주먹밥, 초밥, 오므라이스, 카레라이스는 국물이나 토핑이 보여도 `밥류`

                3. 조리 방식 경계
                - 팬에 부친 것: `전·적 및 부침류`
                - 기름에 튀긴 것: `튀김류`
                - 불에 굽거나 직화/석쇠/그릴: `구이류`
                - 양념/국물에 조려낸 것: `조림류`
                - 찐 것: `찜류`
                - 볶은 것: `볶음류`

                4. 채소 반찬 경계
                - 생채, 겉절이, 샐러드, 초무침: `생채·무침류`
                - 데치거나 익힌 나물 반찬: `나물·숙채류`
                - 김치류면 `김치류`
                - 장아찌/절임이면 `장아찌·절임류`

                5. 음료/디저트 경계
                - 액체 음료는 `음료 및 차류`
                - 아이스크림/빙수/요구르트/우유/치즈는 `유제품류 및 빙과류`
                - 피자, 버거, 샌드위치, 케이크, 도넛, 파이류는 `빵 및 과자류`

                [base_food 규칙]
                base_food는 서비스 DB의 sub_category에 최대한 가깝게 잡는다.
                즉, 상품명 전체가 아니라 “핵심 음식 타입”을 반환한다.
                브랜드명, 매장명, 용량, 개수, 세트명, 광고 문구는 제외한다.
                base_food는 자연어 음식명이 아니라, 서비스 DB의 sub_category에 저장될 법한 대표명으로 반환한다.

                좋은 예:
                - 바지락 칼국수 -> 칼국수
                - 순대국밥 -> 국밥
                - 후라이드 치킨 -> 닭튀김
                - 카페라떼 -> 라떼
                - 아메리카노 -> 커피
                - 클래식 애플파이 -> 파이/만주
                - 미트볼 스파게티 -> 스파게티
                - 불고기피자 -> 피자

                나쁜 예:
                - “맛있는 후라이드 치킨 세트”
                - “OO브랜드 아메리카노”
                - “대컵 카페라떼”
                - “불고기 피자 M”

                [modifiers 규칙]
                modifiers는 base_food를 더 구체화하는 변형명만 반환한다.
                모르면 추측하지 말고 []를 반환한다.
                modifiers는 detail_category 또는 food_name suffix 검색에 직접 사용할 수 있는 표현으로 반환한다.
                modifiers는 최대 2개까지만 반환한다.

                포함 가능:
                - 핵심 재료
                - 조리 스타일
                - 맛/시즈닝
                - 고정된 변형명

                제외:
                - 브랜드명
                - 매장명
                - 용량
                - 개수
                - 세트/프로모션 문구
                - 포장 상태 설명

                좋은 예:
                - 바지락 칼국수 -> ["바지락"]
                - 순대국밥 -> ["순대국밥"]
                - 후라이드 치킨 -> ["후라이드 치킨"]
                - 카페라떼 -> ["카페라떼"]
                - 클래식 애플파이 -> ["클래식애플파이"]
                - 미트볼 스파게티 -> ["미트볼"]
                - 불고기피자 -> ["불고기피자"]

                [복수 음식 규칙]
                - 사진에 여러 음식이 있으면 가장 크고 중심인 메인 음식 1개만 선택한다.
                - 반찬, 토핑, 사이드, 음료는 메인 음식이 아니면 선택하지 않는다.
                - 다만 메인 음식을 구분하는 핵심 재료라면 modifiers에만 반영한다.

                [불확실성 규칙]
                - main_category는 반드시 1개를 선택한다.
                - base_food는 가장 보수적이고 안정적인 핵심 음식 타입을 선택한다.
                - modifiers는 보이는 것만 쓴다. 보이지 않는 재료는 추정하지 않는다.

                [출력 예시]
                {"main_category":"면 및 만두류","base_food":"칼국수","modifiers":["바지락"]}
                {"main_category":"밥류","base_food":"국밥","modifiers":["순대국밥"]}
                {"main_category":"튀김류","base_food":"닭튀김","modifiers":["후라이드 치킨"]}
                {"main_category":"빵 및 과자류","base_food":"파이/만주","modifiers":["클래식애플파이"]}
                {"main_category":"면 및 만두류","base_food":"스파게티","modifiers":["미트볼"]}
                {"main_category":"음료 및 차류","base_food":"라떼","modifiers":["카페라떼"]}
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

    static LlmFoodAnalysisResponseDto parseResponse(String rawText, ObjectMapper objectMapper) {
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
        String mainCategory = objectMapper.convertValue(root.path("main_category"), String.class);
        String baseFood = objectMapper.convertValue(root.path("base_food"), String.class);
        List<String> modifiers = objectMapper.convertValue(
                root.path("modifiers"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        if (!StringUtils.hasText(baseFood)) {
            return null;
        }

        return LlmFoodAnalysisResponseDto.builder()
                .mainCategory(StringUtils.hasText(mainCategory) ? mainCategory.trim() : null)
                .baseFood(baseFood.trim())
                .modifiers(modifiers == null ? List.of() : modifiers.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList())
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
