# syntax=docker/dockerfile:1
# 가치가 백엔드 이미지 (T3-3). 저장소 루트에서:  docker compose -f docker-compose.prod.yml build backend
#
# 1단계: 빌드. 테스트는 CI 가 이미 돌렸으므로 여기서는 jar 만 만든다 (-x test).
# Gradle 캐시는 BuildKit 캐시 마운트에 둔다 — 소스가 바뀌어도 의존성을 다시 받지 않는다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar -x test -q

# 2단계: 실행. JRE 만 들어간 이미지. bash 가 있어야 아래 healthcheck 가 돈다
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin gachiga
USER gachiga
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
# MaxRAMPercentage 는 컨테이너 메모리 한도 기준이다 — compose 의 deploy.resources.limits.memory 가 있어야 의미가 있다
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
# actuator 가 없어서 포트가 열렸는지만 본다. /v3/api-docs 는 prod 에서 꺼져 있다
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
