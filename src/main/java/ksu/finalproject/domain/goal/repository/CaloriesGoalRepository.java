package ksu.finalproject.domain.goal.repository;

import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.goal.entity.WeightGoal;
import ksu.finalproject.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaloriesGoalRepository extends JpaRepository<CaloriesGoal, Long> {
    // 현재 목표
    Optional<CaloriesGoal> findTopByUserOrderByCreatedAtDesc(Users user);
}
