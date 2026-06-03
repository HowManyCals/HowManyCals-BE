package ksu.finalproject.domain.recommendation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ksu.finalproject.domain.goal.entity.CaloriesGoal;
import ksu.finalproject.domain.recommendation.engine.DietRecommendationEngine;
import ksu.finalproject.domain.recommendation.engine.RecommendationJobStatus;
import ksu.finalproject.domain.user.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "daily_meal_recommendation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_meal_recommendation_user_date", columnNames = {"user_id", "recommendation_date"})
        }
)
public class DailyMealRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calories_goal_id")
    private CaloriesGoal caloriesGoal;

    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecommendationJobStatus status;

    @Column(name = "target_daily_kcal")
    private Integer targetDailyKcal;

    @Column(name = "breakfast_target_kcal")
    private Integer breakfastTargetKcal;

    @Column(name = "lunch_target_kcal")
    private Integer lunchTargetKcal;

    @Column(name = "dinner_target_kcal")
    private Integer dinnerTargetKcal;

    @Column(name = "catalog_version")
    private String catalogVersion;

    @Column(name = "failure_reason")
    private String failureReason;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MealRecommendationItem> items = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateSuccess(CaloriesGoal caloriesGoal,
                              String catalogVersion,
                              DietRecommendationEngine.DailyCaloriePlan plan) {
        this.caloriesGoal = caloriesGoal;
        this.status = RecommendationJobStatus.SUCCESS;
        this.targetDailyKcal = plan.targetDailyKcal();
        this.breakfastTargetKcal = plan.breakfastTargetKcal();
        this.lunchTargetKcal = plan.lunchTargetKcal();
        this.dinnerTargetKcal = plan.dinnerTargetKcal();
        this.catalogVersion = catalogVersion;
        this.failureReason = null;
    }

    public void updateFailure(CaloriesGoal caloriesGoal, String failureReason) {
        this.caloriesGoal = caloriesGoal;
        this.status = RecommendationJobStatus.FAILED;
        this.targetDailyKcal = null;
        this.breakfastTargetKcal = null;
        this.lunchTargetKcal = null;
        this.dinnerTargetKcal = null;
        this.failureReason = failureReason;
    }

    public void replaceItems(List<MealRecommendationItem> newItems) {
        this.items.clear();
        for (MealRecommendationItem item : newItems) {
            addItem(item);
        }
    }

    public void addItem(MealRecommendationItem item) {
        item.assignRecommendation(this);
        this.items.add(item);
    }
}

