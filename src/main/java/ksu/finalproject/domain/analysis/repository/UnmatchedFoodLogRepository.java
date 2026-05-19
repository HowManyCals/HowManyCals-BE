package ksu.finalproject.domain.analysis.repository;

import ksu.finalproject.domain.food.entity.UnmatchedFoodLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmatchedFoodLogRepository extends JpaRepository<UnmatchedFoodLog, Long> {

}
