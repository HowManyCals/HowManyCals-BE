package ksu.finalproject.domain.foodrecord.service;

import ksu.finalproject.domain.analysis.dto.FoodAnalysisResultDto;
import ksu.finalproject.domain.analysis.entity.AiAnalysisLog;
import ksu.finalproject.domain.analysis.repository.AiAnalysisLogRepository;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.enums.MealType;
import ksu.finalproject.domain.food.repository.FoodRepository;
import ksu.finalproject.domain.foodrecord.dto.DailyFoodRecordResponseDto;
import ksu.finalproject.domain.foodrecord.dto.FoodRecordResponseDto;
import ksu.finalproject.domain.foodrecord.dto.FoodRecordSaveRequestDto;
import ksu.finalproject.domain.foodrecord.dto.MonthlyCalendarResponseDto;
import ksu.finalproject.domain.foodrecord.entity.FoodRecord;
import ksu.finalproject.domain.foodrecord.repository.FoodRecordRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodRecordService {

    private final FoodRecordRepository foodRecordRepository;
    private final UserRepository userRepository;
    private final FoodRepository foodRepository;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public FoodRecordResponseDto saveRecord(FoodRecordSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);
        AiAnalysisLog aiAnalysisLog = findAiAnalysisLog(request.getAiLogId(), userId);
        Food food = resolveFood(request.getFoodId(), aiAnalysisLog);

        FoodRecord record = FoodRecord.builder()
                .user(user)
                .food(food)
                .aiAnalysisLog(aiAnalysisLog)
                .foodName(request.getFoodName())
                .eatenDate(request.getEatenDate())
                .mealType(request.getMealType())
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

    @Transactional(readOnly = true)
    public DailyFoodRecordResponseDto getDailyRecord(LocalDate date, Long userId) throws CustomException {
        Users user = findUser(userId);

        List<FoodRecord> records = foodRecordRepository.findByUserAndEatenDateOrderByMealTypeAsc(user, date);

        Map<MealType, List<FoodRecordResponseDto>> meals = records.stream()
                .collect(Collectors.groupingBy(
                        FoodRecord::getMealType,
                        Collectors.mapping(FoodRecordResponseDto::from, Collectors.toList())
                ));

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

    @Transactional(readOnly = true)
    public MonthlyCalendarResponseDto getMonthlyCalendar(int year, int month, Long userId) throws CustomException {
        Users user = findUser(userId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<FoodRecord> records = foodRecordRepository.findByUserAndEatenDateBetween(user, start, end);

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

        record.deActivate();
        log.info("식사 기록 비활성화 완료 userId={}, recordId={}", userId, recordId);
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("사용자 없음 userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });
    }

    private AiAnalysisLog findAiAnalysisLog(Long aiLogId, Long userId) throws CustomException {
        if (aiLogId == null) {
            return null;
        }

        return aiAnalysisLogRepository.findByIdAndUserId(aiLogId, userId)
                .orElseThrow(() -> {
                    log.warn("식사 기록 저장 실패 - 본인 소유 AI 분석 로그 없음 aiLogId={}, userId={}", aiLogId, userId);
                    return new CustomException(ResponseCode.NOT_FOUND_FOOD_IMAGE_ANALYSIS);
                });
    }

    private Food resolveFood(Long requestFoodId, AiAnalysisLog aiAnalysisLog) throws CustomException {
        if (requestFoodId != null) {
            return foodRepository.findById(requestFoodId)
                    .orElseThrow(() -> {
                        log.warn("식사 기록 저장 실패 - 음식 없음 foodId={}", requestFoodId);
                        return new CustomException(ResponseCode.NOT_FOUND);
                    });
        }

        Long resolvedFoodId = extractFoodIdFromAnalysis(aiAnalysisLog);
        if (resolvedFoodId == null) {
            return null;
        }

        Food resolvedFood = foodRepository.findById(resolvedFoodId).orElse(null);
        if (resolvedFood == null) {
            log.warn("식사 기록 저장 - AI 분석 결과 foodId 자동 매핑 실패 aiLogId={}, foodId={}",
                    aiAnalysisLog.getId(),
                    resolvedFoodId);
            return null;
        }

        log.info("식사 기록 저장 - AI 분석 결과 foodId 자동 매핑 aiLogId={}, foodId={}",
                aiAnalysisLog.getId(),
                resolvedFoodId);
        return resolvedFood;
    }

    private Long extractFoodIdFromAnalysis(AiAnalysisLog aiAnalysisLog) {
        if (aiAnalysisLog == null || !StringUtils.hasText(aiAnalysisLog.getRawOutput())) {
            return null;
        }

        try {
            FoodAnalysisResultDto analysisResult = objectMapper.readValue(aiAnalysisLog.getRawOutput(), FoodAnalysisResultDto.class);
            if (analysisResult.getCandidates() == null || analysisResult.getCandidates().isEmpty()) {
                return null;
            }

            return analysisResult.getCandidates().stream()
                    .map(candidate -> candidate != null ? candidate.getFoodId() : null)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("식사 기록 저장 - 분석 결과에서 foodId 추출 실패 aiLogId={}", aiAnalysisLog.getId(), e);
            return null;
        }
    }
}
