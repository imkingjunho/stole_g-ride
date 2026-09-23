-- 가치가(WE-Meet) 스키마 — docs/schema.sql (T3-2)
--
-- 어떻게 만들었나: local 프로파일(ddl-auto: update)로 기동한 로컬 MySQL 8.0 에서 DDL 만 뽑았다.
--   docker compose exec -T mysql sh -c 'mysqldump -ugachiga -pgachiga --no-data --skip-add-drop-table --skip-comments --compact gachiga'
-- AUTO_INCREMENT 현재값과 클라이언트 문자셋 지시문은 지웠다. 데이터는 없다.
--
-- 어디에 쓰나: 베타/운영 DB 에 이 파일을 먼저 적용한 뒤 prod 프로파일(ddl-auto: validate)로 기동한다 (PRD §7.3).
--   validate 는 컬럼·타입만 대조하고 UNIQUE·인덱스는 검사하지 않으므로, 제약은 이 파일이 유일한 근거다.
--
-- 언제 다시 뽑나: 누가 엔티티(@Entity)를 바꾸면 그 사람이 local 에서 기동해 update 로 반영한 뒤,
--   이승민이 같은 명령으로 다시 뽑아 이 파일을 갱신한다. 컬럼 삭제·이름 변경·타입 변경은 update 가
--   반영하지 못하므로 그때는 docker compose down -v 로 초기화하고 다시 뽑는다 (CLAUDE.md §1).
--
-- 추출 시점의 엔티티: ride_requests(ride) · users·reports(user) · chat_messages(realtime).
--   refresh_tokens(auth T1-4)·매칭 테이블(matching)·hubs(route)는 아직 엔티티가 없어 여기에도 없다.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

/*!50503 SET character_set_client = utf8mb4 */;
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
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!50503 SET character_set_client = utf8mb4 */;
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
  `hub_id` bigint NOT NULL,
  `max_detour_ratio` decimal(3,2) NOT NULL,
  `max_wait_min` int NOT NULL,
  `same_gender_only` bit(1) NOT NULL,
  `solo_distance` int NOT NULL,
  `solo_fare` int NOT NULL,
  `status` enum('WAITING','MATCHED','CONFIRMED','CANCELLED','EXPIRED','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `version` int NOT NULL,
  `group_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ride_requests_active_user` (`active_user_id`),
  KEY `idx_ride_requests_status_hub_depart` (`status`,`hub_id`,`depart_at`),
  KEY `idx_ride_requests_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!50503 SET character_set_client = utf8mb4 */;
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
