package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.AiAnalysisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisLogRepository extends JpaRepository<AiAnalysisLog, Long> {
    Optional<AiAnalysisLog> findByIdAndUserId(Long id, Long userId);
}

