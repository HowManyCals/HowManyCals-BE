package ksu.finalproject.domain.food.entity;

import ch.qos.logback.core.util.StringUtil;
import jakarta.persistence.*;
import ksu.finalproject.domain.food.entity.enums.ServingUnit;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

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

    @Column(name = "main_category")
    private String mainCategory; // 예 : 면 및 만두

    @Column(name = "sub_category")
    private String subCategory; // 예 : 칼국수

    @Column(name = "detail_category")
    private String detailCategory; // 예 : 바지락

    public String getDisplayName(){
        if (StringUtils.hasText(detailCategory)) return detailCategory + " " + subCategory; // 예 : 바지락 칼국수
        return subCategory; // 예 : 칼국수
    }

    // 기준 제공 수량 (예: 1, 2, 3, 0.5)
    @Column(name = "base_weight", nullable = false)
    private Double baseWeight; // 100 (고정임. 영양성분함량기준)

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false)
    private ServingUnit baseUnit;   // g, ml

    @Column(name = "base_kcal", nullable = false)
    private Double baseKcal; // 100g 기준 칼로리

    // 영양성분이 비어있는 항목이 존재할 수 있으므로, nullable은 true로 처리
    @Column(name = "carbohydrate")
    private Double carbohydrate;

    @Column(name = "protein")
    private Double protein;

    @Column(name = "fat")
    private Double fat;

    // 1인분 기준
    @Column(name = "serving_weight", nullable = false)
    private Double servingWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "serving_unit", nullable = false)
    private ServingUnit servingUnit; // g, ml

    @Column(name = "serving_kcal", nullable = false)
    private Double servingKcal;

    // 음식 비활성화 여부 -> 데이터 삭제 없이 비활성화 처리
    @Column(name = "is_active", nullable = false)
    @Builder.Default // builder 패턴 사용 시 기본값을 true로 처리
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
