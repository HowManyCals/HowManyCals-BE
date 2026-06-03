package ksu.finalproject.domain.recommendation.repository;

import ksu.finalproject.domain.recommendation.entity.MealRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealRecommendationItemRepository extends JpaRepository<MealRecommendationItem, Long> {
}

