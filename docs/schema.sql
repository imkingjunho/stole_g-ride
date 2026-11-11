-- 가치가(WE-Meet) 스키마 — docs/schema.sql (T3-2)
-- 추출일: 2026-11-11, MySQL 8.0.46
-- 출처: 지정한 MySQL DB의 실제 mysqldump --no-data 결과.
-- 재추출 전 최신 앱을 별도 빈 검증 DB에 local(ddl-auto: update)로 기동한다.
-- 재현: bash deploy/scripts/export-schema.sh <MySQL 컨테이너> docs/schema.sql
-- 클라이언트 문자셋 지시문과 AUTO_INCREMENT 현재값만 제거했다. 행 데이터는 포함하지 않는다.
-- 새 빈 DB에 적용한 후 prod(ddl-auto: validate)로 기동하고 check-schema-indexes.sh로 인덱스를 확인한다.
-- 기존 DB에는 이 전체 파일을 재실행하지 않는다. 변경분 적용은 docs/deploy.md §2를 따른다.
-- 재추출에 기존 DB의 볼륨 삭제는 필요하지 않다. 별도 빈 DB를 만들고 local로 생성한다.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `type` enum('TEXT','QUICK','SYSTEM') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_messages_group_created` (`group_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `hubs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lat` double NOT NULL,
  `lng` double NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('CAMPUS','STATION','TERMINAL','SCHOOL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `match_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `closed_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `estimated` bit(1) NOT NULL,
  `hub_id` bigint DEFAULT NULL,
  `hub_lat` double DEFAULT NULL,
  `hub_lng` double DEFAULT NULL,
  `hub_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hub_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `route_json` longtext COLLATE utf8mb4_unicode_ci,
  `status` enum('PENDING','CONFIRMED','COMPLETED','DISSOLVED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_distance` int NOT NULL,
  `total_duration` int NOT NULL,
  `total_fare` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `match_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accepted` bit(1) NOT NULL,
  `boarding_order` int NOT NULL,
  `completed` bit(1) DEFAULT NULL,
  `dest_lat` double DEFAULT NULL,
  `dest_lng` double DEFAULT NULL,
  `dest_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detour_ratio` decimal(5,3) DEFAULT NULL,
  `dropoff_order` int NOT NULL,
  `group_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  `saving_amount` int NOT NULL,
  `share_amount` int NOT NULL,
  `shared_distance` int NOT NULL,
  `solo_distance` int DEFAULT NULL,
  `solo_fare` int DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlospuc7s3k556d5r7mouv1oec` (`group_id`,`user_id`)
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
CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `group_id` bigint NOT NULL,
  `reason` enum('NO_SHOW','RUDE','UNSAFE','PAYMENT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `reported_id` bigint NOT NULL,
  `reporter_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `ride_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `depart_at` datetime(6) NOT NULL,
  `dest_lat` double NOT NULL,
  `dest_lng` double NOT NULL,
  `dest_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `estimated` bit(1) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `hub_id` bigint NOT NULL,
  `max_detour_ratio` decimal(3,2) NOT NULL,
  `max_wait_min` int NOT NULL,
  `same_gender_only` bit(1) NOT NULL,
  `solo_distance` int NOT NULL,
  `solo_fare` int NOT NULL,
  `status` enum('WAITING','MATCHED','CONFIRMED','CANCELLED','EXPIRED','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ride_requests_active_user` (`active_user_id`),
  KEY `idx_ride_requests_status_hub_depart` (`status`,`hub_id`,`depart_at`),
  KEY `idx_ride_requests_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `department` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` enum('M','F') COLLATE utf8mb4_unicode_ci NOT NULL,
  `grade` int DEFAULT NULL,
  `nickname` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `report_count` int NOT NULL,
  `status` enum('ACTIVE','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `suspended_until` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
