# 요청 API 부하 테스트 (T3-1)

JWT 로그인 계정 200개로 `생성 → 내 요청 조회 3회 → 취소`를 반복한다. 각 API의 P95 목표는 500ms 미만이다. 결과 해석과 실제 측정값은 `docs/performance.md`에 기록한다.

## 측정 환경

- Java 17, k6, 이미 내려받은 백엔드 Gradle 의존성을 사용한다. 별도 라이브러리는 추가하지 않는다.
- **측정용 MySQL·Redis·백엔드를 개발 서버와 분리한다.** 빈 로컬 DB로 시작하고 백엔드를 먼저 기동하여 스키마와 거점을 생성한다. 기본 URL은 `http://127.0.0.1:18081`이다. 스크립트는 로컬 HTTP 주소만 허용하며 리다이렉트도 따라가지 않는다.
- 현재 요청 API 성능을 측정할 때 백엔드의 `KAKAO_REST_API_KEY`를 비워 `EstimatedRouteProvider` fallback을 사용한다. 실제 카카오 API를 부하 테스트하지 않는다. `ROUTE_MODE=fallback`은 결과에 남기는 환경 설명이며, 서버 설정을 변경하거나 외부 호출을 차단하는 옵션은 아니다. 별도 경로 mock 서버를 구성한 경우에만 `ROUTE_MODE=mock`으로 기록한다.
- 매칭 엔진을 활성화하면 조회 도중 요청이 매칭되어 취소 시나리오가 달라질 수 있다. 이 측정은 대기열 요청 API 대상이다. 실제 매칭·그룹 수락·WebSocket·외부 경로 지연은 포함하지 않는다.
- 측정 중 노트북 덮개를 열어 두고 수면을 피한다. macOS에서는 `caffeinate -i k6 run ...`으로 유휴 수면을 막을 수 있지만 덮개 닫힘 수면까지 막지는 못한다. 긴 실행 공백이 있으면 결과를 정상 기준으로 쓰지 않는다.
- SQL 상세 로그를 끄고, 이 테스트가 사용하는 DB/Redis에는 다른 애플리케이션 인스턴스를 연결하지 않는다. 큐 재구축과 사용자 시드가 공유 개발 환경에 영향을 줄 수 있다.

## 인증 가능한 사용자 시드

시드는 `load011@jnu.ac.kr`부터 VU별 이메일을 만든다. PK는 DB가 생성하므로 사용자 ID 11~210이라는 가정이 없다. 재실행 시 같은 이메일의 테스트 계정 비밀번호와 정지 상태만 초기화한다. 기존 진행 요청은 삭제하지 않는다.

비밀번호는 실행 시 생성하고 환경변수로만 전달한다. 스크립트는 Java 17 소스 실행과 기존 Spring Security BCrypt를 이용해 **현재 users 스키마**의 SQL을 출력한다. 생성 SQL에는 해시가 있으므로 커밋하지 않는다. 예시는 이미 준비된 격리 MySQL 컨테이너에 적용한다.

```bash
# 저장소 루트, bash 기준. macOS에서 Java 17 선택 예시
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export VUS=200 FIRST_USER_NUMBER=11
export LOAD_TEST_PASSWORD="$(openssl rand -hex 24)"

umask 077
load_seed_file="$(mktemp)"
bash load/seed-users.sh > "$load_seed_file"

# 실제 측정용 컨테이너 이름을 지정한다. 개발용 DB에는 적용하지 않는다.
export LOAD_MYSQL_CONTAINER=gachiga-load-mysql
docker exec -i "$LOAD_MYSQL_CONTAINER" sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < "$load_seed_file"
rm -f "$load_seed_file"
```

