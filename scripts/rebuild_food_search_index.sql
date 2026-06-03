-- food_search_index 재구성 SQL
-- MySQL / RDS 기준
--
-- 사용 방법
-- 1) 빠른 복구가 목적이면 [A안]만 실행
--    - 장점: 지금 DB 테이블만으로 바로 실행 가능
--    - 단점: AI / 가공식품 alias CSV를 쓰지 않으므로 exact 커버리지가 앱 initializer와 100% 동일하지는 않음
--
-- 2) 앱 initializer와 최대한 비슷하게 재구성하려면 [B안] 사용
--    - DBeaver로 아래 helper table 2개를 먼저 만든 뒤
--    - CSV import 기능으로 alias CSV를 넣고
--    - [B안] INSERT를 실행
--
-- 현재 앱 설정 기준
-- - packaged.enabled=true
-- - packaged.includeMedium=true
-- - packaged.includeLow=false


-- ============================================================
-- [공통] 전체 삭제
-- ============================================================
-- 주의: 기존 food_search_index를 전부 비웁니다.
-- 운영 반영 전 백업이 필요하면 먼저 따로 덤프하세요.

-- DELETE FROM food_search_index;
-- 또는 FK 없으면 아래가 더 빠를 수 있음
-- TRUNCATE TABLE food_search_index;


-- ============================================================
-- [A안] DB 테이블만으로 빠르게 복구
-- ============================================================
-- 이 안은 지금 DB에 있는 테이블만 사용합니다.
-- FOOD는 거의 그대로 복구 가능하고,
-- AI / PACKAGED는 canonical 중심의 최소 exact 인덱스로 복구합니다.

START TRANSACTION;

DELETE FROM food_search_index;

-- 1) FOOD source
INSERT INTO food_search_index (
    normalized_name,
    search_name,
    resolved_food_name,
    source_type,
    external_code,
    priority,
    serving_kcal,
    carbohydrate,
    protein,
    fat,
    serving_unit_label,
    is_active,
    created_at,
    updated_at
)
SELECT
    fa.normalized_alias,
    fa.alias_name,
    CASE
        WHEN f.detail_category IS NOT NULL AND TRIM(f.detail_category) <> ''
            THEN CONCAT(f.detail_category, ' ', f.sub_category)
        ELSE f.sub_category
    END AS resolved_food_name,
    'FOOD' AS source_type,
    CAST(f.food_id AS CHAR) AS external_code,
    100 AS priority,
    f.serving_kcal,
    f.carbohydrate,
    f.protein,
    f.fat,
    CASE
        WHEN f.serving_unit = 'G' THEN CONCAT(f.serving_weight, 'g')
        WHEN f.serving_unit = 'ML' THEN CONCAT(f.serving_weight, 'ml')
        ELSE NULL
    END AS serving_unit_label,
    1 AS is_active,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM food_alias fa
JOIN food f ON f.food_id = fa.food_id
WHERE fa.is_active = 1
  AND f.is_active = 1
  AND fa.normalized_alias IS NOT NULL
  AND TRIM(fa.normalized_alias) <> '';

-- 2) AI_FOOD_ENTRY source (DB-only 최소 exact)
INSERT INTO food_search_index (
    normalized_name,
    search_name,
    resolved_food_name,
    source_type,
    external_code,
    priority,
    serving_kcal,
    carbohydrate,
    protein,
    fat,
    serving_unit_label,
    is_active,
    created_at,
    updated_at
)
SELECT
    a.normalized_ai_food_name,
    a.ai_food_name,
    a.ai_food_name,
    'AI_FOOD_ENTRY' AS source_type,
    a.normalized_ai_food_name AS external_code,
    100 AS priority,
    a.serving_kcal,
    a.carbohydrate,
    a.protein,
    a.fat,
    CASE
        WHEN a.serving_weight IS NOT NULL
         AND a.serving_unit IS NOT NULL
         AND TRIM(a.serving_unit) <> ''
            THEN CONCAT(a.serving_weight, a.serving_unit)
        ELSE NULL
    END AS serving_unit_label,
    1 AS is_active,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM ai_food_source a
WHERE a.is_active = 1
  AND a.normalized_ai_food_name IS NOT NULL
  AND TRIM(a.normalized_ai_food_name) <> '';

