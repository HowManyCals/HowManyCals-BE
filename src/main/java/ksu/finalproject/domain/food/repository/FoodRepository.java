package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {
	List<Food> findAllByFoodNameIn(Collection<String> foodNames);
}

