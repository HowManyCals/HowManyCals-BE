package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.UnmatchedFoodLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmatchedFoodLogRepository extends JpaRepository<UnmatchedFoodLog, Long> {

    // 관리자 미처리 목록 조회 시 사용 (향후 관리자 기능 추가 시)
    // List<UnmatchedFoodLog> findByIsResolvedFalseOrderByCreatedAtDesc();
}

