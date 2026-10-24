# 얼굴 인식 출석 API 연동 명세

> 백엔드 전달용 초안 · 2026-10-22 · 참고: 「캡스톤 설계계획서」(2026-09-16), 이 프로젝트의 「Set up attendance screen flow」 대화, 현재 저장소
>
> **상태 표기**: `확정`은 설계 계획서, 이전 대화 또는 이번 대화에서 명시된 요구, `제안`은 연동을 위해 이 문서에서 정한 계약, `확인 필요`는 실제 운영값이나 구현 세부 결정이 필요한 사항이다. 제안과 확인 필요 항목은 기존 백엔드 구현 사항이 아니다.

## 1. 범위와 근거

- **확정 · 설계 계획서**: 라즈베리파이 웹 키오스크에서 얼굴을 촬영하고, 수업 정보와 함께 서버로 전송한다. 서버는 해당 수업의 등록 학생과 대조한다. 관리자는 학생별 출결과 인증 시각, 상태별 인원을 조회하고 잘못된 상태를 정정한다. 미인증은 출석 마감 이후 결석으로 구분한다.
- **확정 · 이전 프로젝트 대화**: 사용자 화면 흐름은 `홈 → 얼굴 인식 → "000님 출석할까요?" 확인 → 완료 → 홈`이다. 현재 원형 얼굴 인식 영역의 클릭과 학생 정보는 API 연동 전 임시 동작/데이터이며, 완료 화면의 5초 카운트다운은 홈 복귀용이다.
- **확정 · 현재 저장소**: `/api/v1/users` 아래 회원가입, 로그인, 아이디 찾기, 비밀번호 재설정만 구현되어 있다. 키오스크의 출석 호출과 관리자 출결 API는 새로 합의하고 구현해야 한다.
- **확정 · 이번 대화**: 얼굴 식별은 백엔드가 **이미지**를 받아 수행한다. 얼굴 식별만으로 기록하지 않고 학생이 확인 화면의 **전자출결** 버튼을 눌러야 기록한다. 출석/지각/마감 기준은 차시별 수업 일정에 저장한다. 키오스크는 단말기용 자격증명으로 인증한다. 이번 전달 범위는 키오스크와 관리자 API를 우선한다. 계획서의 '인식 성공 즉시 출석 기록' 설명보다 이번 대화의 2단계 확정 흐름을 우선한다.
- 수업, 수강생, 얼굴 특징 정보는 시제품에서 DB에 사전 등록한다. 수업/학생/얼굴 등록 화면과 종료 시 재인증은 **후속 범위**로 둔다. 후속 범위로의 구분은 설계 계획서에 명시되어 있다.

## 2. 결정 사항과 남은 입력값

| 항목 | 결정 |
| --- | --- | --- |
| 얼굴 매칭 | 서버에서 촬영 이미지의 얼굴을 식별한다. 저장소 README의 라즈베리파이 벡터 매칭 문구는 이번 계약에 맞게 수정이 필요하다. |
| 출석 확정 | `얼굴 식별 → 후보 확인 → 버튼 클릭 → 서버 기록`의 2단계 계약이다. `제가 아니에요`를 누르면 기록하지 않는다. |
| 시간 규칙 | 각 차시에 `openAt`, `lateFrom`, `closeAt`을 저장해 서버 시각으로 판정한다. 실제 시각값은 차시 데이터를 준비할 때 입력해야 한다. |
| 키오스크 인증 | 라즈베리파이의 **브라우저 밖** 단말기 프로그램이 자격증명을 보관하고 API 요청에 첨부한다. 관리자 API는 기존 로그인에서 발급한 관리자 역할 JWT 활용을 제안한다. |
| 범위 | 키오스크와 관리자 화면의 조회/출석/정정을 우선한다. 학생 개인의 주간 시간표/히스토리는 이번 명세에서 제외한다. |

