-- 2026-11-11: 358a3bb의 매칭 테이블이 이미 있는 DB 전용. 각 컬럼이 없는지 먼저 확인한다.
-- 신규 6개 테이블 DB/이미 업데이트된 DB에는 실행하지 않는다. 백업 후 1회 실행.
-- 기존 그룹의 경로/단독요금은 추측하여 채우지 않는다. 새 상세 조회는 새 그룹부터 지원한다.
ALTER TABLE match_groups
  ADD COLUMN route_json LONGTEXT NULL,
  ADD COLUMN hub_name VARCHAR(255) NULL,
  ADD COLUMN hub_lat DOUBLE NULL,
  ADD COLUMN hub_lng DOUBLE NULL,
  ADD COLUMN hub_type VARCHAR(255) NULL;
ALTER TABLE match_members
  ADD COLUMN dest_name VARCHAR(255) NULL,
  ADD COLUMN dest_lat DOUBLE NULL,
  ADD COLUMN dest_lng DOUBLE NULL,
  ADD COLUMN solo_distance INT NULL,
  ADD COLUMN solo_fare INT NULL,
  ADD COLUMN completed BIT(1) NULL;
