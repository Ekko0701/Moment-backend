# ---- Build stage ----
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app

# 의존성 캐시 레이어: 빌드 스크립트만 먼저 복사해 의존성 해석을 캐시한다
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true

COPY src ./src
RUN gradle bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 비루트 사용자로 실행 (컨테이너 보안 기본기)
RUN groupadd --system moment && useradd --system --gid moment moment
USER moment

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 운영 기동: 프로필/시크릿은 전부 런타임 환경 변수로 주입한다
#   SPRING_PROFILES_ACTIVE=prod
#   MOMENT_DB_URL / MOMENT_DB_USERNAME / MOMENT_DB_PASSWORD / MOMENT_JWT_SECRET
ENTRYPOINT ["java", "-jar", "app.jar"]
