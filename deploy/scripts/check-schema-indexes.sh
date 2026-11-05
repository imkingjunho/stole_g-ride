#!/usr/bin/env bash
# Hibernate validate가 검사하지 않는 PK·UNIQUE·조회 인덱스를 별도로 대조한다.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "사용법: bash deploy/scripts/check-schema-indexes.sh <MySQL 컨테이너 이름 또는 ID> [DB 이름]" >&2
  exit 2
fi

container=$1
database=${2:-}
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

# 자동 생성된 UNIQUE 이름 대신 종류와 컬럼 순서로 비교한다. 접두부 인덱스는 동일하게 취급하지 않는다.
docker exec -i -e SCHEMA_DATABASE="$database" "$container" sh -eu -c '
  : "${MYSQL_DATABASE:?}" "${MYSQL_USER:?}" "${MYSQL_PASSWORD:?}"
  database=${SCHEMA_DATABASE:-$MYSQL_DATABASE}
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --batch --skip-column-names \
    --database="$database"
' <<'SQL' | LC_ALL=C sort > "$scratch/actual.txt"
SELECT CONCAT(TABLE_NAME, '|',
  CASE WHEN INDEX_NAME = 'PRIMARY' THEN 'PRIMARY'
       WHEN NON_UNIQUE = 0 THEN 'UNIQUE' ELSE 'INDEX' END, '|',
  GROUP_CONCAT(CONCAT(COLUMN_NAME, IF(SUB_PART IS NULL, '', CONCAT('(', SUB_PART, ')')))
    ORDER BY SEQ_IN_INDEX SEPARATOR ','))
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE;
SQL

LC_ALL=C sort > "$scratch/expected.txt" <<'EXPECTED'
chat_messages|PRIMARY|id
chat_messages|INDEX|group_id,created_at
hubs|PRIMARY|id
refresh_tokens|PRIMARY|id
refresh_tokens|UNIQUE|token_hash
refresh_tokens|INDEX|user_id
reports|PRIMARY|id
ride_requests|PRIMARY|id
ride_requests|UNIQUE|active_user_id
ride_requests|INDEX|status,hub_id,depart_at
ride_requests|INDEX|user_id,status
users|PRIMARY|id
users|UNIQUE|email
users|UNIQUE|nickname
EXPECTED

if ! diff -u "$scratch/expected.txt" "$scratch/actual.txt"; then
  echo "인덱스 불일치: 엔티티·docs/schema.sql과 실제 DB를 확인한다. 이 검사는 DB를 변경하지 않는다." >&2
  exit 1
fi
echo "인덱스 확인 완료: 6개 테이블, PK 6개·UNIQUE 4개·조회 인덱스 4개. 컬럼·타입은 prod validate로 확인한다."
