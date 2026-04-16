package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodAnalyzeResponseDto;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.AiServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.Map;

/**
 * AI 서버와의 HTTP 통신을 담당합니다.
 * 이미지 분석 요청 전송 / 응답 파싱 책임만 가집니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiServerRequestService {

    private final RestTemplate restTemplate;
    private final AiServerProperties aiServerProperties;

    /**
     * AI 서버에 이미지 분석을 비동기 요청합니다.
     *
     * @param imageFile   분석 대상 이미지 임시 파일
     * @param contentType 이미지 MIME 타입
     * @param aiLogId     AI 분석 로그 ID (우리 DB PK)
     * @return 접수 응답 (status: "processing", ai_log_id)
     */
    public FoodAnalyzeResponseDto requestAnalysis(Path imageFile, String contentType, Long aiLogId) throws CustomException {
        if (!StringUtils.hasText(aiServerProperties.getAnalyzeUrl())) {
            log.warn("AI 서버 요청 실패 - analyzeUrl 미설정 aiLogId={}", aiLogId);
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }

        log.info("AI 서버 분석 요청 시작 aiLogId={}, analyzeUrl={}, contentType={}", aiLogId, aiServerProperties.getAnalyzeUrl(), contentType);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    aiServerProperties.getAnalyzeUrl(),
                    HttpMethod.POST,
                    buildRequest(imageFile, contentType, aiLogId),
                    new ParameterizedTypeReference<>() {}
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("AI 서버 응답 상태 비정상 aiLogId={}, statusCode={}", aiLogId, response.getStatusCode());
                throw new CustomException(ResponseCode.AI_SERVER_RESPONSE_INVALID);
            }

            log.info("AI 서버 분석 요청 성공 aiLogId={}, statusCode={}", aiLogId, response.getStatusCode());
            return toResponseDto(response.getBody(), aiLogId);
        } catch (RestClientException e) {
            log.error("AI 서버 분석 요청 예외 aiLogId={}, analyzeUrl={}", aiLogId, aiServerProperties.getAnalyzeUrl(), e);
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> buildRequest(Path imageFile, String contentType, Long aiLogId) {
        HttpHeaders fileHeaders = new HttpHeaders();
        if (StringUtils.hasText(contentType)) {
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("ai_log_id", String.valueOf(aiLogId));
        body.add("image", new HttpEntity<>(new FileSystemResource(imageFile.toFile()), fileHeaders));
        if (StringUtils.hasText(aiServerProperties.getCallbackUrl())) {
            body.add("callback_url", aiServerProperties.getCallbackUrl());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.hasText(aiServerProperties.getApiKey())) {
            headers.add("X-API-Key", aiServerProperties.getApiKey());
        }

        return new HttpEntity<>(body, headers);
    }

    private FoodAnalyzeResponseDto toResponseDto(Map<String, Object> body, Long aiLogId) {
        String status = (body != null && body.get("status") instanceof String s && StringUtils.hasText(s))
                ? s : "processing";
        return FoodAnalyzeResponseDto.builder()
                .status(status)
                .aiLogId(aiLogId)
                .build();
    }
}

