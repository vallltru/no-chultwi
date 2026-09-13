# API

No Chultwi의 비즈니스 로직과 API를 담당하는 Spring Boot 기반 백엔드 REST API 서버입니다.

## 역할

- [ ] 회원 및 기본 인증 (User & Auth)
  - 학생/관리자 회원가입 및 로그인 API
  - H2 인메모리 데이터베이스 연동 및 기본 SecurityConfig 구축
  - JJWT 기반 Access Token 발급/검증 구조
- [ ] 강의 및 출석 관리 (Course & Schedule)
  - 학번 기준 학생 맞춤형 주간 강의 및 출석 데이터 조회 API
- [ ] 얼굴 인식 기반 출석 자동 판정 (Attendance Processing)
  - 단말기(라즈베리 파이) 128차원 벡터 매칭 및 태깅 시각 기준 출석/지각/결석 자동 판정 API
- [ ] 출석 모니터링 및 관리자 기능 (Admin Reporting)
  - 주차별 출석 히스토리 조회 및 출석 상태 수동 변경 API

## 기술 스택 및 버전 (Tech Stack & Versions)

### Core & Framework

- Java: 17.0.19 (JDK 17)
- **Spring Boot: 4.1.1
- Spring Dependency Management: 1.1.7
- Build Tool: Gradle 8.x

### Database & ORM

- In-Memory DB (Dev): H2 Database 2.4.240
- Production DB: MySQL 8.0 (Connector/J 9.7.0)
- ORM Framework: Spring Data JPA 4.1.1 (Hibernate ORM 7.4.5.Final)

### Security & Authentication

- Security Framework: Spring Security 7.1.1
- JWT Library: JJWT 0.11.5 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)

### Libraries & Utilities

- Lombok: 1.18.46
- Spring Validation: Jakarta Validation 3.1.1
- DevTools: Spring Boot DevTools 4.1.1
- API Documentation: Spring REST Docs (`spring-restdocs-mockmvc`, `asciidoctor`)


## API 명세서 (API Documentation)
Spring REST Docs를 통해 Controller 단위 테스트 통과 시 자동으로 생성되는 API 문서입니다.
* **로컬 환경**: `http://localhost:8080/docs/index.html`
* **개발 서버**: `http://43.203.146.101:8080/docs/index.html`   
* **문서 생성 태스크**: `./gradlew asciidoctor copyDocument`

## 실행 방법 (Getting Started)

### 1. 사전 준비 (Prerequisites)

- Java 17 (JDK 17.0.19 이상) 설치 필수

### 2. 프로젝트 빌드 (Build)

```bash
# Windows (PowerShell / CMD)
.\gradlew.bat clean build -x test

# Linux / macOS / EC2
./gradlew clean build -x test