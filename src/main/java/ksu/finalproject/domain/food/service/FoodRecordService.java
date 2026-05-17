package ksu.finalproject.domain.food.service;

import ksu.finalproject.domain.food.dto.*;
import ksu.finalproject.domain.food.entity.AiAnalysisLog;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.FoodRecord;
import ksu.finalproject.domain.food.entity.enums.MealType;
import ksu.finalproject.domain.food.repository.AiAnalysisLogRepository;
import ksu.finalproject.domain.food.repository.FoodRecordRepository;
import ksu.finalproject.domain.food.repository.FoodRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodRecordService {

    private final FoodRecordRepository foodRecordRepository;
    private final UserRepository userRepository;
    private final FoodRepository foodRepository;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;

    /**
     * 식사 기록을 저장합니다.
     */
    @Transactional
    public FoodRecordResponseDto saveRecord(FoodRecordSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        // Food 조회 (nullable)
        Food food = null;
        if (request.getFoodId() != null) {
            food = foodRepository.findById(request.getFoodId())
                    .orElseThrow(() -> {
                        log.warn("식사 기록 저장 실패 - 음식 없음 foodId={}", request.getFoodId());
                        return new CustomException(ResponseCode.NOT_FOUND);
                    });
        }

        // AiAnalysisLog 조회 (nullable)
        AiAnalysisLog aiAnalysisLog = null;
        if (request.getAiLogId() != null) {
            aiAnalysisLog = aiAnalysisLogRepository.findById(request.getAiLogId())
                    .orElseThrow(() -> {
                        log.warn("식사 기록 저장 실패 - AI 분석 로그 없음 aiLogId={}", request.getAiLogId());
                        return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                    });
        }

        FoodRecord record = FoodRecord.builder()
                .user(user)
                .food(food)
                .aiAnalysisLog(aiAnalysisLog)
                .foodName(request.getFoodName())
                .eatenDate(request.getEatenDate())
                .mealType(request.getMealType())
//                .amountG(request.getAmountG())
                .calories(request.getCalories())
                .carbohydrate(request.getCarbohydrate())
                .protein(request.getProtein())
                .fat(request.getFat())
                .build();

        FoodRecord saved = foodRecordRepository.save(record);
        log.info("식사 기록 저장 완료 userId={}, recordId={}, foodName={}, eatenDate={}, mealType={}",
                userId, saved.getId(), saved.getFoodName(), saved.getEatenDate(), saved.getMealType());

        return FoodRecordResponseDto.from(saved);
    }

    /**
     * 특정 날짜의 식사 기록을 식사 종류별로 조회합니다.
     */
    @Transactional(readOnly = true)
    public DailyFoodRecordResponseDto getDailyRecord(LocalDate date, Long userId) throws CustomException {
        Users user = findUser(userId);

        List<FoodRecord> records = foodRecordRepository
                .findByUserAndEatenDateOrderByMealTypeAsc(user, date);

        // 식사 종류별 그룹핑
        Map<MealType, List<FoodRecordResponseDto>> meals = records.stream()
                .collect(Collectors.groupingBy(
                        FoodRecord::getMealType,
                        Collectors.mapping(FoodRecordResponseDto::from, Collectors.toList())
                ));

        // 하루 총 칼로리
        double totalCalories = records.stream()
                .mapToDouble(FoodRecord::getCalories)
                .sum();

        log.info("일별 식사 기록 조회 userId={}, date={}, recordCount={}, totalCalories={}",
                userId, date, records.size(), totalCalories);

        return DailyFoodRecordResponseDto.builder()
                .date(date)
                .totalCalories(totalCalories)
                .meals(meals)
                .build();
    }

    /**
     * 특정 연월의 날짜별 총 칼로리를 캘린더 형태로 조회합니다.
     */
    @Transactional(readOnly = true)
    public MonthlyCalendarResponseDto getMonthlyCalendar(int year, int month, Long userId) throws CustomException {
        Users user = findUser(userId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<FoodRecord> records = foodRecordRepository
                .findByUserAndEatenDateBetween(user, start, end);

        // 날짜별 칼로리 합산
        Map<LocalDate, Double> dailyCalories = records.stream()
                .collect(Collectors.groupingBy(
                        FoodRecord::getEatenDate,
                        Collectors.summingDouble(FoodRecord::getCalories)
                ));

        log.info("월별 캘린더 조회 userId={}, year={}, month={}, recordCount={}",
                userId, year, month, records.size());

        return MonthlyCalendarResponseDto.builder()
                .year(year)
                .month(month)
                .dailyCalories(dailyCalories)
                .build();
    }

    /**
     * 식사 기록을 삭제합니다. 본인 기록만 삭제 가능합니다.
     */
    @Transactional
    public void deleteRecord(Long recordId, Long userId) throws CustomException {
        FoodRecord record = foodRecordRepository.findById(recordId)
                .orElseThrow(() -> {
                    log.warn("식사 기록 삭제 실패 - 기록 없음 recordId={}", recordId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_RECORD);
                });

        if (!record.getUser().getId().equals(userId)) {
            log.warn("식사 기록 삭제 실패 - 권한 없음 recordId={}, requestUserId={}", recordId, userId);
            throw new CustomException(ResponseCode.FORBIDDEN);
        }

        foodRecordRepository.delete(record);
        log.info("식사 기록 삭제 완료 userId={}, recordId={}", userId, recordId);
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });
    }
}