-- 3) PACKAGED_FOOD source (DB-only 최소 exact: canonical name)
INSERT INTO food_search_index (
    normalized_name,
    search_name,
    resolved_food_name,
    source_type,
    external_code,
    priority,
    serving_kcal,
    carbohydrate,
    protein,
    fat,
    serving_unit_label,
    is_active,
    created_at,
    updated_at
)
SELECT
    p.normalized_canonical_product_name,
    p.canonical_product_name,
    COALESCE(NULLIF(TRIM(p.canonical_product_name), ''), p.product_name_raw) AS resolved_food_name,
    'PACKAGED_FOOD' AS source_type,
    p.product_code AS external_code,
    80 AS priority,
    p.kcal,
    p.carbohydrate,
    p.protein,
    p.fat,
    COALESCE(NULLIF(TRIM(p.serving_reference), ''), NULLIF(TRIM(p.package_weight), '')) AS serving_unit_label,
    1 AS is_active,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM packaged_food_source p
WHERE p.is_active = 1
  AND p.normalized_canonical_product_name IS NOT NULL
  AND TRIM(p.normalized_canonical_product_name) <> ''
  AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW';

-- 4) PACKAGED_FOOD source (DB-only 최소 exact: raw product name 보강)
INSERT INTO food_search_index (
    normalized_name,
    search_name,
    resolved_food_name,
    source_type,
    external_code,
    priority,
    serving_kcal,
    carbohydrate,
    protein,
    fat,
    serving_unit_label,
    is_active,
    created_at,
    updated_at
)
SELECT
    p.normalized_product_name_raw,
    p.product_name_raw,
    COALESCE(NULLIF(TRIM(p.canonical_product_name), ''), p.product_name_raw) AS resolved_food_name,
    'PACKAGED_FOOD' AS source_type,
    p.product_code AS external_code,
    70 AS priority,
    p.kcal,
    p.carbohydrate,
    p.protein,
    p.fat,
    COALESCE(NULLIF(TRIM(p.serving_reference), ''), NULLIF(TRIM(p.package_weight), '')) AS serving_unit_label,
    1 AS is_active,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM packaged_food_source p
WHERE p.is_active = 1
  AND p.normalized_product_name_raw IS NOT NULL
  AND TRIM(p.normalized_product_name_raw) <> ''
  AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW'
  AND (
        p.normalized_canonical_product_name IS NULL
        OR p.normalized_canonical_product_name <> p.normalized_product_name_raw
      );

COMMIT;

-- 검증 쿼리
-- SELECT COUNT(*) AS total_count FROM food_search_index;
-- SELECT source_type, COUNT(*) AS cnt FROM food_search_index GROUP BY source_type ORDER BY source_type;


-- ============================================================
-- [B안] CSV alias import까지 포함한 정밀 복구
-- ============================================================
-- 목적: FoodSearchIndexInitializer와 최대한 비슷하게 재구성
--
-- 사전 준비 (DBeaver 추천)
-- 1) 아래 helper table 생성
-- 2) DBeaver의 Import Data 기능으로 CSV를 각각 import
--    - ai_food_entry_alias_seed.csv -> tmp_ai_food_entry_alias_seed
--    - packaged_food_alias_seed.csv -> tmp_packaged_food_alias_seed
-- 3) 이후 아래 DELETE + INSERT 실행
--
-- helper table은 TEMPORARY가 아니라 일반 table로 만들었습니다.
-- DBeaver import 편의 때문이며, 작업 후 DROP TABLE 해도 됩니다.

-- CREATE TABLE IF NOT EXISTS tmp_ai_food_entry_alias_seed (
--     source_code VARCHAR(500) NOT NULL,
--     alias_name VARCHAR(500) NOT NULL,
--     normalized_alias VARCHAR(500) NOT NULL,
--     priority INT NULL
-- );
--
-- CREATE TABLE IF NOT EXISTS tmp_packaged_food_alias_seed (
--     source_code VARCHAR(500) NOT NULL,
--     alias_name VARCHAR(500) NOT NULL,
--     normalized_alias VARCHAR(500) NOT NULL,
--     priority INT NULL,
--     alias_type VARCHAR(100) NULL
-- );

