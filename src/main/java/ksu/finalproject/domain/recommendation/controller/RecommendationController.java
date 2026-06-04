package ksu.finalproject.domain.recommendation.controller;

import ksu.finalproject.domain.recommendation.dto.DailyMealRecommendationResponseDto;
import ksu.finalproject.domain.recommendation.dto.RecommendationJobResponseDto;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobResult;
import ksu.finalproject.domain.recommendation.service.RecommendationApplicationService;
import ksu.finalproject.domain.recommendation.service.RecommendationQueryService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationQueryService recommendationQueryService;
    private final RecommendationApplicationService recommendationApplicationService;

    @GetMapping("/daily")
    public CommonResponse<DailyMealRecommendationResponseDto> getDailyRecommendation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) throws CustomException {
        return new CommonResponse<>(
                ResponseCode.SUCCESS_GET_DAILY_MEAL_RECOMMENDATION,
                recommendationQueryService.getDailyRecommendation(extractUserId(authentication), date)
        );
    }

    @PostMapping("/daily/generate")
    public CommonResponse<RecommendationJobResponseDto> generateDailyRecommendation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) throws CustomException {
        RecommendationJobResult result = recommendationApplicationService.generateForUser(
                Math.toIntExact(extractUserId(authentication)),
                date
        );
        return new CommonResponse<>(
                ResponseCode.SUCCESS_GENERATE_DAILY_MEAL_RECOMMENDATION,
                RecommendationJobResponseDto.from(result)
        );
    }

    private Long extractUserId(Authentication authentication) throws CustomException {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }
}
