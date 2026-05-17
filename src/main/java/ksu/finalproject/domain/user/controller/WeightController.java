package ksu.finalproject.domain.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ksu.finalproject.domain.user.dto.WeightGoalSaveRequestDto;
import ksu.finalproject.domain.user.dto.WeightRecordSaveRequestDto;
import ksu.finalproject.domain.user.dto.WeightRecordsResponseDto;
import ksu.finalproject.domain.user.dto.WeightSummaryResponseDto;
import ksu.finalproject.domain.user.service.WeightService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/weight")
@RequiredArgsConstructor
public class WeightController {

    private final WeightService weightService;

    private Long extractUserId(Authentication authentication) throws CustomException {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 체중 기록 저장
     * POST /weight/record
     */
    @PostMapping("/record")
    public CommonResponse<Void> saveRecord(
            @RequestBody @Valid WeightRecordSaveRequestDto request,
            Authentication authentication) throws CustomException {
        weightService.saveRecord(request, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_SAVE_WEIGHT_RECORD);
    }

    /**
     * 목표 체중 설정
     * POST /weight/goal
     */
    @PostMapping("/goal")
    public CommonResponse<Void> saveGoal(
            @RequestBody @Valid WeightGoalSaveRequestDto request,
            Authentication authentication) throws CustomException {
        weightService.saveGoal(request, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_SAVE_WEIGHT_GOAL);
    }

    /**
     * 체중 메인 화면 (현재 체중 + 목표 체중)
     * GET /weight/summary
     */
    @GetMapping("/summary")
    public CommonResponse<WeightSummaryResponseDto> getSummary(
            Authentication authentication) throws CustomException {
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_WEIGHT_SUMMARY,
                weightService.getSummary(extractUserId(authentication)));
    }

    /**
     * 일간 그래프 (최근 7일)
     * GET /weight/records/daily
     */
    @GetMapping("/records/daily")
    public CommonResponse<WeightRecordsResponseDto> getDailyRecords(
            Authentication authentication) throws CustomException {
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_WEIGHT_RECORDS,
                weightService.getDailyRecords(extractUserId(authentication)));
    }

    /**
     * 월간 그래프
     * GET /weight/records/monthly?year=2026&month=5
     */
    @GetMapping("/records/monthly")
    public CommonResponse<WeightRecordsResponseDto> getMonthlyRecords(
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month,
            Authentication authentication) throws CustomException {
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_WEIGHT_RECORDS,
                weightService.getMonthlyRecords(year, month, extractUserId(authentication)));
    }
}