-- START TRANSACTION;
--
-- DELETE FROM food_search_index;
--
-- -- 1) FOOD source (initializer와 동일)
-- INSERT INTO food_search_index (
--     normalized_name,
--     search_name,
--     resolved_food_name,
--     source_type,
--     external_code,
--     priority,
--     serving_kcal,
--     carbohydrate,
--     protein,
--     fat,
--     serving_unit_label,
--     is_active,
--     created_at,
--     updated_at
-- )
-- SELECT
--     fa.normalized_alias,
--     fa.alias_name,
--     CASE
--         WHEN f.detail_category IS NOT NULL AND TRIM(f.detail_category) <> ''
--             THEN CONCAT(f.detail_category, ' ', f.sub_category)
--         ELSE f.sub_category
--     END,
--     'FOOD',
--     CAST(f.food_id AS CHAR),
--     100,
--     f.serving_kcal,
--     f.carbohydrate,
--     f.protein,
--     f.fat,
--     CASE
--         WHEN f.serving_unit = 'G' THEN CONCAT(f.serving_weight, 'g')
--         WHEN f.serving_unit = 'ML' THEN CONCAT(f.serving_weight, 'ml')
--         ELSE NULL
--     END,
--     1,
--     CURRENT_TIMESTAMP,
--     CURRENT_TIMESTAMP
-- FROM food_alias fa
-- JOIN food f ON f.food_id = fa.food_id
-- WHERE fa.is_active = 1
--   AND f.is_active = 1
--   AND fa.normalized_alias IS NOT NULL
--   AND TRIM(fa.normalized_alias) <> '';
--
-- -- 2) AI_FOOD_ENTRY source (alias CSV 사용)
-- INSERT INTO food_search_index (
--     normalized_name,
--     search_name,
--     resolved_food_name,
--     source_type,
--     external_code,
--     priority,
--     serving_kcal,
--     carbohydrate,
--     protein,
--     fat,
--     serving_unit_label,
--     is_active,
--     created_at,
--     updated_at
-- )
-- SELECT
--     t.normalized_alias,
--     t.alias_name,
--     a.ai_food_name,
--     'AI_FOOD_ENTRY',
--     t.source_code,
--     COALESCE(t.priority, 100),
--     a.serving_kcal,
--     a.carbohydrate,
--     a.protein,
--     a.fat,
--     CASE
--         WHEN a.serving_weight IS NOT NULL
--          AND a.serving_unit IS NOT NULL
--          AND TRIM(a.serving_unit) <> ''
--             THEN CONCAT(a.serving_weight, a.serving_unit)
--         ELSE NULL
--     END,
--     1,
--     CURRENT_TIMESTAMP,
--     CURRENT_TIMESTAMP
-- FROM tmp_ai_food_entry_alias_seed t
-- JOIN ai_food_source a
--   ON a.normalized_ai_food_name = t.source_code
-- WHERE a.is_active = 1
--   AND t.normalized_alias IS NOT NULL
--   AND TRIM(t.normalized_alias) <> '';
--
-- -- 3) PACKAGED_FOOD source (alias CSV 사용, initializer 정책 반영)
-- INSERT INTO food_search_index (
--     normalized_name,
--     search_name,
--     resolved_food_name,
--     source_type,
--     external_code,
--     priority,
--     serving_kcal,
--     carbohydrate,
--     protein,
--     fat,
--     serving_unit_label,
--     is_active,
--     created_at,
--     updated_at
-- )
-- SELECT
--     t.normalized_alias,
--     t.alias_name,
--     COALESCE(NULLIF(TRIM(p.canonical_product_name), ''), p.product_name_raw) AS resolved_food_name,
--     'PACKAGED_FOOD',
--     t.source_code,
--     COALESCE(t.priority, 80),
--     p.kcal,
--     p.carbohydrate,
--     p.protein,
--     p.fat,
--     COALESCE(NULLIF(TRIM(p.serving_reference), ''), NULLIF(TRIM(p.package_weight), '')) AS serving_unit_label,
--     1,
--     CURRENT_TIMESTAMP,
--     CURRENT_TIMESTAMP
-- FROM tmp_packaged_food_alias_seed t
-- JOIN packaged_food_source p
--   ON p.product_code = t.source_code
-- WHERE p.is_active = 1
--   AND t.normalized_alias IS NOT NULL
--   AND TRIM(t.normalized_alias) <> ''
--   AND UPPER(COALESCE(p.canonical_confidence, '')) <> 'LOW'
--   AND COALESCE(t.alias_type, '') <> 'REPRESENTATIVE_NAME'
--   AND NOT (
--       p.normalized_representative_food_name IS NOT NULL
--       AND TRIM(p.normalized_representative_food_name) <> ''
--       AND p.normalized_representative_food_name = t.normalized_alias
--       AND COALESCE(t.alias_type, '') LIKE 'CANONICAL%'
--   );
--
-- COMMIT;
--
-- -- 작업 후 정리 원하면
-- -- DROP TABLE IF EXISTS tmp_ai_food_entry_alias_seed;
-- -- DROP TABLE IF EXISTS tmp_packaged_food_alias_seed;


-- ============================================================
-- [검증용 SQL]
-- ============================================================
-- SELECT COUNT(*) AS total_count FROM food_search_index;
--
-- SELECT source_type, COUNT(*) AS cnt
-- FROM food_search_index
-- GROUP BY source_type
-- ORDER BY source_type;
--
-- SELECT normalized_name, search_name, resolved_food_name, source_type, priority
-- FROM food_search_index
-- WHERE normalized_name = '김치찌개'
-- ORDER BY source_type, priority DESC, food_search_index_id ASC;



