package ksu.finalproject.domain.weight.service;

import ksu.finalproject.domain.weight.dto.WeightRecordSaveRequestDto;
import ksu.finalproject.domain.weight.dto.WeightRecordsResponseDto;
import ksu.finalproject.domain.weight.dto.WeightSummaryResponseDto;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.goal.entity.WeightGoal;
import ksu.finalproject.domain.weight.dto.WeightYearlyRecordsResponseDto;
import ksu.finalproject.domain.weight.entity.WeightRecord;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.domain.goal.repository.WeightGoalRepository;
import ksu.finalproject.domain.weight.repository.WeightRecordRepository;
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
public class WeightService {

    private final WeightRecordRepository weightRecordRepository;
    private final WeightGoalRepository weightGoalRepository;
    private final UserRepository userRepository;

    /**
     * 오늘 체중 기록 저장
     * 같은 날짜에 여러 번 기록 가능 — 새 행 INSERT로 이력 보존
     */
    @Transactional
    public void saveRecord(WeightRecordSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        WeightRecord record = WeightRecord.builder()
                .user(user)
                .weight(request.getWeight())
                .recordedDate(request.getRecordedDate())
                .build();

        weightRecordRepository.save(record);
        log.info("체중 기록 완료 userId={}, weight={}, date={}", userId, request.getWeight(), request.getRecordedDate());
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

        Double goalWeight = weightGoalRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .map(WeightGoal::getGoalWeights)
                .orElse(null);

        log.info("체중 요약 조회 userId={}, currentWeight={}, goalWeight={}", userId, currentWeight, goalWeight);

        return WeightSummaryResponseDto.builder()
                .currentWeight(currentWeight)
                .targetWeight(goalWeight)
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

    /**
     * 연간 그래프 - 해당년도 전체 체중 이력
     */
    @Transactional(readOnly = true)
    public WeightYearlyRecordsResponseDto getYearlyRecords(int year, Long userId) throws CustomException {
        Users user = findUser(userId);

        LocalDate start = LocalDate.of(year, 1, 1); // 1월 1일부터
        LocalDate end = LocalDate.of(year, 12, 31); // 12월 31일까지


        Map<Integer, List<WeightYearlyRecordsResponseDto.WeightMonthlyRecordDto>> records = weightRecordRepository
                .findByUserAndRecordedDateBetweenOrderByRecordedDateAsc(user, start, end)
                .stream()
                .map(r -> WeightYearlyRecordsResponseDto.WeightMonthlyRecordDto.builder()
                        .date(r.getRecordedDate())
                        .weight(r.getWeight())
                        .build())
                .collect(Collectors.groupingBy(dto -> dto.getDate().getMonthValue())); // 월(1~12)별로 그룹핑

        log.info("연간 체중 조회 userId={}, year={}, recordCount={}", userId, year, records.size());
        return WeightYearlyRecordsResponseDto.builder().records(records).build();
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("사용자가 존재하지 않습니다. userId={}", userId);
                    return new CustomException(ResponseCode.NOT_FOUND_USER);
                });
    }
}
