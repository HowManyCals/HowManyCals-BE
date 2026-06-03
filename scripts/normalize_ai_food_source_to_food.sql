-- ai_food_source -> food 정규화 및 매핑
-- 전제:
-- 1) scripts/create_food_source_map_tables.sql 선실행
-- 2) ai_food_source / food / food_alias 테이블 존재
--
-- 정책:
-- - 이미 food_alias.normalized_alias 와 정확히 일치하면 기존 food에 AUTO 매핑
-- - 일치하지 않으면 ai_food_source 기준으로 새로운 food / food_alias 생성 후 AUTO 매핑
-- - 새로 생성되는 food는 main_category = 'AI_SOURCE', sub_category = ai_food_name 으로 저장
-- - serving_unit 이 ml 이면 ML, 그 외는 G 로 표준화
-- - serving_kcal 이 없는 row는 자동 생성 대상에서 제외 (수동 검토 필요)

START TRANSACTION;

-- 1) 기존 food_alias 와 exact 일치하는 source 우선 매핑
INSERT INTO ai_food_source_map (
    ai_food_source_id,
    food_id,
    mapping_status,
    confidence_score,
    is_active,
    created_at,
    updated_at
)
SELECT
    s.ai_food_source_id,
    MIN(fa.food_id) AS food_id,
    'AUTO' AS mapping_status,
    1.0 AS confidence_score,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM ai_food_source s
JOIN food_alias fa
  ON fa.normalized_alias = s.normalized_ai_food_name
 AND fa.is_active = 1
JOIN food f
  ON f.food_id = fa.food_id
 AND f.is_active = 1
LEFT JOIN ai_food_source_map m
  ON m.ai_food_source_id = s.ai_food_source_id
 AND m.is_active = 1
WHERE s.is_active = 1
  AND s.normalized_ai_food_name IS NOT NULL
  AND TRIM(s.normalized_ai_food_name) <> ''
  AND m.ai_food_source_map_id IS NULL
GROUP BY s.ai_food_source_id;

-- 2) 자동 생성 대상 추출
DROP TEMPORARY TABLE IF EXISTS tmp_ai_food_source_unmapped;
CREATE TEMPORARY TABLE tmp_ai_food_source_unmapped AS
SELECT
    s.ai_food_source_id,
    s.ai_food_name,
    s.normalized_ai_food_name,
    CASE
        WHEN LOWER(TRIM(COALESCE(s.serving_unit, 'g'))) = 'ml' THEN 'ML'
        ELSE 'G'
    END AS serving_unit_enum,
    COALESCE(NULLIF(s.serving_weight, 0), 100.0) AS serving_weight_value,
    COALESCE(
        ROUND((s.serving_kcal / NULLIF(s.serving_weight, 0)) * 100, 2),
        s.serving_kcal
    ) AS base_kcal_value,
    s.serving_kcal,
    s.carbohydrate,
    s.protein,
    s.fat
FROM ai_food_source s
LEFT JOIN ai_food_source_map m
  ON m.ai_food_source_id = s.ai_food_source_id
 AND m.is_active = 1
WHERE s.is_active = 1
  AND m.ai_food_source_map_id IS NULL
  AND s.ai_food_name IS NOT NULL
  AND TRIM(s.ai_food_name) <> ''
  AND s.normalized_ai_food_name IS NOT NULL
  AND TRIM(s.normalized_ai_food_name) <> ''
  AND s.serving_kcal IS NOT NULL;

-- 3) 새 food 생성
INSERT INTO food (
    food_name,
    main_category,
    sub_category,
    detail_category,
    base_weight,
    base_unit,
    base_kcal,
    carbohydrate,
    protein,
    fat,
    serving_weight,
    serving_unit,
    serving_kcal,
    is_active,
    created_at,
    updated_at
)
SELECT
    t.ai_food_name,
    'AI_SOURCE' AS main_category,
    t.ai_food_name AS sub_category,
    NULL AS detail_category,
    100.0 AS base_weight,
    t.serving_unit_enum AS base_unit,
    t.base_kcal_value AS base_kcal,
    t.carbohydrate,
    t.protein,
    t.fat,
    t.serving_weight_value AS serving_weight,
    t.serving_unit_enum AS serving_unit,
    t.serving_kcal,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tmp_ai_food_source_unmapped t
LEFT JOIN food_alias fa
  ON fa.normalized_alias = t.normalized_ai_food_name
 AND fa.is_active = 1
WHERE fa.food_alias_id IS NULL;

-- 4) 새 food_alias 생성
INSERT INTO food_alias (
    food_id,
    alias_name,
    normalized_alias,
    is_active,
    created_at,
    updated_at
)
SELECT
    f.food_id,
    t.ai_food_name,
    t.normalized_ai_food_name,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tmp_ai_food_source_unmapped t
JOIN food f
  ON f.main_category = 'AI_SOURCE'
 AND f.sub_category = t.ai_food_name
 AND f.is_active = 1
LEFT JOIN food_alias fa
  ON fa.food_id = f.food_id
 AND fa.normalized_alias = t.normalized_ai_food_name
WHERE fa.food_alias_id IS NULL;

-- 5) 남은 unmapped source 를 food_alias 기반으로 매핑
INSERT INTO ai_food_source_map (
    ai_food_source_id,
    food_id,
    mapping_status,
    confidence_score,
    is_active,
    created_at,
    updated_at
)
SELECT
    s.ai_food_source_id,
    MIN(fa.food_id) AS food_id,
    'AUTO' AS mapping_status,
    0.95 AS confidence_score,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM ai_food_source s
JOIN food_alias fa
  ON fa.normalized_alias = s.normalized_ai_food_name
 AND fa.is_active = 1
JOIN food f
  ON f.food_id = fa.food_id
 AND f.is_active = 1
LEFT JOIN ai_food_source_map m
  ON m.ai_food_source_id = s.ai_food_source_id
 AND m.is_active = 1
WHERE s.is_active = 1
  AND m.ai_food_source_map_id IS NULL
GROUP BY s.ai_food_source_id;

COMMIT;

-- 검증용
-- SELECT COUNT(*) FROM ai_food_source_map;
-- SELECT mapping_status, COUNT(*) FROM ai_food_source_map GROUP BY mapping_status;
-- SELECT COUNT(*) FROM ai_food_source s LEFT JOIN ai_food_source_map m ON m.ai_food_source_id = s.ai_food_source_id AND m.is_active = 1 WHERE s.is_active = 1 AND m.ai_food_source_map_id IS NULL;

