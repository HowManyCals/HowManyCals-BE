-- food source -> food 매핑 테이블 생성
-- 실행 대상: MySQL / RDS

START TRANSACTION;

CREATE TABLE IF NOT EXISTS ai_food_source_map (
    ai_food_source_map_id BIGINT NOT NULL AUTO_INCREMENT,
    ai_food_source_id BIGINT NOT NULL,
    food_id BIGINT NOT NULL,
    mapping_status VARCHAR(30) NOT NULL,
    confidence_score DOUBLE NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ai_food_source_map_id),
    UNIQUE KEY uk_ai_food_source_map_source_id (ai_food_source_id),
    KEY idx_ai_food_source_map_food_id (food_id),
    CONSTRAINT fk_ai_food_source_map_source FOREIGN KEY (ai_food_source_id) REFERENCES ai_food_source (ai_food_source_id),
    CONSTRAINT fk_ai_food_source_map_food FOREIGN KEY (food_id) REFERENCES food (food_id)
);

CREATE TABLE IF NOT EXISTS packaged_food_source_map (
    packaged_food_source_map_id BIGINT NOT NULL AUTO_INCREMENT,
    packaged_food_source_id BIGINT NOT NULL,
    food_id BIGINT NOT NULL,
    mapping_status VARCHAR(30) NOT NULL,
    confidence_score DOUBLE NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (packaged_food_source_map_id),
    UNIQUE KEY uk_packaged_food_source_map_source_id (packaged_food_source_id),
    KEY idx_packaged_food_source_map_food_id (food_id),
    CONSTRAINT fk_packaged_food_source_map_source FOREIGN KEY (packaged_food_source_id) REFERENCES packaged_food_source (packaged_food_source_id),
    CONSTRAINT fk_packaged_food_source_map_food FOREIGN KEY (food_id) REFERENCES food (food_id)
);

COMMIT;

-- 검증용
-- SHOW COLUMNS FROM ai_food_source_map;
-- SHOW COLUMNS FROM packaged_food_source_map;

