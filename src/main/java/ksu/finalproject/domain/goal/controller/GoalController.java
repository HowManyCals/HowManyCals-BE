package ksu.finalproject.domain.goal.controller;

import jakarta.validation.Valid;
import ksu.finalproject.domain.goal.dto.CaloriesGoalSaveRequestDto;
import ksu.finalproject.domain.goal.dto.WeightGoalSaveRequestDto;
import ksu.finalproject.domain.goal.service.GoalService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    private Long extractUserId(Authentication authentication) throws CustomException{
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 목표 체중 설정
     * POST /goal/weight
     */
    @PostMapping("/weight")
    private CommonResponse<Void> saveGoalWeight(@RequestBody @Valid WeightGoalSaveRequestDto request,
                                                Authentication authentication) throws CustomException{
        goalService.saveGoalWeight(request, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_SAVE_WEIGHT_GOAL);
    }

    /**
     * 목표 칼로리 설정
     * POST /goal/calories
     */
    @PostMapping("/calories")
    private CommonResponse<Void> saveGoalCalories(@RequestBody @Valid CaloriesGoalSaveRequestDto request,
                                                  Authentication authentication) throws CustomException{
        goalService.saveGoalCalories(request, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_SAVE_CALORIE_GOAL);
    }
}
