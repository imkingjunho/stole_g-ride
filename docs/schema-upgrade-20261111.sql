-- 2026-11-11: 기존 6개 테이블 DB용. match_groups/match_members가 둘 다 없을 때만 실행한다.
-- 백업 후 적용. 기존 8개 테이블 DB에는 snapshots 변경분을 사용한다.
SET NAMES utf8mb4;

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
