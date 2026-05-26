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
import java.util.ArrayList;
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
            log.warn("Gemini API 키가 비어 있어요. aiLogId={}", request.getAiLogId());
            return null;
        }

        AnalysisImageStore.CachedImage cachedImage = analysisImageStore.get(request.getAiLogId());
        if (cachedImage == null || cachedImage.data() == null || cachedImage.data().length == 0) {
            log.warn("LLM fallback 실패 - 캐시된 이미지가 없어요. aiLogId={}", request.getAiLogId());
            return null;
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequest(cachedImage);
            String requestUrl = googleGeminiProperties.getUrlTemplate().formatted(
                    googleGeminiProperties.getModel(),
                    googleGeminiProperties.getApiKey()
            ); // %s 순서대로 매핑
            Object responseObject = restTemplate.postForObject(requestUrl, requestBody, Map.class);
            if (!(responseObject instanceof Map<?, ?> rawResponse)) {
                log.warn("LLM fallback 실패 - Gemini 응답 형식이 올바르지 않아요. aiLogId={}", request.getAiLogId());
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rawResponse;

            String rawText = extractResponseText(response);
            if (!StringUtils.hasText(rawText)) {
                log.warn("LLM fallback 실패 - Gemini 응답 본문이 비어 있어요. aiLogId={}", request.getAiLogId());
                return null;
            }

            LlmFoodAnalysisResponseDto parsed = parseResponse(rawText);
            if (parsed == null || parsed.getCandidates() == null || parsed.getCandidates().isEmpty()) {
                log.warn("LLM fallback 실패 - Gemini 응답 파싱 결과가 비어 있어요. aiLogId={}, rawText={}", request.getAiLogId(), rawText);
                return null;
            }

            log.info("LLM fallback 성공 aiLogId={}, originalFoodName={}, llmCandidates={}",
                    request.getAiLogId(),
                    request.getOriginalFoodName(),
                    parsed.getCandidates());
            return parsed;
        } catch (RestClientException e) {
            log.error("LLM fallback 실패 - Gemini 호출 예외 aiLogId={}", request.getAiLogId(), e);
            return null;
        } catch (Exception e) {
            log.error("LLM fallback 실패 - Gemini 응답 처리 예외 aiLogId={}", request.getAiLogId(), e);
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
        generationConfig.put("maxOutputTokens", 128);
        generationConfig.put("responseMimeType", "application/json");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private String buildPrompt() {
        return """
                음식 이미지다.
                가장 가능성 높은 음식 3개를 한국어로 반환해라.
                JSON만 출력.

                {
                  "candidates": ["", "", ""]
                }
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

    private LlmFoodAnalysisResponseDto parseResponse(String rawText) {
        String sanitized = rawText.trim();
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replace("```json", "").replace("```", "").trim();
        }

        JsonNode root = objectMapper.readTree(sanitized.getBytes(StandardCharsets.UTF_8));
        JsonNode candidatesNode = root.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        for (JsonNode candidateNode : candidatesNode) {
            String candidate = objectMapper.convertValue(candidateNode, String.class);
            if (StringUtils.hasText(candidate)) {
                candidates.add(candidate.trim());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return LlmFoodAnalysisResponseDto.builder()
                .candidates(candidates)
                .build();
    }
}
