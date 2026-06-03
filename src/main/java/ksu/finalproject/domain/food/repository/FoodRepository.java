package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.Food;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {
	List<Food> findAllByFoodNameIn(Collection<String> foodNames);

	List<Food> findAllByIsActiveTrue();

	long countByIsActiveTrue();

	@Query("""
	        select distinct f.mainCategory
	        from Food f
	        where f.isActive = true
	          and f.mainCategory is not null
	          and f.mainCategory <> ''
	        order by f.mainCategory asc
	        """)
	List<String> findDistinctActiveMainCategories();

	@Query("""
	        select f
	        from Food f
	        where f.isActive = true
	          and replace(replace(lower(coalesce(f.subCategory, '')), ' ', ''), '_', '') = :normalizedValue
	        order by f.id asc
	        """)
	List<Food> findAllActiveByNormalizedSubCategoryExact(@Param("normalizedValue") String normalizedValue);

	@Query("""
	        select f
	        from Food f
	        where f.isActive = true
	          and (
	               replace(replace(lower(coalesce(f.foodName, '')), ' ', ''), '_', '') like concat('%', :normalizedKeyword, '%')
	            or replace(replace(lower(coalesce(f.mainCategory, '')), ' ', ''), '_', '') like concat('%', :normalizedKeyword, '%')
	            or replace(replace(lower(coalesce(f.subCategory, '')), ' ', ''), '_', '') like concat('%', :normalizedKeyword, '%')
	            or replace(replace(lower(coalesce(f.detailCategory, '')), ' ', ''), '_', '') like concat('%', :normalizedKeyword, '%')
	          )
	        order by f.id asc
	        """)
	List<Food> searchActiveFoodsByNormalizedKeyword(@Param("normalizedKeyword") String normalizedKeyword, Pageable pageable);
}

