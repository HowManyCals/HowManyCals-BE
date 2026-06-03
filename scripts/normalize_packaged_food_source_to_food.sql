-- packaged_food_source -> food 개념 정규화 및 매핑
-- 전제:
-- 1) scripts/create_food_source_map_tables.sql 선실행
-- 2) packaged_food_source / food / food_alias 테이블 존재
--
-- 정책:
-- - representative_food_name / canonical_product_name 기준으로 음식 개념 key 생성
-- - 기존 food_alias 와 exact 일치하면 기존 food에 AUTO 매핑
-- - 일치하지 않는 개념 key 는 food 로 승격 후 packaged_food_source_map 에 AUTO 매핑
-- - 새로 생성되는 food 는 main_category = 'PACKAGED_SOURCE', sub_category = 개념명 으로 저장
-- - packaged_food_source 영양값은 source 마다 granularity 가 다를 수 있어 serving/base 를 100g 기준 대표값으로 저장
-- - canonical_confidence = 'LOW' 인 row 는 자동 승격 대상에서 제외

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_packaged_food_source_group;
CREATE TEMPORARY TABLE tmp_packaged_food_source_group AS
SELECT
    COALESCE(
        NULLIF(TRIM(p.normalized_representative_food_name), ''),
        NULLIF(TRIM(p.normalized_canonical_product_name), '')
    ) AS normalized_food_key,
    COALESCE(
        NULLIF(TRIM(p.representative_food_name), ''),
        NULLIF(TRIM(p.canonical_product_name), ''),
        p.product_name_raw
    ) AS food_display_name,
    ROUND(AVG(p.kcal), 2) AS avg_kcal,
    ROUND(AVG(p.carbohydrate), 2) AS avg_carbohydrate,
    ROUND(AVG(p.protein), 2) AS avg_protein,
    ROUND(AVG(p.fat), 2) AS avg_fat,
    COUNT(*) AS source_count
FROM packaged_food_source p
WHERE p.is_active = 1
  AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW'
  AND COALESCE(
        NULLIF(TRIM(p.normalized_representative_food_name), ''),
        NULLIF(TRIM(p.normalized_canonical_product_name), '')
      ) IS NOT NULL
GROUP BY
    COALESCE(
        NULLIF(TRIM(p.normalized_representative_food_name), ''),
        NULLIF(TRIM(p.normalized_canonical_product_name), '')
    ),
    COALESCE(
        NULLIF(TRIM(p.representative_food_name), ''),
        NULLIF(TRIM(p.canonical_product_name), ''),
        p.product_name_raw
    );

-- 1) 기존 alias 와 exact 일치하는 개념은 먼저 매핑
INSERT INTO packaged_food_source_map (
    packaged_food_source_id,
    food_id,
    mapping_status,
    confidence_score,
    is_active,
    created_at,
    updated_at
)
SELECT
    p.packaged_food_source_id,
    MIN(fa.food_id) AS food_id,
    'AUTO' AS mapping_status,
    1.0 AS confidence_score,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM packaged_food_source p
JOIN food_alias fa
  ON fa.normalized_alias = COALESCE(
        NULLIF(TRIM(p.normalized_representative_food_name), ''),
        NULLIF(TRIM(p.normalized_canonical_product_name), '')
     )
 AND fa.is_active = 1
JOIN food f
  ON f.food_id = fa.food_id
 AND f.is_active = 1
LEFT JOIN packaged_food_source_map m
  ON m.packaged_food_source_id = p.packaged_food_source_id
 AND m.is_active = 1
WHERE p.is_active = 1
  AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW'
  AND m.packaged_food_source_map_id IS NULL
GROUP BY p.packaged_food_source_id;

-- 2) 아직 매핑되지 않은 개념 key 를 food 로 승격
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
    g.food_display_name,
    'PACKAGED_SOURCE' AS main_category,
    g.food_display_name AS sub_category,
    NULL AS detail_category,
    100.0 AS base_weight,
    'G' AS base_unit,
    COALESCE(g.avg_kcal, 0.0) AS base_kcal,
    g.avg_carbohydrate,
    g.avg_protein,
    g.avg_fat,
    100.0 AS serving_weight,
    'G' AS serving_unit,
    COALESCE(g.avg_kcal, 0.0) AS serving_kcal,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tmp_packaged_food_source_group g
LEFT JOIN food_alias fa
  ON fa.normalized_alias = g.normalized_food_key
 AND fa.is_active = 1
WHERE g.food_display_name IS NOT NULL
  AND TRIM(g.food_display_name) <> ''
  AND fa.food_alias_id IS NULL;

-- 3) 승격된 개념 row 에 alias 부여
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
    g.food_display_name,
    g.normalized_food_key,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tmp_packaged_food_source_group g
JOIN food f
  ON f.main_category = 'PACKAGED_SOURCE'
 AND f.sub_category = g.food_display_name
 AND f.is_active = 1
LEFT JOIN food_alias fa
  ON fa.food_id = f.food_id
 AND fa.normalized_alias = g.normalized_food_key
WHERE g.normalized_food_key IS NOT NULL
  AND TRIM(g.normalized_food_key) <> ''
  AND fa.food_alias_id IS NULL;

-- 4) source row -> food AUTO 매핑
INSERT INTO packaged_food_source_map (
    packaged_food_source_id,
    food_id,
    mapping_status,
    confidence_score,
    is_active,
    created_at,
    updated_at
)
SELECT
    p.packaged_food_source_id,
    MIN(fa.food_id) AS food_id,
    'AUTO' AS mapping_status,
    0.9 AS confidence_score,
    1 AS is_active,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM packaged_food_source p
JOIN food_alias fa
  ON fa.normalized_alias = COALESCE(
        NULLIF(TRIM(p.normalized_representative_food_name), ''),
        NULLIF(TRIM(p.normalized_canonical_product_name), '')
     )
 AND fa.is_active = 1
JOIN food f
  ON f.food_id = fa.food_id
 AND f.is_active = 1
LEFT JOIN packaged_food_source_map m
  ON m.packaged_food_source_id = p.packaged_food_source_id
 AND m.is_active = 1
WHERE p.is_active = 1
  AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW'
  AND m.packaged_food_source_map_id IS NULL
GROUP BY p.packaged_food_source_id;

COMMIT;

-- 검증용
-- SELECT COUNT(*) FROM packaged_food_source_map;
-- SELECT mapping_status, COUNT(*) FROM packaged_food_source_map GROUP BY mapping_status;
-- SELECT COUNT(*) FROM packaged_food_source p LEFT JOIN packaged_food_source_map m ON m.packaged_food_source_id = p.packaged_food_source_id AND m.is_active = 1 WHERE p.is_active = 1 AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW' AND m.packaged_food_source_map_id IS NULL;

