# 초경량 JRE 17 런타임 환경 (빌드는 CI에서 완료된 JAR 파일 복사)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# GitHub Actions에서 빌드된 Spring Boot JAR 파일 복사
COPY build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
