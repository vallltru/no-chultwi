# API

No Chultwi의 비즈니스 로직과 API를 담당하는 Spring Boot 기반 백엔드 REST API 서버입니다.

## 역할

- [ ] 회원 및 기본 인증 (User & Auth)
  - 학생/관리자 회원가입 및 로그인 API
  - H2 인메모리 데이터베이스 연동 및 기본 SecurityConfig 구축
  - JJWT 기반 Access Token 발급/검증 구조
- [x] 강의 및 출석 관리 (Course & Schedule)
  - 키오스크용 활성 강의/차시 조회 및 2단계 출결 확정 API
- [x] 얼굴 인식 기반 출석 자동 판정 (Attendance Processing)
  - FastAPI 사이드카(InsightFace ArcFace 512차원) 특징 벡터 추출 및 코사인 유사도 매칭 기반 출결 판정 API
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


## 실행 방법 (Getting Started)

### 1. 사전 준비 (Prerequisites)

- Java 17 (JDK 17.0.19 이상) 설치 필수

### 2. 프로젝트 빌드 (Build)

```bash
# Windows (PowerShell / CMD)
.\gradlew.bat clean build -x test

# Linux / macOS / EC2
./gradlew clean build -x test
```
