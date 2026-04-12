# ─────────────────────────────────────────────
# Stage 1 : Build
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 의존성 캐시를 위해 Gradle 래퍼 & 설정 파일을 먼저 복사
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# 의존성만 먼저 다운로드 (소스 변경 시 캐시 재사용)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 전체 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ─────────────────────────────────────────────
# Stage 2 : Run
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 보안: 전용 비루트 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 빌드 결과물만 복사
COPY --from=builder /app/build/libs/finalproject-0.0.1-SNAPSHOT.jar app.jar

# 소유권 변경 후 비루트 사용자로 실행
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