얼굴 모델/유사도 임계값, 실제 차시별 시각, 단말기 자격증명 발급값은 아직 주어지지 않았다. 이들은 데이터·설정값이며 아래 API의 필드와 판정 경계는 먼저 정의할 수 있다.

## 3. 기존 API와 구현 차이

| 현재 구현 | 요청/응답 | 이번 출석 연동에서의 처리 |
| --- | --- | --- |
| `POST /api/v1/users/signup` | `loginId`, `password`, `name`, `email`, `studentNumber`(숫자), `role` → Bearer 토큰 | 학생/관리자 계정 발급 방식은 별도 합의 필요 |
| `POST /api/v1/users/login` | `loginId`, `password` → `tokenType`, `accessToken` | 관리자 로그인에 재사용 제안. 관리자 역할 검사는 새로 구현 필요 |
| `POST /api/v1/users/findid` | `email`, `studentNumber` → `loginId` | 출석 흐름과 직접 관계 없음 |
| `POST /api/v1/users/reset-password` | `loginId`, `email`, `studentNumber`, `newPassword` → `message` | 출석 흐름과 직접 관계 없음 |

현재 공개 회원가입 요청의 `role` 값으로 `ROLE_ADMIN`을 지정할 수 있다. 관리자 출결 정정 API를 공개하기 전에 이 경로에서 관리자 역할을 직접 발급받지 못하게 바꾸고 관리자 API에 `ROLE_ADMIN` 권한 검사를 추가해야 한다. 현재 사용자 엔티티에는 학과와 입학연도 필드가 없으므로 키오스크 후보 카드에 이 정보를 표시하려면 데이터 소스를 추가해야 한다. 학번은 식별자이므로 선행 0 보존 가능성을 고려해 새 출결 API에서 문자열 사용을 **제안**한다. 현재 계정 API의 숫자 타입과는 별도 호환 조정이 필요하다.

## 4. 공통 연동 원칙 (제안)

- Base path: `/api/v1`. JSON 필드는 `camelCase`, 시각은 타임존 오프셋이 포함된 ISO 8601 문자열(예: `2026-10-22T09:05:30+09:00`)로 주고받는다. 시제품의 차시 타임존은 `Asia/Seoul`로 둔다.
- 출석 판정과 저장 시각은 **서버 시계**를 기준으로 한다. 단말 `capturedAt`은 진단용으로만 받는다. 응답의 `checkedInAt`을 완료 화면에 표시한다.
- 수업(`course`)과 실제 날짜/주차의 차시(`session`)를 별도 식별한다. 동일 학생의 중복 여부는 `학생 × 차시` 단위로 판정한다. 특정 강의의 다음 주 수업을 중복으로 처리하지 않기 위함이다. 과목코드 `30000` 같은 표시용 코드를 내부 `courseId`로 간주하지 않는다.
- 얼굴 원본 이미지는 식별 처리 후 삭제하고 출석 조회 응답에 포함하지 않는다. 브라우저도 전송 후 Blob과 미리보기를 해제한다. 서버에는 필요한 특징 정보와 출석 기록만 남긴다.
- 네트워크 오류 또는 타임아웃을 출석 성공으로 표시하지 않는다. 재요청 시 중복 기록이 생기지 않도록 서버의 원자적 중복 판정과 요청 멱등성 처리가 필요하다.
- 관리자 검색/필터/주기적 새로고침은 조회 API만 반복 호출한다. 실시간 소켓은 기본 범위에 포함하지 않는다.
- 아래 경로명·열거형·예시는 **제안 계약**이다. 백엔드 담당자와 이 이름 그대로 합의하면 프론트엔드에서 바로 사용할 수 있다. 예시 ID와 시각은 가상 데이터이며 출석 기준값이 아니다.

## 5. 데이터와 판정 규칙 (제안 계약)

