package ksu.finalproject.domain.analysis.controller;

import ksu.finalproject.domain.analysis.dto.AiAnalysisCallbackDto;
import ksu.finalproject.domain.food.service.FoodService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCallbackController {

    private final FoodService foodService;

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<Void> receiveAnalysisCallback(@RequestBody AiAnalysisCallbackDto result)
            throws CustomException {
        foodService.saveAnalysisResult(result);
        return new CommonResponse<>(ResponseCode.SUCCESS_ANALYZE_FOOD_IMAGE, null);
    }
}

