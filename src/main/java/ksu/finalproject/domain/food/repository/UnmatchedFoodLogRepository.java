package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.UnmatchedFoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnmatchedFoodLogRepository extends JpaRepository<UnmatchedFoodLog, Long> {
}

