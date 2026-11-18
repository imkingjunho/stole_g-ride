-- 기존 MySQL 데이터 보존. prod(validate) 재시작 전에 1회 적용한다. 재실행 가능.
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE match_members ADD COLUMN completed_at DATETIME(6) NULL',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='match_members' AND column_name='completed_at');
PREPARE member_completion FROM @ddl;
EXECUTE member_completion;
DEALLOCATE PREPARE member_completion;
