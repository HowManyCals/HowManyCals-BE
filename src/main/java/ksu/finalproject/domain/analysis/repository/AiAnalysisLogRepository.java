package ksu.finalproject.domain.analysis.repository;

import ksu.finalproject.domain.analysis.entity.AiAnalysisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisLogRepository extends JpaRepository<AiAnalysisLog, Long> {
    Optional<AiAnalysisLog> findByIdAndUserId(Long id, Long userId);
}

