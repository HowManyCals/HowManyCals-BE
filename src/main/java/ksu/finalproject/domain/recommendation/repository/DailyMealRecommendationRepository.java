package ksu.finalproject.domain.recommendation.repository;

import ksu.finalproject.domain.recommendation.entity.DailyMealRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyMealRecommendationRepository extends JpaRepository<DailyMealRecommendation, Long> {

    Optional<DailyMealRecommendation> findByUserIdAndRecommendationDate(Long userId, LocalDate recommendationDate);

    @EntityGraph(attributePaths = "items")
    Optional<DailyMealRecommendation> findWithItemsByUserIdAndRecommendationDate(Long userId, LocalDate recommendationDate);
}

