package ksu.finalproject.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "unmatched_food_log")
public class UnmatchedFoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unmatched_food_log_id")
    private Long id;

    @Column(name = "ai_log_id", nullable = false)
    private Long aiLogId;

    @Column(name = "original_food_name")
    private String originalFoodName;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "llm_main_category")
    private String llmMainCategory;

    @Column(name = "llm_base_food")
    private String llmBaseFood;

    @Column(name = "llm_modifiers", length = 1000)
    private String llmModifiers;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

