package ksu.finalproject.domain.food.repository;

import ksu.finalproject.domain.food.entity.FoodAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FoodAliasRepository extends JpaRepository<FoodAlias, Long> {

    @Query("""
            select fa
            from FoodAlias fa
            join fetch fa.food f
            where fa.isActive = true
              and f.isActive = true
              and fa.normalizedAlias in :normalizedAliases
            """)
    List<FoodAlias> findAllActiveWithFoodByNormalizedAliasIn(@Param("normalizedAliases") Collection<String> normalizedAliases);

    @Query("""
            select fa.food.id
            from FoodAlias fa
            where fa.isActive = true
            """)
    List<Long> findAllActiveFoodIds();
}

