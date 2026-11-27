# 백엔드

Spring Boot API는 단말기를 인증하고, 서울시립대 WISE 시간표를 바탕으로 현재 출석 차시를 조회합니다. 얼굴 인식과 출석 확정은 두 단계입니다. 인식 요청은 수강생 후보와 임시 토큰을 반환하고, 확인 요청이 성공하면 출석을 저장합니다. 요청·응답 형식은 [출석 API 명세](ATTENDANCE_API_SPEC.md)를 참고하세요.

학생·관리자 로그인, 관리자 출석 조회 및 수동 정정은 후속 개발 범위입니다.

## 사용 버전

| 구성 | 저장소 설정 |
| --- | --- |
| Java | JDK 17 |
| Spring Boot | 4.1.1 |
| Gradle | Wrapper 9.7.1 |
| 데이터베이스 | 로컬 H2 인메모리, 배포 MySQL 8.0 |

빌드 기준은 [Gradle 설정](backend/build.gradle)과 [Wrapper 설정](backend/gradle/wrapper/gradle-wrapper.properties)에서 확인할 수 있습니다. Gradle Wrapper를 사용하면 첫 실행에 지정된 Gradle 배포판을 내려받습니다.

## 로컬 실행과 테스트

저장소 루트에서 시작합니다.

**Windows PowerShell**

```powershell
Set-Location api/backend
.\gradlew.bat bootRun
```

**Linux / macOS**

```bash
cd api/backend
bash ./gradlew bootRun
```

기본 프로필은 `local`이며 API는 `http://localhost:8080`에서 실행됩니다. 로컬 H2 데이터는 서버를 종료하면 사라집니다.

| 작업 | Windows PowerShell | Linux / macOS |
| --- | --- | --- |
| 테스트 | `.\gradlew.bat test` | `bash ./gradlew test` |
| 테스트 포함 빌드 | `.\gradlew.bat build` | `bash ./gradlew build` |

테스트와 빌드 명령은 서버를 종료하거나 별도 터미널에서 `api/backend`로 이동해 실행합니다.

## 연동 설정

로컬에서 `bootRun`을 실행할 때는 실행 전에 셸 환경 변수를 설정합니다. 루트의 [`.env.example`](../.env.example)은 Docker Compose 배포용입니다.

| 변수 | 역할 |
| --- | --- |
| `SCHOOL_UOS_API_KEY` | WISE 시간표 조회용 API 키. 로컬에서는 [설정 예제](backend/src/main/resources/application-secret.properties.example)를 같은 디렉터리의 `application-secret.properties`로 복사해 키를 지정할 수도 있습니다. |
| `AI_FACE_ENABLED` | 기본값 `false`는 모의 인식 어댑터를 사용합니다. `true`는 실제 추론 서비스에 연결합니다. |
| `AI_FACE_SERVER_URL` | 백엔드에서 접근할 수 있는 추론 서비스 주소. 로컬에서 두 서비스를 각각 실행할 때 기본값은 `http://localhost:8000`입니다. |
| `AI_SIMILARITY_THRESHOLD` | 얼굴 벡터 매칭 임계값. 기본값은 `0.42`입니다. |
| `AI_INTERNAL_SECRET` | 추론 서비스에 내부 인증을 설정한 경우 양쪽에 같은 값으로 지정합니다. |

WISE의 조회 학년도와 학기 코드는 [공통 설정](backend/src/main/resources/application.properties)에 있습니다. 학기가 바뀌면 `school.uos.year`와 `school.uos.term`을 확인하세요. 실제 추론 서비스를 실행하려면 [AI 서비스 README](ai/README.md)를 참고하세요.

## CI와 EC2 배포

- **CI**는 `dev`, `main` 대상 PR에서 백엔드 Gradle 테스트와 AI 서비스 테스트를 실행합니다. [CI 워크플로우](../.github/workflows/ci.yml)
- **CD**는 `dev`에 배포 대상 변경이 푸시되거나 수동으로 실행될 때 Spring Boot JAR를 빌드합니다. Docker Hub에 백엔드 이미지를 `latest` 태그로 게시한 뒤, EC2에서 Docker Compose로 반영합니다. [배포 워크플로우](../.github/workflows/deploy.yml)
- **운영 설정**은 `prod` 프로필과 MySQL을 사용합니다. EC2의 Compose 환경 변수는 루트 `.env.example`을 기준으로 구성합니다. Compose가 MySQL 설정에서 Spring의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 구성합니다. `DOCKER_IMAGE`는 배포할 이미지, `CORS_ALLOWED_ORIGINS`는 허용할 브라우저 출처를 지정합니다. [Compose 설정](../docker-compose.yml)
- **배포 확인 범위**는 MySQL 헬스체크와 백엔드 컨테이너 시작까지입니다. 현재 워크플로우에는 백엔드 API 준비 상태를 확인하는 단계가 없습니다. AI 서비스는 현재 Compose 구성 외부에서 별도로 실행하고, 실제 인식을 사용할 때 백엔드 컨테이너에서 접근 가능한 주소를 지정합니다.
