# 1단계: 빌드 환경 (경량 Alpine + JDK 17)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# 윈도우 줄바꿈(CRLF) 호환을 위한 dos2unix 설치
RUN apk add --no-cache dos2unix

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN dos2unix ./gradlew && chmod +x ./gradlew

# 소스 코드 복사 및 bootJar 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 환경 (초경량 JRE 17, 약 150MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 산출물 복사
COPY --from=builder /workspace/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
