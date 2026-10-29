-- 기존 고정 ID/무효 비밀번호 시드는 사용하지 않는다.
-- LOAD_TEST_PASSWORD 환경변수를 지정하고 bash load/seed-users.sh로 SQL을 생성한다.
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'Generate a local JWT fixture with bash load/seed-users.sh; see load/README.md';