| 필드 | 의미 |
| --- | --- |
| `courseId` | 사전 등록된 강의의 내부 ID. 키오스크 설정에 저장한다. |
| `sessionId` | 날짜/주차/차시를 구분하는 ID. 같은 강의에 차시가 여러 개 존재한다. |
| `studentId` | 학생의 내부 ID. 학번(`studentNumber`)과 분리한다. |
| `openAt`, `lateFrom`, `closeAt` | 차시별 출석 시작·지각 시작·마감 시각. `openAt < lateFrom < closeAt`. |
| `checkedInAt` | 얼굴 식별 시각이 아니라 **전자출결 확정 요청을 서버가 처리한 시각**. 얼굴 인증 기록이 없으면 `null`. |
| `status` | `PRESENT`(출석), `LATE`(지각), `PENDING`(미인증), `ABSENT`(결석). |

- `openAt ≤ checkedInAt < lateFrom`이면 `PRESENT`, `lateFrom ≤ checkedInAt < closeAt`이면 `LATE`. `openAt` 이전과 `closeAt` 이상은 새 출석을 받지 않는다. 기준 시각의 포함 여부까지 이 규칙으로 고정한다.
- 등록 수강생에게 기록이 없으면 `closeAt` 전에는 `PENDING`, `closeAt` 이후에는 `ABSENT`로 조회된다. 결석을 실제 행으로 확정하는 배치/트랜잭션 방식은 백엔드 내부 선택이다. 관리자 조회 결과는 마감과 동시에 같은 규칙을 충족해야 한다.
- 한 학생에게 한 차시의 유효 출석은 최대 1개다. DB의 `sessionId × studentId` 유일성 또는 동등한 원자적 처리로 동시 요청도 막는다. 재인증은 최초 `checkedInAt`과 상태를 바꾸지 않는다.
- 수업에 등록되지 않은 얼굴, 식별 기준 미달, 여러 얼굴로 결과가 불명확한 경우 **출석을 만들지 않는다**. 키오스크에는 학생 신원을 노출하지 않는 재시도 결과만 반환한다. 여러 얼굴이 보일 때의 기본 대상은 화면 중앙에 가장 가까운 얼굴이다. 두 명 이상이면 촬영 자체를 막는 방식은 계획서의 선택 기능으로 둔다.
- 관리자가 수정한 `status`와 `updatedAt`은 별도로 보존한다. 수동 수정은 과거의 얼굴 `checkedInAt`을 만들어내거나 덮어쓰지 않는다. `source`는 얼굴 기록이면 `FACE`, 관리자 정정이면 `ADMIN`, 기록이 없어 계산된 `PENDING`/`ABSENT`이면 `AUTO`다. 관리자 정정 상태가 자동 판정보다 우선하며, 키오스크 재시도로 덮어쓰지 않는다. 정정 이력에는 관리자 ID, 이전/새 상태, 사유, 수정 시각을 남긴다.

## 6. API 목록

| 메서드·경로 | 호출 주체 | 목적 |
| --- | --- | --- |
| `GET /api/v1/kiosk/courses/{courseId}/current-session` | 단말기 | 홈 화면의 강의 정보와 출석 가능한 차시 조회 |
| `POST /api/v1/kiosk/recognitions` | 단말기 | 사진으로 수강생 식별. **출석 기록 없음** |
| `POST /api/v1/kiosk/check-ins` | 단말기 | 확인 버튼을 누른 학생의 출석 확정 |
| `GET /api/v1/admin/courses` | 관리자 | 관리자 화면 강의 목록 |
| `GET /api/v1/admin/courses/{courseId}/sessions` | 관리자 | 날짜별/주차별 차시 목록 |
| `GET /api/v1/admin/sessions/{sessionId}/attendances` | 관리자 | 상태 집계와 학생별 출결 목록, 검색/필터 |
| `PATCH /api/v1/admin/sessions/{sessionId}/attendances/{studentId}` | 관리자 | 출결 상태 정정 |

### 6.1 키오스크 인증 및 호출 흐름

