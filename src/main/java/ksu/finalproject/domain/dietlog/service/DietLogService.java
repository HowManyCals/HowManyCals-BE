package ksu.finalproject.domain.dietlog.service;

import ksu.finalproject.domain.dietlog.dto.DietLogResponseDto;
import ksu.finalproject.domain.dietlog.repository.DietLogRepository;
import ksu.finalproject.domain.foodrecord.entity.FoodRecord;
import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.goal.repository.CaloriesGoalRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.enums.GoalType;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DietLogService {

    private final UserRepository userRepository;
    private final DietLogRepository dietLogRepository;
    private final CaloriesGoalRepository caloriesGoalRepository;

    @Transactional(readOnly = true)
    public DietLogResponseDto getStats(Long userId) throws CustomException{
        Users user = findUser(userId);

        List<FoodRecord> records = dietLogRepository.findByUserOrderByEatenDateAscCreatedAtAsc(user);
        Map<LocalDate, Double> dailyCalories = aggregateDailyCalories(records); // 날짜 별 칼로리 집계
        long recordDays = dailyCalories.size();

        if (dailyCalories.isEmpty()) {
            log.info("식단 통계 조회 userId={}, recordDays=0", userId);
            return DietLogResponseDto.builder()
                    .recordDays(0)
                    .goalAchievedDays(0)
                    .currentStreakDays(0)
                    .maxStreakDays(0)
                    .build();
        }

        List<CaloriesGoal> goalHistory = caloriesGoalRepository.findAllByUserOrderByCreatedAtAsc(user);
        if (goalHistory.isEmpty()) {
            log.info("식단 통계 조회 userId={}, recordDays={}, goalHistory=0", userId, recordDays);
            return DietLogResponseDto.builder()
                    .recordDays(recordDays)
                    .goalAchievedDays(0)
                    .currentStreakDays(0)
                    .maxStreakDays(0)
                    .build();
        }

        StatsSummary summary = calculateStats(dailyCalories, goalHistory, user.getGoalType());

        DietLogResponseDto response = DietLogResponseDto.builder()
                .recordDays(recordDays)
                .goalAchievedDays(summary.goalAchievedDays())
                .currentStreakDays(summary.currentStreakDays())
                .maxStreakDays(summary.maxStreakDays())
                .build();

        log.info("식단 통계 조회 userId={}, recordDays={}, goalAchievedDays={}, currentStreakDays={}, maxStreakDays={}",
                userId, response.getRecordDays(), response.getGoalAchievedDays(),
                response.getCurrentStreakDays(), response.getMaxStreakDays());
        return response;
    }

    private Map<LocalDate, Double> aggregateDailyCalories(List<FoodRecord> records) {
        Map<LocalDate, Double> dailyCalories = new LinkedHashMap<>();
        for (FoodRecord record : records) {
            if (record.getEatenDate() == null) {
                continue;
            }
            dailyCalories.merge(record.getEatenDate(), nullableCalories(record.getCalories()), Double::sum);
        }
        return dailyCalories;
    }

    private StatsSummary calculateStats(Map<LocalDate, Double> dailyCalories, List<CaloriesGoal> goalHistory, GoalType goalType) {
        int goalIndex = 0;
        int currentStreak = 0;
        int maxStreak = 0;
        long goalAchievedDays = 0;
        LocalDate previousDate = null;

        for (Map.Entry<LocalDate, Double> entry : dailyCalories.entrySet()) {
            LocalDate eatenDate = entry.getKey();
            LocalDateTime dayEnd = eatenDate.atTime(23, 59, 59, 999_999_999);

            while (goalIndex + 1 < goalHistory.size()
                    && !goalHistory.get(goalIndex + 1).getCreatedAt().isAfter(dayEnd)) {
                goalIndex++;
            }

            CaloriesGoal applicableGoal = goalHistory.get(goalIndex);
            boolean goalExistsForDay = !applicableGoal.getCreatedAt().isAfter(dayEnd);
            boolean achieved = goalExistsForDay && isSuccessful(goalType, entry.getValue(), applicableGoal.getGoalCalories());

            if (achieved) {
                goalAchievedDays++;
                if (previousDate != null && previousDate.plusDays(1).equals(eatenDate)) {
                    currentStreak++;
                } else {
                    currentStreak = 1;
                }
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }

            previousDate = eatenDate;
        }

        return new StatsSummary(goalAchievedDays, currentStreak, maxStreak);
    }

    private boolean isSuccessful(GoalType goalType, double actualCalories, Double goalCalories) {
        if (goalType == null || goalCalories == null || goalCalories <= 0) {
            return false;
        }

        double percent = (actualCalories / goalCalories) * 100.0;
        return switch (goalType) {
            case DIET -> percent >= 85.0 && percent <= 105.0;
            case MAINTAIN -> percent >= 90.0 && percent <= 110.0;
            case BULK -> percent >= 95.0 && percent <= 115.0;
            case HABIT -> percent >= 80.0 && percent <= 120.0;
        };
    }

    private double nullableCalories(Double calories) {
        return calories == null ? 0.0 : calories;
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId).orElseThrow(() -> {
            log.warn("사용자 정보를 찾을 수 없습니다. userId={}", userId);
            return new CustomException(ResponseCode.NOT_FOUND_USER);
        });
    }

    private record StatsSummary(long goalAchievedDays, long currentStreakDays, long maxStreakDays) {
    }
}
