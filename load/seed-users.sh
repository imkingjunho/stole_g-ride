#!/usr/bin/env bash
set -euo pipefail

# Java 17과 기존 Gradle 캐시만 사용한다. 다운로드·DB 접속은 하지 않는다.
load_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
load_java="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
load_gradle_cache="${GRADLE_USER_HOME:-${HOME}/.gradle}/caches/modules-2/files-2.1"
load_crypto_jar="${SPRING_SECURITY_CRYPTO_JAR:-}"
if [[ -z "$load_crypto_jar" ]]; then
  for load_candidate in "$load_gradle_cache"/org.springframework.security/spring-security-crypto/6.2.4/*/spring-security-crypto-6.2.4.jar; do
    if [[ -f "$load_candidate" ]]; then load_crypto_jar="$load_candidate"; break; fi
  done
fi
if [[ ! -f "$load_crypto_jar" ]]; then
  echo 'spring-security-crypto 6.2.4 jar가 없습니다. 백엔드 의존성을 준비하거나 SPRING_SECURITY_CRYPTO_JAR를 지정하세요.' >&2
  exit 1
fi
exec "$load_java" --class-path "$load_crypto_jar" "$load_dir/SeedUsers.java"