- **제안**: 라즈베리파이의 별도 로컬 프로그램이 `deviceId`와 난수형 단말기 비밀키를 보관한다. 브라우저 React 코드와 빌드 산출물에는 비밀키를 넣지 않는다. 브라우저의 사진/확인 요청을 로컬 프로그램이 받아 백엔드로 중계하면서 `X-Device-Id`와 `X-Device-Key` 헤더를 붙인다. 로컬 프로그램은 루프백 주소에서만 요청을 받고 허용된 화면 출처만 받는다.
- 백엔드는 비밀키를 원문이 아닌 검증 가능한 형태로 저장하고, `deviceId`가 요청의 `courseId`에 사용 권한이 있는지 검사한다. `check-ins`에서는 임시 토큰에 결합된 차시/강의와 요청 단말기의 권한을 다시 검사한다. 자격증명 누락/실패는 `401`, 다른 강의 접근은 `403`. 단말기 등록과 비밀키 발급은 시제품 DB 사전 설정으로 처리한다.
- **관리자 인증 제안**: 기존 로그인 API의 `Authorization: Bearer <accessToken>`을 사용하되 `ROLE_ADMIN`을 필수로 검사한다. 현재 로그인은 역할을 담은 JWT를 발급하지만 관리자 전용 경로 검사는 아직 없다. 단말기 비밀키로 관리자 경로를 호출할 수 없고 관리자 JWT로 단말기 경로를 호출할 수 없다.
- 배포된 키오스크와 서버 사이에 이미지를 전송하므로 HTTPS를 사용한다. `X-Device-Key`, 얼굴 이미지, 인식용 임시 토큰을 로그에 기록하지 않는다.

### 6.2 `GET /api/v1/kiosk/courses/{courseId}/current-session`

사전 등록된 `courseId`로 홈 화면의 강의 정보를 가져온다. 인증 범위에 속한 강의의 현재 차시가 없으면 `session: null`을 반환하고 촬영 버튼을 비활성화한다. 같은 강의에서 동시에 유효한 차시는 하나여야 한다.

```json
{
  "course": { "courseId": "course-demo", "courseCode": "30000", "name": "창의공학기초설계(30)" },
  "serverTime": "2026-10-22T09:02:00+09:00",
  "session": {
    "sessionId": "session-demo-01",
    "week": 4,
    "sessionNumber": 1,
    "openAt": "2026-10-22T09:00:00+09:00",
    "lateFrom": "2026-10-22T09:10:00+09:00",
    "closeAt": "2026-10-22T09:30:00+09:00"
  }
}
```

`200` 정상, `401/403` 단말기 인증·강의 권한 실패, `404` 강의 없음. 위 시각은 형식 예시이며 실제 지각/마감 규칙으로 사용하지 않는다.

### 6.3 `POST /api/v1/kiosk/recognitions`

`Content-Type: multipart/form-data`. 키오스크가 얼굴을 한 번 촬영한 뒤 요청한다. 이 호출은 학생 후보를 찾을 뿐 출석 기록을 생성하거나 변경하지 않는다.

| 파트 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `courseId` | text | Y | 단말기에 설정된 강의 ID |
| `sessionId` | text | Y | 6.2에서 받은 차시 ID |
| `capturedAt` | text | Y | 촬영 시각. 기록 판정에는 사용하지 않음 |
| `image` | JPEG/PNG 파일 | Y | 한 장의 촬영 이미지. 최대 5 MiB 제안 |

식별 성공 `200`:

```json
{
  "result": "MATCHED",
  "recognitionToken": "opaque-short-lived-token",
  "expiresAt": "2026-10-22T09:04:00+09:00",
  "candidate": {
    "name": "홍길동",
    "department": "컴퓨터과학부",
    "admissionYear": 2026
  }
}
```

