-- 2026-10-28 스키마 추가분: hubs + refresh_tokens (T3-2)
-- 출처: MySQL 8.0.46 검증 DB에서 추출한 docs/schema.sql의 두 CREATE TABLE 문.
-- 대상: 이전 schema.sql의 chat_messages, reports, ride_requests, users만 있는 DB.
-- 적용 전 전체 데이터 백업 및 아래 두 테이블이 없는지 확인한다.
--   SHOW TABLES LIKE 'hubs';
--   SHOW TABLES LIKE 'refresh_tokens';
-- 둘 중 하나라도 이미 있으면 이 파일을 바로 실행하지 말고 실제 DDL과 schema.sql을 비교한다.
-- 기존 테이블·데이터를 수정하지 않는다. IF NOT EXISTS로 다른 구조를 숨기지 않는다.
-- MySQL DDL은 자동 커밋되므로 부분 성공 뒤에는 완료된 테이블을 확인하고 남은 문장만 적용한다.
-- 적용 후 prod(ddl-auto: validate)로 기동하고 check-schema-indexes.sh로 인덱스를 검사한다.
-- 거점 6개 데이터는 HubSeedRunner가 테이블이 비어 있을 때 앱 기동 중 등록한다.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

CREATE TABLE `hubs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lat` double NOT NULL,
  `lng` double NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('CAMPUS','STATION','TERMINAL','SCHOOL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked` bit(1) NOT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_o2mlirhldriil2y7krapq4frt` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