- `VUS`: 1~1000, 기본 200. `FIRST_USER_NUMBER`: 1~999000, 기본 11. 생성과 실행에 같은 값을 사용한다.
- `LOAD_TEST_PASSWORD`: BCrypt 제한에 따라 72 UTF-8 바이트 이하. k6 로그인에 같은 값이 필요하다.
- 기존 해시가 있으면 `LOAD_TEST_PASSWORD_HASH`를 사용할 수 있다. 비밀번호도 함께 주면 해시와 일치하는지 검증한다. 해시만으로는 k6 로그인을 할 수 없다.
- 기본 jar 위치는 `${GRADLE_USER_HOME:-$HOME/.gradle}`의 Spring Security 6.2.4 캐시다. 캐시가 다른 경우 `SPRING_SECURITY_CRYPTO_JAR`를 지정한다. 스크립트 자체는 다운로드하지 않는다.
- 예전 `load/seed-users.sql`은 유효하지 않은 비밀번호를 넣던 파일이다. 오실행을 막기 위해 지금은 안내 오류만 발생시킨다.

## 실행

비밀번호를 생성한 **같은 셸**에서 저장소 루트를 기준으로 실행한다. `-e` 인자에 비밀번호를 직접 쓰지 않는다.

```bash
# 우선 2명으로 연결·인증·응답 형태 확인
k6 run -e BASE_URL=http://127.0.0.1:18081 -e VUS=2 \
  -e RAMP_UP=1s -e HOLD=3s -e RAMP_DOWN=1s \
  -e RESULT_FILE=load/k6/result-smoke.json load/k6/ride-requests.js

# 정식 측정: 30초 증가 → 200명 60초 유지 → 10초 감소
k6 run -e BASE_URL=http://127.0.0.1:18081 \
  -e ROUTE_MODE=fallback load/k6/ride-requests.js
```

`setup()`에서 모든 계정에 `POST /api/auth/login`을 실행하고 활성 사용자·토큰·진행 요청 없음 여부를 확인한다. `GET /api/hubs`로 거점 ID/좌표를 가져오며, 계정 누락·잔여 요청·빈 거점·잘못된 응답이면 측정 전에 중단한다. 로그인은 setup 통계이며 요청 API 지연 통계에 섞지 않는다. 각 VU는 자기 계정의 Bearer 토큰만 사용한다.

실행 옵션은 `BASE_URL`, `VUS`, `FIRST_USER_NUMBER`, `RAMP_UP`, `HOLD`, `RAMP_DOWN`, `RESULT_FILE`, `ROUTE_MODE`다. 시간은 `30s`, `2m` 같은 양의 정수로 지정하며 토큰 수명을 고려하여 전체 측정은 20분 이하로 제한한다.

## 결과와 정리

- 요약은 콘솔과 `load/k6/last-result.json`에 저장된다. JSON에는 설정·성공 생성/취소 건수·409 건수·검사 성공률·API별 지연·threshold 결과가 있으며, 비밀번호와 JWT는 포함하지 않는다.
- `create_ms`, `me_ms`, `cancel_ms`는 성공 응답만 집계한다. 409는 별도 실패 조건이며 API별 P95뿐 아니라 오류율·검사 성공률·실제 생성/취소 건수를 함께 확인한다. 로그인/사전 조회는 전체 HTTP 건수에 포함되지만 `failed_rate`는 `phase:load`만 집계한다.
- 생성 성공 응답의 요청 ID만 `finally`에서 취소한다. 생성 실패 시 이전 요청을 임의로 취소하지 않는다. 중간 조회가 다른 ID를 반환해도 그 ID를 삭제하지 않는다.
- 정상 종료 후 `create_ok`와 `cancel_ok`가 같은지, 활성 테스트 요청과 Redis 큐가 비었는지 확인한다. 강제 종료·취소 실패가 있으면 잔여 요청을 조사하고 격리 환경을 재생성한다. 개발 DB에 광범위한 `DELETE`나 `FLUSHALL`을 실행하지 않는다.
- 측정이 끝나면 격리 컨테이너/볼륨을 정리하고 `unset LOAD_TEST_PASSWORD LOAD_TEST_PASSWORD_HASH`로 셸의 테스트 비밀번호를 지운다. 실서비스 환경의 처리량을 검증한 것으로 해석하지 않는다.

## 하네스 자체 검증

```bash
node --test load/k6/ride-requests.test.cjs
```

네트워크 없이 전체 계정 사전 확인, 동적 거점, JWT 헤더, 실패 시 기존 요청 보존, 현재 회차 요청 정리, 로컬 주소 제한을 검증한다. 실제 API·DB 통합 검증은 위 smoke/200 VU 실행으로 수행한다.