- `recognitionToken`은 서버가 발급한 **1회용 임시 토큰**이며 `studentId`, `sessionId`, `deviceId`와 결합한다. **유효기간 60초는 제안값**이다. 클라이언트는 토큰을 확인 화면에서만 메모리에 두고 재촬영/홈 복귀 시 폐기한다. 얼굴 이미지나 특징 벡터를 토큰/응답에 넣지 않는다. 후보 `name`은 현재 확인 화면의 본인 확인용이다. 완료 화면에는 이름을 보여 주지 않으며, 이후 표시한다면 계획서대로 일부를 가린다.
- 이미 출석한 학생은 `200`과 `{"result":"ALREADY_CHECKED_IN","status":"PRESENT","checkedInAt":"..."}`를 반환하며 토큰을 발급하지 않는다. 기존 상태가 `LATE`면 그대로 반환한다. 관리자가 `PRESENT`/`LATE`로 정정한 경우에도 이 결과를 반환하되 `source: "ADMIN"`, `checkedInAt: null`일 수 있다.
- 관리자가 `ABSENT`로 정정한 학생의 재인식은 `200`과 `{"result":"ATTENDANCE_LOCKED","message":"관리자에게 출결 상태를 문의해 주세요"}`를 반환하고 토큰을 발급하지 않는다. 관리자 정정을 키오스크가 되돌리지 않기 위한 제안 규칙이다.
- 미등록·미수강·불명확·얼굴 없음은 `200`과 `{"result":"RETRY","message":"얼굴을 확인하지 못했어요. 다시 시도해 주세요"}`를 반환하며 신원 정보를 넣지 않는다. 여러 얼굴이 보이면 화면 중앙에 가장 가까운 얼굴을 선택한다. 서버가 하나의 대상을 확실하게 고르지 못하면 `RETRY`를 반환한다. 여러 얼굴에서 촬영 차단은 후속 선택 기능이다.
- 인식 모델 장애는 `503` 오류이고 출석은 기록하지 않는다. 잘못된 파일은 `400/415`, 크기 초과는 `413`. 서버는 비교 후 원본 이미지와 임시 업로드 파일을 제거한다.

### 6.4 `POST /api/v1/kiosk/check-ins`

학생이 후보 정보를 보고 **전자출결**을 누를 때만 호출한다. `Content-Type: application/json`. `Idempotency-Key`(UUID)는 필수다. 같은 버튼 클릭의 네트워크 재시도에는 동일한 키와 본문을 유지한다.

```http
Idempotency-Key: 2c265719-d4c5-4aba-a69d-33844792f0b8
```

```json
{ "recognitionToken": "opaque-short-lived-token" }
```

신규 기록 `201`:

```json
{
  "result": "CHECKED_IN",
  "status": "PRESENT",
  "checkedInAt": "2026-10-22T09:03:15+09:00",
  "sessionId": "session-demo-01"
}
```

`LATE`이면 같은 구조에서 `status: "LATE"`를 반환한다. 이미 그 차시에 기록된 학생은 `200`과 `result: "ALREADY_CHECKED_IN"`, **최초** `status`/`checkedInAt`을 반환한다. 관리자 `PRESENT`/`LATE` 정정이 있으면 정정 상태를 반환하되 `checkedInAt`은 `null`일 수 있다. 관리자 `ABSENT` 정정이 있으면 새 기록을 만들지 않고 `409 ATTENDANCE_LOCKED`를 반환한다. 같은 멱등성 키와 본문의 재시도는 토큰 만료 후에도 최초 **성공** 응답의 HTTP 상태와 본문을 그대로 반환한다. 서버는 이 성공 결과를 차시 종료 후 최소 24시간 보관한다. 같은 키에 다른 본문을 보내면 `409 IDEMPOTENCY_KEY_REUSED`다. 토큰 만료·다른 단말기 사용·이미 사용된 토큰의 다른 요청·출석 기간 외 요청도 `409` 오류 코드로 구분한다. 출석 시간 판정은 사진 촬영 시각이 아닌 이 요청을 서버가 처리하는 시각을 기준으로 한다. `제가 아니에요`는 이 API를 호출하지 않는다.

### 6.5 관리자 강의/차시 조회

