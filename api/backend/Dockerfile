# 초경량 JRE 17 런타임 환경 (빌드는 CI에서 완료된 JAR 파일 복사)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# GitHub Actions에서 빌드된 Spring Boot JAR 파일 복사
COPY build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
EXPOSE 8080

# 프리티어(1GB RAM) 메모리 폭주 방지를 위해 최대 힙 메모리를 350MB로 제한
ENTRYPOINT ["java", "-Xms128m", "-Xmx350m", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
