package ksu.finalproject.domain.food.controller;

import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.dto.FoodAnalyzeResponseDto;
import ksu.finalproject.domain.food.dto.ManualFoodSearchResponseDto;
import ksu.finalproject.domain.food.service.FoodService;
import ksu.finalproject.domain.food.service.ManualFoodSearchService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;
    private final ManualFoodSearchService manualFoodSearchService;

    private Long extractUserId(Authentication authentication) throws CustomException {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<FoodAnalyzeResponseDto>> analyzeFoodImage(
            @RequestPart("image") MultipartFile image,
            Authentication authentication
    ) throws CustomException {
        FoodAnalyzeResponseDto response = foodService.analyzeFoodImage(image, extractUserId(authentication));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new CommonResponse<>(ResponseCode.SUCCESS_REQUEST_ANALYZE_FOOD_IMAGE, response));
    }

    @GetMapping("/analyze/{aiLogId}")
    public CommonResponse<FoodAnalysisResultDto> getAnalysisResult(
            @PathVariable Long aiLogId,
            Authentication authentication
    ) throws CustomException {
        FoodAnalysisResultDto response = foodService.getAnalysisResult(aiLogId, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_FOOD_IMAGE_ANALYSIS, response);
    }

    @GetMapping("/images/{imageKey}")
    public ResponseEntity<byte[]> getFoodImage(
            @PathVariable String imageKey,
            Authentication authentication
    ) throws CustomException {
        FoodService.StoredFoodImage image = foodService.getFoodImage(imageKey, extractUserId(authentication));
        MediaType contentType = MediaType.parseMediaType(image.contentType());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(image.data());
    }

    @GetMapping("/search")
    public CommonResponse<ManualFoodSearchResponseDto> searchFoods(
            @RequestParam("query") String query,
            Authentication authentication
    ) throws CustomException {
        extractUserId(authentication);
        ManualFoodSearchResponseDto response = manualFoodSearchService.search(query);
        return new CommonResponse<>(ResponseCode.SUCCESS_SEARCH_FOOD_FOR_MANUAL_ENTRY, response);
    }

    @GetMapping(value = "/analyze/{aiLogId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeAnalysisResult(
            @PathVariable Long aiLogId,
            Authentication authentication
    ) throws CustomException {
        return foodService.subscribeToResult(aiLogId, extractUserId(authentication));
    }
}
