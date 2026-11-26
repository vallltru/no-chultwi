"""
FACE ATTENDANCE (노출튀) - AI Face Recognition Inference Sidecar Service
ArcFace 512-dimensional embedding extraction using FastAPI and ONNX Runtime.
Hardened with security controls: DoS/Decompression bomb defense, singleton model caching,
threadpool inference isolation, safe failure fallback, and private host binding.
"""

import hashlib
import hmac
import io
import logging
import os
from contextlib import asynccontextmanager
from typing import List, Optional

import numpy as np
from PIL import Image
from fastapi import FastAPI, File, Header, HTTPException, UploadFile, status
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

# 1. Decompression Bomb 방어: 최대 이미지 픽셀 수 제한 (약 3162x3162 픽셀 = 1천만 픽셀)
Image.MAX_IMAGE_PIXELS = 10_000_000
MAX_IMAGE_FILE_BYTES = 5 * 1024 * 1024  # 최대 5MB 제한

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ai_service")

# ONNX Runtime 및 InsightFace 세션 싱글톤 캐시
face_analyzer = None
onnx_session = None
input_tensor_name = None
output_tensor_name = None


def get_internal_secret() -> str:
    """내부 인증 시크릿(환경변수 설정 시 활성화)."""
    return os.getenv("AI_INTERNAL_SECRET", "").strip()


def allow_mock_fallback() -> bool:
    """개발·테스트 전용 해시 폴백 허용 여부 (운영 기본값: False)."""
    return os.getenv("AI_ALLOW_MOCK_FALLBACK", "false").strip().lower() == "true"


def disable_model_load() -> bool:
    """단위 테스트 등에서 무거운 모델 로딩을 건너뛰기 위한 플래그."""
    return os.getenv("AI_DISABLE_MODEL_LOAD", "false").strip().lower() == "true"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global face_analyzer, onnx_session, input_tensor_name, output_tensor_name

    if disable_model_load():
        logger.info("AI_DISABLE_MODEL_LOAD=true: skipping model initialization.")
        yield
        return

    # 1. InsightFace 공식 모델 우선 시도 (필요한 detection + recognition 2개 모듈만 경량 로딩)
    try:
        from insightface.app import FaceAnalysis

        logger.info("Initializing InsightFace FaceAnalysis (buffalo_s, detection+recognition)...")
        analyzer = FaceAnalysis(
            name="buffalo_s",
            allowed_modules=["detection", "recognition"],
            providers=["CPUExecutionProvider"],
        )
        analyzer.prepare(ctx_id=0, det_size=(640, 640))
        face_analyzer = analyzer
        logger.info("InsightFace FaceAnalysis engine loaded successfully.")
    except Exception as e:
        logger.warning(f"InsightFace initialization skipped: {e}. Falling back to standalone ONNX.")

    # 2. 단독 ONNX 모델 가중치 파일 로딩 시도 (InsightFace 실패 시)
    if face_analyzer is None:
        model_path = os.getenv("ARCFACE_MODEL_PATH", "models/mobilefacenet.onnx")
        if os.path.exists(model_path):
            try:
                import onnxruntime as ort

                logger.info(f"Loading standalone ArcFace ONNX model from {model_path}...")
                onnx_session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
                input_tensor_name = onnx_session.get_inputs()[0].name
                output_tensor_name = onnx_session.get_outputs()[0].name
                logger.warning(
                    "Standalone ONNX loaded without InsightFace face detector "
                    f"(Input: {input_tensor_name}, Output: {output_tensor_name})."
                )
            except Exception as e:
                logger.error(f"Failed to load ONNX model: {e}")
                onnx_session = None
        else:
            logger.warning(f"ONNX model file not found at '{model_path}'.")

    yield

    # 종료 시 세션 정리
    face_analyzer = None
    onnx_session = None
    input_tensor_name = None
    output_tensor_name = None


app = FastAPI(
    title="Face Attendance AI Engine",
    description="Extracts 512-dim ArcFace embeddings for university kiosk attendance",
    version="1.2.0",
    lifespan=lifespan,
)


class EmbedResponse(BaseModel):
    success: bool
    embedding: Optional[List[float]] = None
    message: Optional[str] = None


@app.get("/health")
def health_check():
    model_loaded = face_analyzer is not None or onnx_session is not None
    mock_enabled = allow_mock_fallback()
    if face_analyzer is not None:
        engine = "InsightFace (buffalo_s: detection+recognition)"
    elif onnx_session is not None:
        engine = "ONNX Runtime"
    elif mock_enabled:
        engine = "Mock"
    else:
        engine = "Unavailable"

    return {
        "status": "UP" if (model_loaded or mock_enabled) else "DEGRADED",
        "service": "Face Attendance AI Engine (ArcFace 512d)",
        "engine": engine,
        "model_loaded": model_loaded,
        "mock_fallback_enabled": mock_enabled,
    }


