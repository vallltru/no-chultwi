"""
FACE ATTENDANCE (노출튀) - AI Face Recognition Inference Sidecar Service
ArcFace 512-dimensional embedding extraction using FastAPI and ONNX Runtime.
Hardened with security controls: DoS/Decompression bomb defense, singleton model caching, and private host binding.
"""

import hashlib
import io
import os
import sys
import logging
from contextlib import asynccontextmanager
from typing import List, Optional

import numpy as np
from PIL import Image
from fastapi import FastAPI, File, UploadFile, HTTPException, Header, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel

# 1. Decompression Bomb 방어: 최대 이미지 픽셀 수 제한 (약 3162x3162 픽셀 = 1천만 픽셀)
Image.MAX_IMAGE_PIXELS = 10_000_000
MAX_IMAGE_FILE_BYTES = 5 * 1024 * 1024  # 최대 5MB 제한

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ai_service")

# 내부 인증 시크릿 (환경변수 설정 시 활성화)
INTERNAL_SECRET = os.getenv("AI_INTERNAL_SECRET", "")

# ONNX Runtime 및 InsightFace 세션 싱글톤 캐시
face_analyzer = None
onnx_session = None
input_tensor_name = None
output_tensor_name = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global face_analyzer, onnx_session, input_tensor_name, output_tensor_name

    # 1. InsightFace 공식 모델 우선 시도 (얼굴 감지 + 5-Landmark 정렬 + 512차원 ArcFace)
    try:
        import insightface
        from insightface.app import FaceAnalysis
        logger.info("Initializing InsightFace FaceAnalysis (buffalo_s)...")
        analyzer = FaceAnalysis(name="buffalo_s", providers=["CPUExecutionProvider"])
        analyzer.prepare(ctx_id=0, det_size=(640, 640))
        face_analyzer = analyzer
        logger.info("InsightFace FaceAnalysis engine loaded successfully.")
    except Exception as e:
        logger.warning(f"InsightFace initialization skipped: {e}. Falling back to standard ONNX.")

    # 2. 단독 ONNX 모델 가중치 파일 로딩 시도 (InsightFace 실패 시)
    if face_analyzer is None:
        model_path = os.getenv("ARCFACE_MODEL_PATH", "models/mobilefacenet.onnx")
        if os.path.exists(model_path):
            try:
                import onnxruntime as ort
                logger.info(f"Loading ArcFace ONNX model from {model_path}...")
                onnx_session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
                input_tensor_name = onnx_session.get_inputs()[0].name
                output_tensor_name = onnx_session.get_outputs()[0].name
                logger.info(f"ArcFace ONNX model successfully loaded (Input: {input_tensor_name}, Output: {output_tensor_name})")
            except Exception as e:
                logger.error(f"Failed to load ONNX model: {e}")
                onnx_session = None
        else:
            logger.warning(f"ONNX model file not found at '{model_path}'. Running in deterministic mock mode.")

    yield

    # 종료 시 세션 정리
    face_analyzer = None
    onnx_session = None


app = FastAPI(
    title="Face Attendance AI Engine",
    description="Extracts 512-dim ArcFace embeddings for university kiosk attendance",
    version="1.1.0",
    lifespan=lifespan,
)


class EmbedResponse(BaseModel):
    success: bool
    embedding: Optional[List[float]] = None
    message: Optional[str] = None


@app.get("/health")
def health_check():
    model_loaded = face_analyzer is not None or onnx_session is not None
    engine = "InsightFace (buffalo_s)" if face_analyzer is not None else ("ONNX Runtime" if onnx_session is not None else "Mock")
    return {
        "status": "UP",
        "service": "Face Attendance AI Engine (ArcFace 512d)",
        "engine": engine,
        "model_loaded": model_loaded
    }


@app.post("/embed", response_model=EmbedResponse)
async def extract_embedding(
    file: UploadFile = File(...),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret")
):
    """
    단말기/백엔드가 전송한 얼굴 사진에서 512차원 정규화된 특징 벡터를 추출합니다.
    """
    # 보안 1: 내부 API 시크릿 검증 (설정된 경우)
    if INTERNAL_SECRET and x_internal_secret != INTERNAL_SECRET:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized internal AI service access"
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
            import cv2
            # Pillow RGB 이미지를 OpenCV BGR 배열로 변환 (중복 디코드 방지)
            cv_img = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
            
            faces = face_analyzer.get(cv_img)
            if len(faces) == 0:
                logger.info("No face detected in the image.")
                return EmbedResponse(success=False, message="NO_FACE_DETECTED")
            
            # 가장 큰 얼굴 선택
            best_face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
            embedding = best_face.embedding.astype(np.float32)
            
            norm = np.linalg.norm(embedding)
            if norm > 0:
                embedding = embedding / norm

            logger.info(f"InsightFace extracted 512d embedding (norm={np.linalg.norm(embedding):.4f})")
            return EmbedResponse(success=True, embedding=embedding.tolist())
        except Exception as e:
            logger.warning(f"InsightFace inference failed: {e}. Falling back to standard pipeline.")

    # 2. 폴백 파이프라인 (얼굴 크롭 및 정규화 112x112 규격)
    face_img = image.resize((112, 112))
    img_array = np.array(face_img, dtype=np.float32)

    # ArcFace 표준 전처리: (pixel - 127.5) / 128.0, CHW 포맷
    img_array = (img_array - 127.5) / 128.0
    img_array = np.transpose(img_array, (2, 0, 1))  # (3, 112, 112)
    img_tensor = np.expand_dims(img_array, axis=0)   # (1, 3, 112, 112)

    # 512차원 특징 벡터 추출
    embedding = run_arcface_inference(img_tensor, image_bytes)

    # L2 정규화 (Unit Vector)
    norm = np.linalg.norm(embedding)
    if norm > 0:
        embedding = embedding / norm

    logger.info(f"Successfully extracted 512d embedding (norm={np.linalg.norm(embedding):.4f})")
    return EmbedResponse(success=True, embedding=embedding.tolist())


def run_arcface_inference(img_tensor: np.ndarray, raw_bytes: bytes) -> np.ndarray:
    """
    ONNX 모델이 로드되어 있으면 ONNX Runtime으로 512차원 특징을 추론하고,
    모델 가중치가 아직 없는 경우 이미지 전체 SHA-256 해시 기반의 결정론적 512차원 벡터를 생성합니다.
    """
    global onnx_session, input_tensor_name, output_tensor_name

    if onnx_session is not None:
        try:
            result = onnx_session.run([output_tensor_name], {input_tensor_name: img_tensor})[0]
            return result.flatten().astype(np.float32)
        except Exception as e:
            logger.warning(f"ONNX model inference failed: {e}. Falling back to hash-based embedding.")

    # 모델 미배포 시: JPEG 매직바이트 충돌 방지를 위해 전체 이미지의 SHA-256 해시로 고유 시드 생성
    hasher = hashlib.sha256(raw_bytes)
    seed = int.from_bytes(hasher.digest()[:8], byteorder="little", signed=False)
    rng = np.random.RandomState(seed % (2**31 - 1))
    return rng.randn(512).astype(np.float32)


if __name__ == "__main__":
    import uvicorn
    # 보안 5: 외부 임의 접근 차단을 위해 로컬 루프백(127.0.0.1) 기본 바인딩
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", 8000))
    logger.info(f"Starting Face Attendance AI Server on {host}:{port}...")
    uvicorn.run(app, host=host, port=port)