- `GET /api/v1/admin/courses` → `200` `[{"courseId":"course-demo","courseCode":"30000","name":"창의공학기초설계(30)"}]`.
- `GET /api/v1/admin/courses/{courseId}/sessions?from=2026-10-01&to=2026-10-31` → `200` `[{"sessionId":"session-demo-01","date":"2026-10-22","week":4,"sessionNumber":1,"openAt":"...","lateFrom":"...","closeAt":"..."}]`. 날짜 범위는 포함한다. 기본값은 현재 주간으로 제안한다.
- 사전 등록된 강의와 차시만 반환한다. 관리자가 담당 강의만 봐야 하는 정책이 있다면 서버가 계정-강의 권한을 적용한다. 시제품에는 관리자 계정별 담당 강의 정보가 아직 없다.

### 6.6 `GET /api/v1/admin/sessions/{sessionId}/attendances`

선택 쿼리: `query`(이름/학번 부분 검색), `status`(`PRESENT|LATE|PENDING|ABSENT`), `page`(0부터), `size`(기본 20, 최대 100). `summary`는 검색/필터 전 **전체 수강생** 기준이며 `items`만 필터링한다. `totalElements`는 검색/필터 후 인원이다. `sort` 기본값은 학번 오름차순으로 제안한다. 아래 예시는 `?size=1`로 첫 학생 한 명을 조회한 응답이다.

```json
{
  "sessionId": "session-demo-01",
  "serverTime": "2026-10-22T09:04:00+09:00",
  "summary": { "total": 30, "present": 1, "late": 0, "pending": 29, "absent": 0 },
  "items": [
    {
      "studentId": "student-demo-01",
      "name": "홍길동",
      "studentNumber": "2026920007",
      "status": "PRESENT",
      "checkedInAt": "2026-10-22T09:03:15+09:00",
      "source": "FACE",
      "updatedAt": null
    }
  ],
  "page": 0,
  "size": 1,
  "totalElements": 30
}
```

`summary.total = present + late + pending + absent`가 항상 성립해야 한다. 기록이 없는 학생도 `items`에 포함되어 마감 전 `PENDING`·마감 후 `ABSENT`로 조회된다. 관리자 화면은 이 API를 주기적으로 다시 호출한다.

### 6.7 `PATCH /api/v1/admin/sessions/{sessionId}/attendances/{studentId}`

잘못된 출결을 관리자가 수정한다. `ROLE_ADMIN` 필수. 수강생이 해당 차시에 속하지 않으면 `404`. `status`는 `PRESENT|LATE|ABSENT`만 받는다. `PENDING`은 기록이 없고 마감 전일 때의 조회 상태이므로 직접 저장하지 않는다. 비어 있지 않은 `reason`을 필수로 받고 감사 이력에 저장한다.

```json
{ "status": "PRESENT", "reason": "현장 확인 후 정정" }
```

`200`은 6.6의 학생 행과 같은 구조를 반환한다. 예를 들어 현장 확인으로 출석 처리했지만 얼굴 인증이 없었다면 `status: "PRESENT"`, `source: "ADMIN"`, `checkedInAt: null`, `updatedAt: "..."`가 된다. 얼굴 인증 기록이 있는 행을 수정해도 원래 `checkedInAt`은 보존하고 최종 상태·관리자 ID·수정 시각·사유를 이력에 남긴다. 정정 기록 조회 API는 이번 화면 범위에 없으며 DB 이력 보존만 요구한다.

## 7. 공통 오류 형식 (제안 계약)

```json
{
  "code": "RECOGNITION_EXPIRED",
  "message": "얼굴을 다시 인식해 주세요.",
  "requestId": "b69051a2-13f3-4f48-bc1d-65b5be654eea"
}
```

