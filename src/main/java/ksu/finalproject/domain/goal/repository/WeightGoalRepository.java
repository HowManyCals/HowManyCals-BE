package ksu.finalproject.domain.goal.repository;

import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.goal.entity.WeightGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WeightGoalRepository extends JpaRepository<WeightGoal, Long> {
    // 목표 체중 (최신)
    Optional<WeightGoal> findTopByUserOrderByCreatedAtDesc(Users user);
    // SELECT *
    // FROM weight_goal
    // WHERE user_id = ?
    // ORDER BY created_at DESC
    // LIMIT 1;
}
