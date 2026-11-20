-- 기존 운영 DB에서 백엔드를 교체하기 전에 한 번 적용한다.
ALTER TABLE ride_requests ADD COLUMN preferred_size INT NULL;
ALTER TABLE match_groups
  ADD COLUMN accept_deadline DATETIME(6) NULL,
  ADD COLUMN pair_offer_key VARCHAR(48) NULL,
  ADD UNIQUE KEY uk_group_pair_offer (pair_offer_key),
  ADD KEY idx_group_offer_expiry (status, accept_deadline);
