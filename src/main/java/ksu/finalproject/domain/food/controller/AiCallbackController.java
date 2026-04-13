package ksu.finalproject.domain.food.controller;

import ksu.finalproject.domain.food.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.food.service.FoodService;
import ksu.finalproject.global.common.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Void> receiveAnalysisCallback(@RequestBody FoodAnalysisResultDto result)
            throws CustomException {
        foodService.saveAnalysisResult(result);
        return ResponseEntity.ok().build();
    }
}

