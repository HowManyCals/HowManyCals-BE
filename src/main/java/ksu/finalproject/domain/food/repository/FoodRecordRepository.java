package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.FoodRecord;
import ksu.finalproject.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FoodRecordRepository extends JpaRepository<FoodRecord, Long> {

    // 월별 캘린더: 해당 기간의 모든 기록 조회 (날짜별 칼로리 집계용)
    List<FoodRecord> findByUserAndEatenDateBetween(Users user, LocalDate start, LocalDate end);

    // 일별 상세: 특정 날짜의 모든 식사 기록 (식사 순서대로 정렬)
    List<FoodRecord> findByUserAndEatenDateOrderByMealTypeAsc(Users user, LocalDate date);
}