| HTTP | 코드 예시 | 화면 처리 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 입력/설정 확인 |
| `401` | `DEVICE_UNAUTHORIZED`, `UNAUTHORIZED` | 단말기 설정 또는 관리자 재로그인 |
| `403` | `COURSE_FORBIDDEN`, `ADMIN_REQUIRED` | 접근 불가 안내 |
| `404` | `COURSE_NOT_FOUND`, `SESSION_NOT_FOUND`, `STUDENT_NOT_FOUND` | 운영 설정 확인 |
| `409` | `SESSION_NOT_OPEN`, `SESSION_CLOSED`, `RECOGNITION_EXPIRED`, `RECOGNITION_CONSUMED`, `RECOGNITION_DEVICE_MISMATCH`, `IDEMPOTENCY_KEY_REUSED`, `ATTENDANCE_LOCKED` | 현재 출석 불가·재촬영 또는 관리자 문의 안내 |
| `413/415` | `IMAGE_TOO_LARGE`, `UNSUPPORTED_IMAGE_TYPE` | 재촬영/전송 설정 확인 |
| `503` | `RECOGNITION_UNAVAILABLE` | 잠시 후 재시도. 출석 성공 표시 금지 |

네트워크 실패·타임아웃에도 출석 성공 화면으로 이동하지 않는다. 같은 멱등성 키로 재시도해 실제 저장 결과를 확인한다. 다만 토큰이 만료됐고 첫 요청 결과를 조회할 수 없는 경우에는 임의로 성공 처리하지 않고 재인식을 안내한다.

## 8. 프론트엔드 화면 매핑과 백엔드 완료 조건

1. 홈 진입 → `current-session`으로 강의명/차시 확인. 현재 강의명 하드코딩을 응답 값으로 교체한다.
2. 얼굴 인식 화면 → 브라우저에서 얼굴 위치만 확인하고 한 장을 촬영해 `recognitions` 호출. `MATCHED`면 이름·학과·입학연도로 확인 화면 구성한다. `RETRY`면 재촬영, `ALREADY_CHECKED_IN`이면 중복 안내, `ATTENDANCE_LOCKED`면 관리자 문의 안내.
3. `제가 아니에요` → 토큰 폐기 후 인식 화면으로 복귀. `전자출결` → `check-ins` 호출. 응답 전 버튼 잠금. `CHECKED_IN`일 때만 출석/지각과 서버 `checkedInAt`을 완료 화면에 표시한다.
4. 완료 화면 → 기존 5초 홈 복귀는 그대로 사용한다. 이 카운트다운은 서버 출석 판정 기준과 관계없다.
5. 관리자 → 강의/차시 선택 후 출결 목록·집계 조회. 이름/학번 검색, 상태 필터, 주기적 재조회, 상태 정정에 위 관리자 API 사용.

백엔드 완료 조건: 미등록·미수강·불명확 얼굴은 기록 없음, 확인 버튼 전 기록 없음, 동시/중복 요청은 한 건만 기록, 지각 경계와 마감 경계 정확, 마감 후 미인증은 결석 조회, 관리자만 정정 가능, 원본 이미지 처리 후 삭제, 단말기 다른 강의 접근 차단.

## 9. 연동 전 준비할 값과 현재 구현 수정점

- **데이터 입력 필요**: 실제 `courseId`, 차시별 `openAt`/`lateFrom`/`closeAt`, 수강생·얼굴 특징 정보, 단말기-강의 연결, 관리자 계정. 이 문서의 예시 시각을 실제 규칙으로 사용하지 않는다.
- **백엔드 구현 필요**: 현재 4개 계정 API 외의 모든 경로, 이미지에서 특징 추출·비교, 출석/정정 모델, 단말기 인증 필터, 관리자 `ROLE_ADMIN` 권한 검사, 일관된 오류 응답. 인식 모델과 유사도 임계값은 백엔드/디바이스 담당자가 실제 테스트를 통해 정해야 한다.
- **기존 구현 수정 필요**: 공개 회원가입으로 `ROLE_ADMIN`을 받을 수 있는 문제, 사용자 엔티티의 학과/입학연도 부재, 계정 API의 숫자형 학번과 새 출결 API의 문자열형 학번 차이. 현재 사용자 화면의 이름/시간 더미 값을 API 응답으로 교체한다.
