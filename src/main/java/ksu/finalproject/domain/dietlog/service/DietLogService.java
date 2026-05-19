package ksu.finalproject.domain.dietlog.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import ksu.finalproject.domain.dietlog.dto.DietLogResponseDto;
import ksu.finalproject.domain.dietlog.repository.DietLogRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DietLogService {

    private final UserRepository userRepository;
    private final DietLogRepository dietLogRepository;

    public DietLogResponseDto getStats(Long userId) throws CustomException{
        Users user = findUser(userId);
        // 기록 일수
        long recordDays = dietLogRepository.countDistinctEatenDateByUser(user);
        // 목표 달성 일수 => 일단 보류

        // 현재 streak

        DietLogResponseDto response = DietLogResponseDto.builder()
                .recordDays(recordDays)
                .goalAchievedDays(-1)
                .streakDays(-1)
                .build();
        return response;
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId).orElseThrow(() -> {
            log.warn("사용자 정보를 찾을 수 없습니다. userId={}", userId);
            return new CustomException(ResponseCode.NOT_FOUND_USER);
        });
    }
}
