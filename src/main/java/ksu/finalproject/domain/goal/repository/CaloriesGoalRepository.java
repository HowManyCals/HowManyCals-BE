package ksu.finalproject.domain.goal.repository;

import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaloriesGoalRepository extends JpaRepository<CaloriesGoal, Long> {
    // 현재 목표
    Optional<CaloriesGoal> findTopByUserOrderByCreatedAtDesc(Users user);

    List<CaloriesGoal> findAllByUserOrderByCreatedAtAsc(Users user);

    @Query("select distinct cg.user.id from CaloriesGoal cg")
    List<Long> findDistinctUserIds();
}
