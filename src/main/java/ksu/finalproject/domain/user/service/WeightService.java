package ksu.finalproject.domain.user.service;

import ksu.finalproject.domain.user.dto.WeightGoalSaveRequestDto;
import ksu.finalproject.domain.user.dto.WeightRecordSaveRequestDto;
import ksu.finalproject.domain.user.dto.WeightRecordsResponseDto;
import ksu.finalproject.domain.user.dto.WeightSummaryResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.WeightGoal;
import ksu.finalproject.domain.user.entity.WeightRecord;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.domain.user.repository.WeightGoalRepository;
import ksu.finalproject.domain.user.repository.WeightRecordRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightService {

    private final WeightRecordRepository weightRecordRepository;
    private final WeightGoalRepository weightGoalRepository;
    private final UserRepository userRepository;

    /**
     * 오늘 체중 기록 저장
     * 같은 날짜에 이미 기록이 있으면 예외 발생
     */
    @Transactional
    public void saveRecord(WeightRecordSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        if (weightRecordRepository.findByUserAndRecordedDate(user, request.getRecordedDate()).isPresent()) {
            log.warn("체중 기록 중복 userId={}, date={}", userId, request.getRecordedDate());
            throw new CustomException(ResponseCode.ALREADY_EXIST_WEIGHT_RECORD);
        }

        WeightRecord record = WeightRecord.builder()
                .user(user)
                .weight(request.getWeight())
                .recordedDate(request.getRecordedDate())
                .build();

        weightRecordRepository.save(record);
        log.info("체중 기록 완료 userId={}, weight={}, date={}", userId, request.getWeight(), request.getRecordedDate());
    }

    /**
     * 목표 체중 설정
     * 기존 목표와 무관하게 새 행 INSERT → 이력 보존
     */
    @Transactional
    public void saveGoal(WeightGoalSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        WeightGoal goal = WeightGoal.builder()
                .user(user)
                .targetWeight(request.getTargetWeight())
                .build();

        weightGoalRepository.save(goal);
        log.info("목표 체중 설정 완료 userId={}, targetWeight={}", userId, request.getTargetWeight());
    }

    /**
     * 체중 메인 화면 - 현재 체중 + 목표 체중
     */
    @Transactional(readOnly = true)
    public WeightSummaryResponseDto getSummary(Long userId) throws CustomException {
        Users user = findUser(userId);

        Double currentWeight = weightRecordRepository
                .findTopByUserOrderByRecordedDateDesc(user)
                .map(WeightRecord::getWeight)
                .orElse(null);

        Double targetWeight = weightGoalRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .map(WeightGoal::getTargetWeight)
                .orElse(null);

        log.info("체중 요약 조회 userId={}, currentWeight={}, targetWeight={}", userId, currentWeight, targetWeight);

        return WeightSummaryResponseDto.builder()
                .currentWeight(currentWeight)
                .targetWeight(targetWeight)
                .build();
    }

    /**
     * 일간 그래프 - 최근 7일 체중 이력
     */
    @Transactional(readOnly = true)
    public WeightRecordsResponseDto getDailyRecords(Long userId) throws CustomException {
        Users user = findUser(userId);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        List<WeightRecordsResponseDto.WeightRecordDto> records = weightRecordRepository
                .findByUserAndRecordedDateBetweenOrderByRecordedDateAsc(user, start, end)
                .stream()
                .map(r -> WeightRecordsResponseDto.WeightRecordDto.builder()
                        .date(r.getRecordedDate())
                        .weight(r.getWeight())
                        .build())
                .toList();

        log.info("일간 체중 조회 userId={}, recordCount={}", userId, records.size());
        return WeightRecordsResponseDto.builder().records(records).build();
    }

    /**
     * 월간 그래프 - 해당 월 전체 체중 이력
     */
    @Transactional(readOnly = true)
    public WeightRecordsResponseDto getMonthlyRecords(int year, int month, Long userId) throws CustomException {
        Users user = findUser(userId);

        LocalDate start = YearMonth.of(year, month).atDay(1);
        LocalDate end = YearMonth.of(year, month).atEndOfMonth();

        List<WeightRecordsResponseDto.WeightRecordDto> records = weightRecordRepository
                .findByUserAndRecordedDateBetweenOrderByRecordedDateAsc(user, start, end)
                .stream()
                .map(r -> WeightRecordsResponseDto.WeightRecordDto.builder()
                        .date(r.getRecordedDate())
                        .weight(r.getWeight())
                        .build())
                .toList();

        log.info("월간 체중 조회 userId={}, year={}, month={}, recordCount={}", userId, year, month, records.size());
        return WeightRecordsResponseDto.builder().records(records).build();
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("사용자가 존재하지 않습니다. userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });
    }
}
