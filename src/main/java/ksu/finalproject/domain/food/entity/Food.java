package ksu.finalproject.domain.food.entity;

import jakarta.persistence.*;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Builder
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "food")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_id")
    private Long id;

    @Column(name = "food_name", nullable = false)
    private String foodName;

    // 기준 제공 단위 (BOWL: 공기, SERVING: 인분, PIECE: 조각, CUP: 컵)
    @Enumerated(EnumType.STRING)
    @Column(name = "serving_unit", nullable = false)
    private ServingUnit servingUnit;

    // 기준 제공량 무게 (g)
    @Column(name = "serving_weight_g", nullable = false)
    private Double servingWeightG;

    @Column(name = "calories", nullable = false)
    private Double calories;

    @Column(name = "carbohydrate", nullable = false)
    private Double carbohydrate;

    @Column(name = "protein", nullable = false)
    private Double protein;

    @Column(name = "fat", nullable = false)
    private Double fat;

    // 음식 비활성화 여부 -> 데이터 삭제 없이 비활성화 처리
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
