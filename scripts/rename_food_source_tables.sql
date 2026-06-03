-- AI/가공식품 원천 테이블 명칭 정리
-- 실행 대상: MySQL / RDS
-- 목적:
--   ai_food_entry      -> ai_food_source
--   packaged_food      -> packaged_food_source
--   ai_food_entry_id   -> ai_food_source_id
--   packaged_food_id   -> packaged_food_source_id
--
-- 주의:
-- 1) 앱 배포 전에 먼저 실행하세요.
-- 2) 관련 세션/배치가 테이블을 사용 중이지 않은 상태에서 실행하세요.
-- 3) food_search_index 재구성 SQL도 새 테이블명 기준으로 이미 수정되어 있습니다.

START TRANSACTION;

RENAME TABLE ai_food_entry TO ai_food_source;
ALTER TABLE ai_food_source RENAME COLUMN ai_food_entry_id TO ai_food_source_id;

RENAME TABLE packaged_food TO packaged_food_source;
ALTER TABLE packaged_food_source RENAME COLUMN packaged_food_id TO packaged_food_source_id;

COMMIT;

-- 검증용
-- SHOW COLUMNS FROM ai_food_source;
-- SHOW COLUMNS FROM packaged_food_source;

