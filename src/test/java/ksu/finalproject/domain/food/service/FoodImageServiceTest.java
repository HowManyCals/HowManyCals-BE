package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.FoodImageAnalyzeResponseDto;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import ksu.finalproject.global.config.AiServerProperties;
import ksu.finalproject.global.config.FoodImageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FoodImageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void analyzeFoodImageStoresTemporarilyCallsAiServerAndDeletesFile() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        AiServerProperties aiServerProperties = new AiServerProperties();
        aiServerProperties.setAnalyzeUrl("http://localhost:8000/analyze");

        FoodImageProperties foodImageProperties = new FoodImageProperties();
        foodImageProperties.setTempDir(tempDir.toString());
        foodImageProperties.setMaxFileSize(5_242_880L);

        FoodImageService foodImageService = new FoodImageService(restTemplate, aiServerProperties, foodImageProperties);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "food.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-data".getBytes()
        );

        mockServer.expect(requestTo("http://localhost:8000/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"label\":\"bibimbap\",\"confidence\":0.97}", MediaType.APPLICATION_JSON));

        FoodImageAnalyzeResponseDto response = foodImageService.analyzeFoodImage(image);

        assertEquals("food.png", response.getOriginalFileName());
        assertEquals(MediaType.IMAGE_PNG_VALUE, response.getContentType());
        assertEquals("bibimbap", response.getAnalysisResult().get("label"));
        assertTrue(Files.list(tempDir).findAny().isEmpty());
        mockServer.verify();
    }

    @Test
    void analyzeFoodImageRejectsUnsupportedContentType() {
        RestTemplate restTemplate = new RestTemplate();

        AiServerProperties aiServerProperties = new AiServerProperties();
        aiServerProperties.setAnalyzeUrl("http://localhost:8000/analyze");

        FoodImageProperties foodImageProperties = new FoodImageProperties();
        foodImageProperties.setTempDir(tempDir.toString());
        foodImageProperties.setMaxFileSize(5_242_880L);

        FoodImageService foodImageService = new FoodImageService(restTemplate, aiServerProperties, foodImageProperties);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "food.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not-image".getBytes()
        );

        CustomException exception = assertThrows(CustomException.class, () -> foodImageService.analyzeFoodImage(image));
        assertEquals(ResponseCode.UNSUPPORTED_FOOD_IMAGE_TYPE, exception.getStatus());
    }
}