@app.post("/embed", response_model=EmbedResponse)
async def extract_embedding(
    file: UploadFile = File(...),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret"),
):
    """
    단말기/백엔드가 전송한 얼굴 사진에서 512차원 정규화된 특징 벡터를 추출합니다.
    """
    # 보안 1: 내부 API 시크릿 검증 (상수 시간 비교로 타이밍 공격 방어)
    expected_secret = get_internal_secret()
    if expected_secret and not hmac.compare_digest(x_internal_secret or "", expected_secret):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized internal AI service access",
        )

    # 보안 2: MIME 타입 유효성 검증
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=415, detail="Unsupported media type. Image required.")

    # 보안 3: 페이로드 크기 한도(5MB) 검증 (메모리 고갈 방어)
    image_bytes = await file.read()
    if len(image_bytes) == 0:
        return EmbedResponse(success=False, message="EMPTY_FILE")
    if len(image_bytes) > MAX_IMAGE_FILE_BYTES:
        raise HTTPException(status_code=413, detail="Image file exceeds 5MB size limit.")

    # CPU 바운드 이미지 디코딩·추론을 워커 스레드풀로 분리해 이벤트 루프 블로킹 방지
    return await run_in_threadpool(_process_image_and_embed, image_bytes)


def _process_image_and_embed(image_bytes: bytes) -> EmbedResponse:
    # 보안 4: Decompression Bomb 방어 및 이미지 유효성 디코딩
    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        width, height = image.size
        if width > 4096 or height > 4096:
            return EmbedResponse(success=False, message="IMAGE_DIMENSION_TOO_LARGE")
    except Image.DecompressionBombError:
        logger.warning("Rejected malicious decompression bomb image attempt.")
        return EmbedResponse(success=False, message="DECOMPRESSION_BOMB_DETECTED")
    except Exception as e:
        logger.error(f"Image decode failed: {e}")
        return EmbedResponse(success=False, message="INVALID_IMAGE_FILE")

    # 1. InsightFace 엔진이 활성화된 경우: 얼굴 검출 + 5-Landmark 정렬 + 임베딩 원스톱 추출
    if face_analyzer is not None:
        try:
            # RGB -> BGR 변환 (OpenCV 없이 NumPy 슬라이싱으로 동일 결과 생성)
            bgr_img = np.array(image)[:, :, ::-1].copy()
            faces = face_analyzer.get(bgr_img)
            if len(faces) == 0:
                logger.info("No face detected in the image.")
                return EmbedResponse(success=False, message="NO_FACE_DETECTED")

            # 명세 6.3: 여러 얼굴이 검출된 경우 화면 중앙에 가장 가까운 사용자 얼굴을 선택
            img_center_x, img_center_y = width / 2.0, height / 2.0

            def distance_to_center(f):
                face_cx = (f.bbox[0] + f.bbox[2]) / 2.0
                face_cy = (f.bbox[1] + f.bbox[3]) / 2.0
                return (face_cx - img_center_x) ** 2 + (face_cy - img_center_y) ** 2

            best_face = min(faces, key=distance_to_center)
            embedding = _l2_normalize(np.asarray(best_face.embedding, dtype=np.float32))
            logger.info(f"InsightFace extracted 512d embedding (norm={np.linalg.norm(embedding):.4f})")
            return EmbedResponse(success=True, embedding=embedding.tolist())
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"InsightFace inference failed: {e}")
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="AI face recognition inference failed.",
            )

    # 2. 단독 ONNX 세션이 로드된 경우 (ArcFace 표준 전처리 112x112 CHW)
    if onnx_session is not None:
        try:
            face_img = image.resize((112, 112))
            img_array = np.array(face_img, dtype=np.float32)
            img_array = (img_array - 127.5) / 128.0
            img_array = np.transpose(img_array, (2, 0, 1))  # (3, 112, 112)
            img_tensor = np.expand_dims(img_array, axis=0)  # (1, 3, 112, 112)

            result = onnx_session.run([output_tensor_name], {input_tensor_name: img_tensor})[0]
            embedding = _l2_normalize(result.flatten().astype(np.float32))
            logger.info(f"ONNX extracted 512d embedding (norm={np.linalg.norm(embedding):.4f})")
            return EmbedResponse(success=True, embedding=embedding.tolist())
        except Exception as e:
            logger.error(f"ONNX model inference failed: {e}")
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="AI ONNX inference failed.",
            )

    # 3. 모델 미로딩 시: 운영 기본값은 503 안전 실패, 명시적 개발 플래그일 때만 모의 해시 벡터 허용
    if not allow_mock_fallback():
        logger.error("No AI model loaded and AI_ALLOW_MOCK_FALLBACK is disabled.")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI face recognition model is not loaded.",
        )

    embedding = _l2_normalize(_generate_mock_embedding(image_bytes))
    logger.warning(f"Mock fallback generated 512d embedding (norm={np.linalg.norm(embedding):.4f})")
    return EmbedResponse(success=True, embedding=embedding.tolist())


def _generate_mock_embedding(raw_bytes: bytes) -> np.ndarray:
    """개발/테스트 전용: 이미지 전체 SHA-256 해시 기반의 결정론적 512차원 벡터 생성."""
    hasher = hashlib.sha256(raw_bytes)
    seed = int.from_bytes(hasher.digest()[:8], byteorder="little", signed=False)
    rng = np.random.RandomState(seed % (2**31 - 1))
    return rng.randn(512).astype(np.float32)


def _l2_normalize(vector: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vector)
    if norm > 0:
        return vector / norm
    return vector


if __name__ == "__main__":
    import uvicorn

    # 보안 5: 외부 임의 접근 차단을 위해 로컬 루프백(127.0.0.1) 기본 바인딩
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", 8000))
    logger.info(f"Starting Face Attendance AI Server on {host}:{port}...")
    uvicorn.run(app, host=host, port=port)
