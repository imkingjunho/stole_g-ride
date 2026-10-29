#!/usr/bin/env bash
# 최신 앱을 local(ddl-auto=update)로 기동한 별도 검증 DB에서 DDL만 추출한다.
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "사용법: bash deploy/scripts/export-schema.sh <MySQL 컨테이너 이름 또는 ID> <출력 SQL 경로> [DB 이름]" >&2
  echo "컨테이너의 MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD 환경변수를 사용한다." >&2
  exit 2
fi

container=$1
output=$2
database=${3:-}
output_dir=$(dirname -- "$output")
[[ -d "$output_dir" ]] || { echo "출력 폴더가 없다: $output_dir" >&2; exit 2; }
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

mysql_version=$(docker exec -e SCHEMA_DATABASE="$database" "$container" sh -eu -c '
  : "${MYSQL_DATABASE:?}" "${MYSQL_USER:?}" "${MYSQL_PASSWORD:?}"
  database=${SCHEMA_DATABASE:-$MYSQL_DATABASE}
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --batch --skip-column-names \
    --database="$database" --execute="SELECT VERSION()"
')
docker exec -e SCHEMA_DATABASE="$database" "$container" sh -eu -c '
  : "${MYSQL_DATABASE:?}" "${MYSQL_USER:?}" "${MYSQL_PASSWORD:?}"
  database=${SCHEMA_DATABASE:-$MYSQL_DATABASE}
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --user="$MYSQL_USER" \
    --no-data --skip-add-drop-table --skip-comments --compact --skip-lock-tables \
    --no-tablespaces --set-gtid-purged=OFF -- "$database"
' > "$scratch/raw.sql"

# 클라이언트 문자셋 지시문과 데이터에 따라 달라지는 AUTO_INCREMENT 현재값만 제거한다.
sed -E '/^\/\*!(4010[1-3]|50503)/d; /^$/d; s/ AUTO_INCREMENT=[0-9]+//' \
  "$scratch/raw.sql" > "$scratch/body.sql"
if ! grep -q '^CREATE TABLE ' "$scratch/body.sql"; then
  echo "추출한 테이블이 없어 출력 파일을 변경하지 않는다." >&2
  exit 1
fi

{
  printf '%s\n' '-- 가치가(WE-Meet) 스키마 — docs/schema.sql (T3-2)'
  printf '%s\n' "-- 추출일: $(TZ=Asia/Seoul date +%F), MySQL $mysql_version"
  printf '%s\n' '-- 출처: 지정한 MySQL DB의 실제 mysqldump --no-data 결과.'
  printf '%s\n' '-- 재추출 전 최신 앱을 별도 빈 검증 DB에 local(ddl-auto: update)로 기동한다.'
  printf '%s\n' '-- 재현: bash deploy/scripts/export-schema.sh <MySQL 컨테이너> docs/schema.sql'
  printf '%s\n' '-- 클라이언트 문자셋 지시문과 AUTO_INCREMENT 현재값만 제거했다. 행 데이터는 포함하지 않는다.'
  printf '%s\n' '-- 새 빈 DB에 적용한 후 prod(ddl-auto: validate)로 기동하고 check-schema-indexes.sh로 인덱스를 확인한다.'
  printf '%s\n' '-- 기존 DB에는 이 전체 파일을 재실행하지 않는다. 변경분 적용은 docs/deploy.md §2를 따른다.'
  printf '%s\n' '-- 재추출에 기존 DB의 볼륨 삭제는 필요하지 않다. 별도 빈 DB를 만들고 local로 생성한다.'
  printf '\n%s\n%s\n\n' 'SET NAMES utf8mb4;' "SET time_zone = '+09:00';"
  cat "$scratch/body.sql"
} > "$scratch/schema.sql"

cp "$scratch/schema.sql" "$output"
echo "DDL 추출 완료: $output (MySQL $mysql_version)" >&2
