package ksu.finalproject.domain.analysis.service;

import ksu.finalproject.domain.analysis.dto.FoodAnalyzeResponseDto;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.AiServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
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
     * @param image   FE에서 전달받은 MultipartFile
     * @param aiLogId AI 분석 로그 ID (우리 DB PK)
     * @return 접수 응답 (status: "processing", ai_log_id)
     */
    public FoodAnalyzeResponseDto requestAnalysis(MultipartFile image, Long aiLogId) throws CustomException {
        if (!StringUtils.hasText(aiServerProperties.getAnalyzeUrl())) {
            log.warn("AI 서버 요청 실패 - analyzeUrl 미설정 aiLogId={}", aiLogId);
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }

        log.info("AI 서버 분석 요청 시작 aiLogId={}, analyzeUrl={}, contentType={}", aiLogId, aiServerProperties.getAnalyzeUrl(), image.getContentType());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    aiServerProperties.getAnalyzeUrl(),
                    HttpMethod.POST,
                    buildRequest(image, aiLogId),
                    new ParameterizedTypeReference<>() {
                    }
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

    /**
     * AI 서버로 전송할 multipart/form-data 요청을 구성합니다.
     * <p>
     * MultipartFile의 바이트 배열을 {@link ByteArrayResource}로 래핑하여
     * Content-Disposition에 filename을 포함시켜 AI 서버가 파일로 인식하도록 합니다.
     * </p>
     *
     * @param image   FE에서 전달받은 MultipartFile (이미지 원본)
     * @param aiLogId AI 분석 로그 ID — AI 서버가 콜백 시 식별자로 사용
     * @return AI 서버로 전송할 {@link HttpEntity} (multipart/form-data 형식)
     * @throws CustomException 이미지 바이트 배열 변환 실패 시
     */
    private HttpEntity<MultiValueMap<String, Object>> buildRequest(MultipartFile image, Long aiLogId) throws CustomException {
        try{
        // multipart 표준 스펙에 따라, 파일 값에 파일명(filename)을 지정해줘야 실제 파일로 인식함.
        ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
            @Override // Content-Disposition에 filename 포함하기 위한 재정의
            public String getFilename() {
                return StringUtils.hasText(image.getOriginalFilename())
                        ? image.getOriginalFilename()
                        : "image.jpg"; // fallback
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        if (StringUtils.hasText(image.getContentType())) {
            fileHeaders.setContentType(MediaType.parseMediaType(image.getContentType()));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>(); // 하나의 Key에 여러 value 저장 가능
        // 분석 요청 DTO 형식
        // Header : X-API-KEY
        // Body
        // - ai_log_id
        // - image(multipartFile)
        // - callback_url
        body.add("ai_log_id", String.valueOf(aiLogId));
        body.add("image", new HttpEntity<>(imageResource, fileHeaders));
        if (StringUtils.hasText(aiServerProperties.getCallbackUrl())) {
            body.add("callback_url", aiServerProperties.getCallbackUrl());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.hasText(aiServerProperties.getApiKey()))
            headers.add("X-API-Key", aiServerProperties.getApiKey());

        return new HttpEntity<>(body, headers);
        } catch (IOException e) {
            log.error("이미지 바이트 배열 변환 실패 aiLogId={}", aiLogId, e);
            throw new CustomException(ResponseCode.AI_SERVER_REQUEST_FAILED);
        }
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
