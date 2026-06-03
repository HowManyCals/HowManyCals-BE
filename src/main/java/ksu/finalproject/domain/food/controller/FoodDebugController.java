package ksu.finalproject.domain.food.controller;

import ksu.finalproject.domain.analysis.dto.LlmFoodAnalysisResponseDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugE2EResponseDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugLlmResponseDto;
import ksu.finalproject.domain.food.dto.debug.FoodDebugMatchResponseDto;
import ksu.finalproject.domain.food.service.FoodDebugService;
import ksu.finalproject.global.common.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Profile("local")
@RequestMapping("/debug/food")
@RequiredArgsConstructor
public class FoodDebugController {

    private final FoodDebugService foodDebugService;

    @PostMapping(value = "/llm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FoodDebugLlmResponseDto debugLlm(@RequestPart("image") MultipartFile image) throws CustomException, IOException {
        return foodDebugService.debugLlm(image);
    }

    @PostMapping(value = "/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FoodDebugMatchResponseDto debugMatch(
            @RequestBody LlmFoodAnalysisResponseDto request,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return foodDebugService.debugMatch(request, limit);
    }

    @PostMapping(value = "/e2e", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FoodDebugE2EResponseDto debugE2e(
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "confidenceScore", defaultValue = "0.0") Double confidenceScore
    ) throws CustomException, IOException {
        return foodDebugService.debugE2e(image, confidenceScore);
    }
}
