package ksu.finalproject.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ksu.finalproject.domain.food.entity.Food;
import ksu.finalproject.domain.food.entity.enums.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "meal_recommendation_item")
public class MealRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private DailyMealRecommendation recommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(name = "food_name_snapshot", nullable = false)
    private String foodNameSnapshot;

    @Column(name = "group_name_snapshot", nullable = false)
    private String groupNameSnapshot;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "calories_snapshot", nullable = false)
    private Integer caloriesSnapshot;

    @Column(name = "carbohydrate_snapshot")
    private Double carbohydrateSnapshot;

    @Column(name = "protein_snapshot")
    private Double proteinSnapshot;

    @Column(name = "fat_snapshot")
    private Double fatSnapshot;

    void assignRecommendation(DailyMealRecommendation recommendation) {
        this.recommendation = recommendation;
    }
}

