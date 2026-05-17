package ksu.finalproject.domain.food.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ksu.finalproject.domain.food.dto.*;
import ksu.finalproject.domain.food.service.FoodRecordService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/food/record")
@RequiredArgsConstructor
public class FoodRecordController {

    private final FoodRecordService foodRecordService;

    private Long extractUserId(Authentication authentication) throws CustomException {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 식사 기록 저장
     * POST /food/record
     */
    @PostMapping
    public CommonResponse<FoodRecordResponseDto> saveRecord(
            @RequestBody @Valid FoodRecordSaveRequestDto request,
            Authentication authentication) throws CustomException {
        FoodRecordResponseDto response = foodRecordService.saveRecord(request, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_SAVE_FOOD_RECORD, response);
    }

    /**
     * 일별 식사 기록 조회
     * GET /food/record/daily?date=2026-05-13
     */
    @GetMapping("/daily")
    public CommonResponse<DailyFoodRecordResponseDto> getDailyRecord(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) throws CustomException {
        DailyFoodRecordResponseDto response = foodRecordService.getDailyRecord(date, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_DAILY_FOOD_RECORD, response);
    }

    /**
     * 월별 캘린더 조회 (날짜별 총 칼로리)
     * GET /food/record/monthly?year=2026&month=5
     */
    @GetMapping("/monthly")
    public CommonResponse<MonthlyCalendarResponseDto> getMonthlyCalendar(
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month,
            Authentication authentication) throws CustomException {
        MonthlyCalendarResponseDto response = foodRecordService.getMonthlyCalendar(year, month, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_GET_MONTHLY_CALENDAR, response);
    }

    /**
     * 식사 기록 삭제
     * DELETE /food/record/{recordId}
     */
    @DeleteMapping("/{recordId}")
    public CommonResponse<Void> deleteRecord(
            @PathVariable Long recordId,
            Authentication authentication) throws CustomException {
        foodRecordService.deleteRecord(recordId, extractUserId(authentication));
        return new CommonResponse<>(ResponseCode.SUCCESS_DELETE_FOOD_RECORD);
    }
}


