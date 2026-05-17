package ksu.finalproject.domain.user.repository;

import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.WeightGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WeightGoalRepository extends JpaRepository<WeightGoal, Long> {
    // 현재 목표
    Optional<WeightGoal> findTopByUserOrderByCreatedAtDesc(Users user);
}
