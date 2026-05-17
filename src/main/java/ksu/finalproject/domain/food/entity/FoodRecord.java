package ksu.finalproject.domain.food.entity;

import jakarta.persistence.*;
import ksu.finalproject.domain.food.entity.enums.MealType;
import ksu.finalproject.domain.user.entity.Users;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "food_record")
@SQLRestriction("is_active = true")
public class FoodRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long id;

    // 식사 기록(N) : 사용자(1) 단, 연관관계가 null이어서는 안 됨
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) // FK 참조
    private Users user;

    // 식사 기록(N) : 음식(1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    // AI 분석 로그(N) : 식사 기록(1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_log_id")
    private AiAnalysisLog aiAnalysisLog;

    @Column(name = "food_name", nullable = false)
    private String foodName;

    @Column(name = "eaten_date", nullable = false)
    private LocalDate eatenDate;

    // 식사 종류 (아침/점심/저녁/간식)
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    // 실제 섭취 칼로리 역정규화 저장 (= food.calories / 100 * amountG)
    // Food가 나중에 수정돼도 당시 기록은 변하지 않아야 하므로 직접 보관
    @Column(name = "calories", nullable = false)
    private Double calories;

    @Column(name = "carbohydrate")
    private Double carbohydrate;

    @Column(name = "protein")
    private Double protein;

    @Column(name = "fat")
    private Double fat;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false) // update 때 안 바뀌도록 false 지정
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}

