package ksu.finalproject.domain.food.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@Table(name = "unmatched_food_log")
public class UnmatchedFoodLog {
//    id
//    llmFoodName        ← "어떤 음식이" 없는지
//    isResolved         ← 관리자 처리 여부
//    createdAt          ← "언제" 발생했는지
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unmatched_id")
    private Long id;

    @Column(name = "llm_food_name", nullable = false)
    private String llmFoodName;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private Boolean isResolved = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDate createdAt;

}
