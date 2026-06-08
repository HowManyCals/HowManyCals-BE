package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.cache.AnalysisImageStore;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisRequestDto;
import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
import ksu.finalproject.domain.analysis.entity.AiAnalysisLog;
import ksu.finalproject.domain.analysis.repository.AiAnalysisLogRepository;
import ksu.finalproject.global.config.GoogleGeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
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
    private static final String FOOD_ANALYSIS_PROMPT_PATH = "prompts/food-analysis.prompt";

    private final RestTemplate restTemplate;
    private final AnalysisImageStore analysisImageStore;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;
    private final ObjectMapper objectMapper;
    private final GoogleGeminiProperties googleGeminiProperties;
    private volatile String cachedPrompt;

    public LlmFoodAnalysisResponseDto analyzeFromLlm(LlmFoodAnalysisRequestDto request) {
        if (request == null || request.getAiLogId() == null) {
            return null;
        }
        if (!StringUtils.hasText(googleGeminiProperties.getApiKey())) {
            log.warn("Gemini API 키가 비어 있어 LLM fallback 을 건너뜁니다. aiLogId={}", request.getAiLogId());
            return null;
        }

        AiAnalysisLog analysisLog = aiAnalysisLogRepository.findById(request.getAiLogId()).orElse(null);
        if (analysisLog == null || !StringUtils.hasText(analysisLog.getImageKey())) {
            log.warn("LLM fallback 실패 - imageKey가 없습니다. aiLogId={}", request.getAiLogId());
            return null;
        }

        AnalysisImageStore.CachedImage cachedImage = analysisImageStore.load(analysisLog.getImageKey());
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
        Map<String, Object> textPart = Map.of("text", "이미지에 실제 음식 또는 식품 패키지가 보일 때만 규칙에 맞는 JSON 데이터를 추출해줘.");
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
        generationConfig.put("temperature", 0.2);
        generationConfig.put("topP", 0.8);
        generationConfig.put("maxOutputTokens", 768); // 768~1024
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

        // 3. 요청 바디 구성 (System Instruction을 별도 필드로 분리)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);

        // 프롬프트 규칙들을 systemInstruction 필드로 바인딩
        requestBody.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", loadPrompt()))
        ));

        return requestBody;
    }

    private String loadPrompt() {
        if (StringUtils.hasText(cachedPrompt)) {
            return cachedPrompt;
        }

        synchronized (this) {
            if (StringUtils.hasText(cachedPrompt)) {
                return cachedPrompt;
            }

            try {
                ClassPathResource resource = new ClassPathResource(FOOD_ANALYSIS_PROMPT_PATH);
                byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
                cachedPrompt = new String(bytes, StandardCharsets.UTF_8);
                return cachedPrompt;
            } catch (IOException e) {
                throw new IllegalStateException("LLM 프롬프트 파일을 읽을 수 없습니다: " + FOOD_ANALYSIS_PROMPT_PATH, e);
            }
        }
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
