package ksu.finalproject.domain.dietlog.repository;

import ksu.finalproject.domain.foodrecord.entity.FoodRecord;
import ksu.finalproject.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DietLogRepository extends JpaRepository<FoodRecord, Long> {
    // 기록 일수 = 기록한 날짜 수
        // SELECT COUNT(DISTINCT(fr.eaten_date))
        // FROM food_record fr
        // WHERE user.id = ?
    @Query("SELECT COUNT(DISTINCT(fr.eatenDate)) FROM FoodRecord fr WHERE fr.user = :user")
    long countDistinctEatenDateByUser(@Param("user")Users user);

    // 목표 달성 일수 (성공 count)
    /*
     * 3분류로 나눔에 따라 일단 로직 보류
     * 3분류
     *  - 다이어터
     *  - 유지어터
     *  - 벌크업
     */

    // 연속 달성 일수

}
