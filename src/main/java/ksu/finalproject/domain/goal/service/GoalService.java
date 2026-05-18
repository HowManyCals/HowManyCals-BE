package ksu.finalproject.domain.goal.service;

import ksu.finalproject.domain.goal.dto.CaloriesGoalSaveRequestDto;
import ksu.finalproject.domain.goal.dto.WeightGoalSaveRequestDto;
import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.goal.entity.WeightGoal;
import ksu.finalproject.domain.goal.repository.CaloriesGoalRepository;
import ksu.finalproject.domain.goal.repository.WeightGoalRepository;
import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.repository.UserRepository;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final UserRepository userRepository;
    private final WeightGoalRepository weightGoalRepository;
    private final CaloriesGoalRepository caloriesGoalRepository;

    /**
     * 목표 체중 설정
     */
    @Transactional
    public void saveGoalWeight(WeightGoalSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        // 목표 체중 entity화
        WeightGoal goal = WeightGoal.builder()
                .user(user)
                .goalWeights(request.getGoalWeight())
                .build();

        weightGoalRepository.save(goal); // DB 저장
        log.info("목표 체중 설정 완료 userId={}, goalWeight={}", userId, request.getGoalWeight());
    }

    /**
     * 목표 칼로리 설정
     */
    @Transactional
    public void saveGoalCalories(CaloriesGoalSaveRequestDto request, Long userId) throws CustomException {
        Users user = findUser(userId);

        // 목표 칼로리 entity화
        CaloriesGoal goal = CaloriesGoal.builder()
                .user(user)
                .goalCalories(request.getGoalCalories())
                .build();

        caloriesGoalRepository.save(goal); // DB 저장
        log.info("목표 칼로리 설정 완료 userId={}, goalCalories={}", userId, request.getGoalCalories());
    }

    private Users findUser(Long userId) throws CustomException {
        return userRepository.findById(userId).orElseThrow(() -> {
            log.warn("사용자 정보가 존재하지 않습니다. userId={}", userId);
            return new CustomException(ResponseCode.NOT_FOUND_USER);
        });
    }
}
