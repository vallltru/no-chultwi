# FACE ATTENDANCE (노출튀) - AI Face Recognition Inference Service

이 서비스는 **ArcFace (MobileFaceNet)** 모델을 활용하여 얼굴 이미지에서 512차원 정규화된 고유 특징 벡터(Embedding)를 실시간 추출하는 경량 AI 추론 사이드카입니다.

---

## 🚀 빠른 시작 (Local / EC2)

### 1. 가상환경 생성 및 패키지 설치
```bash
cd api/ai
python -m venv venv

# Windows PowerShell:
.\venv\Scripts\Activate.ps1

# Linux / Mac:
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt
```

### 2. 서버 실행
```bash
python app.py
# 또는
uvicorn app:app --host 127.0.0.1 --port 8000
```

- 서버 주소: `http://localhost:8000`
- 헬스 체크: `http://localhost:8000/health`
- Swagger 문서: `http://localhost:8000/docs`

---

## 📡 API 엔드포인트 규격

### 1. `GET /health`
- **응답 (200 OK)**:
```json
{
  "status": "UP",
  "service": "Face Attendance AI Engine (ArcFace 512d)",
  "engine": "InsightFace (buffalo_s: detection+recognition)",
  "model_loaded": true,
  "mock_fallback_enabled": false
}
```

### 2. `POST /embed`
- **헤더 (선택)**: `X-Internal-Secret: <AI_INTERNAL_SECRET>` (`AI_INTERNAL_SECRET` 환경변수 설정 시 필수)
- **요청**: `multipart/form-data` (file: JPEG/PNG 파일, 최대 5MB)
- **응답 (200 OK)**:
```json
{
  "success": true,
  "embedding": [0.0123, -0.0456, ..., 0.0891],  // 512차원 float 배열
  "message": null
}
```
- 모델이 로드되지 않은 상태에서는 기본적으로 `503 Service Unavailable`을 반환합니다. 로컬 개발·테스트에서만 `AI_ALLOW_MOCK_FALLBACK=true`로 모의 벡터 폴백을 활성화할 수 있습니다.

---

## 🧪 테스트 실행

```bash
cd api/ai
pip install -r requirements-test.txt
pytest -v
# 또는 pytest 없이 표준 unittest 실행
python -m unittest discover -s tests -v
```

---

## ⚙️ Spring Boot 백엔드와의 연동 방법

`api/backend/src/main/resources/application.properties` (또는 환경변수):
```properties
ai.face.enabled=true
ai.face.server-url=http://localhost:8000
# 경량 모델(MobileFaceNet/buffalo_s)은 0.42, 고성능 모델(ResNet-50)은 0.65 권장
ai.face.similarity-threshold=0.42
# AI 서버에 AI_INTERNAL_SECRET을 설정한 경우 동일 값 입력
ai.face.internal-secret=${AI_INTERNAL_SECRET:}
```
- `ai.face.enabled=true`로 설정하면 백엔드가 모의 어댑터(`Stub`) 대신 `HttpFaceRecognitionAdapter`를 활성화하여 실제 AI 모델과 통신합니다.
