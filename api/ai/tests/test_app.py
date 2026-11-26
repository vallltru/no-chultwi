import io
import os
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

import numpy as np
from PIL import Image
from fastapi.testclient import TestClient

# api/ai 디렉터리를 import 경로에 추가 (어느 작업 디렉터리에서 실행해도 동작)
AI_DIR = Path(__file__).resolve().parent.parent
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

import app as ai_app  # noqa: E402


def create_test_image_bytes(color=(120, 180, 220), fmt="JPEG", size=(64, 64)) -> bytes:
    img = Image.new("RGB", size, color=color)
    buf = io.BytesIO()
    img.save(buf, format=fmt)
    return buf.getvalue()


class FakeFaceAnalyzer:
    def __init__(self, faces):
        self._faces = faces

    def get(self, _bgr_img):
        return self._faces


class AiServiceTest(unittest.TestCase):
    def setUp(self):
        self._env_backup = {
            key: os.environ.get(key)
            for key in ("AI_INTERNAL_SECRET", "AI_ALLOW_MOCK_FALLBACK", "AI_DISABLE_MODEL_LOAD")
        }
        os.environ["AI_DISABLE_MODEL_LOAD"] = "true"
        os.environ.pop("AI_INTERNAL_SECRET", None)
        os.environ.pop("AI_ALLOW_MOCK_FALLBACK", None)

        ai_app.face_analyzer = None
        ai_app.onnx_session = None
        ai_app.input_tensor_name = None
        ai_app.output_tensor_name = None

    def tearDown(self):
        ai_app.face_analyzer = None
        ai_app.onnx_session = None
        ai_app.input_tensor_name = None
        ai_app.output_tensor_name = None

        for key, value in self._env_backup.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    def test_health_reports_degraded_when_no_model_and_mock_disabled(self):
        with TestClient(ai_app.app) as client:
            res = client.get("/health")
            self.assertEqual(res.status_code, 200)
            body = res.json()
            self.assertEqual(body["status"], "DEGRADED")
            self.assertFalse(body["model_loaded"])
            self.assertFalse(body["mock_fallback_enabled"])

    def test_health_reports_up_when_mock_fallback_enabled(self):
        os.environ["AI_ALLOW_MOCK_FALLBACK"] = "true"
        with TestClient(ai_app.app) as client:
            res = client.get("/health")
            self.assertEqual(res.status_code, 200)
            body = res.json()
            self.assertEqual(body["status"], "UP")
            self.assertEqual(body["engine"], "Mock")
            self.assertTrue(body["mock_fallback_enabled"])

    def test_embed_returns_401_when_internal_secret_missing_or_wrong(self):
        os.environ["AI_INTERNAL_SECRET"] = "test-secret-123"
        os.environ["AI_ALLOW_MOCK_FALLBACK"] = "true"
        img_bytes = create_test_image_bytes()

        with TestClient(ai_app.app) as client:
            res_missing = client.post(
                "/embed",
                files={"file": ("face.jpg", img_bytes, "image/jpeg")},
            )
            self.assertEqual(res_missing.status_code, 401)

            res_wrong = client.post(
                "/embed",
                headers={"X-Internal-Secret": "wrong-secret"},
                files={"file": ("face.jpg", img_bytes, "image/jpeg")},
            )
            self.assertEqual(res_wrong.status_code, 401)

    def test_embed_succeeds_with_matching_internal_secret(self):
        os.environ["AI_INTERNAL_SECRET"] = "test-secret-123"
        os.environ["AI_ALLOW_MOCK_FALLBACK"] = "true"
        img_bytes = create_test_image_bytes()

        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                headers={"X-Internal-Secret": "test-secret-123"},
                files={"file": ("face.jpg", img_bytes, "image/jpeg")},
            )
            self.assertEqual(res.status_code, 200)
            body = res.json()
            self.assertTrue(body["success"])
            self.assertEqual(len(body["embedding"]), 512)

    def test_embed_returns_415_for_non_image_content_type(self):
        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                files={"file": ("doc.pdf", b"%PDF-1.4", "application/pdf")},
            )
            self.assertEqual(res.status_code, 415)

    def test_embed_returns_empty_file_message_for_zero_bytes(self):
        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                files={"file": ("empty.jpg", b"", "image/jpeg")},
            )
            self.assertEqual(res.status_code, 200)
            self.assertEqual(res.json(), {"success": False, "embedding": None, "message": "EMPTY_FILE"})

    def test_embed_returns_413_when_image_exceeds_5mb(self):
        oversized = b"\x00" * (5 * 1024 * 1024 + 1)
        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                files={"file": ("huge.jpg", oversized, "image/jpeg")},
            )
            self.assertEqual(res.status_code, 413)

    def test_embed_returns_invalid_image_for_corrupted_bytes(self):
        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                files={"file": ("corrupt.jpg", b"not-a-valid-image", "image/jpeg")},
            )
            self.assertEqual(res.status_code, 200)
            self.assertFalse(res.json()["success"])
            self.assertEqual(res.json()["message"], "INVALID_IMAGE_FILE")

    def test_embed_returns_503_when_no_model_loaded_and_mock_disabled(self):
        img_bytes = create_test_image_bytes()
        with TestClient(ai_app.app) as client:
            res = client.post(
                "/embed",
                files={"file": ("face.jpg", img_bytes, "image/jpeg")},
            )
            self.assertEqual(res.status_code, 503)

    def test_embed_returns_normalized_512d_vector_in_mock_fallback_mode(self):
        os.environ["AI_ALLOW_MOCK_FALLBACK"] = "true"
        img_a = create_test_image_bytes(color=(10, 20, 30), fmt="PNG")
        img_b = create_test_image_bytes(color=(200, 100, 50), fmt="PNG")

        with TestClient(ai_app.app) as client:
            res_a1 = client.post("/embed", files={"file": ("a.png", img_a, "image/png")})
            res_a2 = client.post("/embed", files={"file": ("a.png", img_a, "image/png")})
            res_b = client.post("/embed", files={"file": ("b.png", img_b, "image/png")})

            self.assertEqual(res_a1.status_code, 200)
            vec_a1 = np.array(res_a1.json()["embedding"], dtype=np.float32)
            vec_a2 = np.array(res_a2.json()["embedding"], dtype=np.float32)
            vec_b = np.array(res_b.json()["embedding"], dtype=np.float32)

            self.assertEqual(len(vec_a1), 512)
            self.assertAlmostEqual(float(np.linalg.norm(vec_a1)), 1.0, places=4)
            np.testing.assert_allclose(vec_a1, vec_a2)
            self.assertFalse(np.allclose(vec_a1, vec_b))

    def test_embed_selects_center_most_face_when_multiple_faces_detected(self):
        img_bytes = create_test_image_bytes(size=(200, 200), fmt="JPEG")
        edge_vec = np.zeros(512, dtype=np.float32)
        edge_vec[0] = 3.0
        center_vec = np.zeros(512, dtype=np.float32)
        center_vec[1] = 4.0

        edge_face = SimpleNamespace(bbox=[0, 0, 20, 20], embedding=edge_vec)
        center_face = SimpleNamespace(bbox=[90, 90, 110, 110], embedding=center_vec)

        with TestClient(ai_app.app) as client:
            ai_app.face_analyzer = FakeFaceAnalyzer([edge_face, center_face])
            res = client.post("/embed", files={"file": ("multi.jpg", img_bytes, "image/jpeg")})

            self.assertEqual(res.status_code, 200)
            body = res.json()
            self.assertTrue(body["success"])
            vec = np.array(body["embedding"], dtype=np.float32)
            self.assertAlmostEqual(float(vec[0]), 0.0, places=5)
            self.assertAlmostEqual(float(vec[1]), 1.0, places=5)

    def test_embed_returns_no_face_detected_when_analyzer_finds_empty_list(self):
        img_bytes = create_test_image_bytes(size=(100, 100), fmt="JPEG")

        with TestClient(ai_app.app) as client:
            ai_app.face_analyzer = FakeFaceAnalyzer([])
            res = client.post("/embed", files={"file": ("empty_face.jpg", img_bytes, "image/jpeg")})

            self.assertEqual(res.status_code, 200)
            self.assertFalse(res.json()["success"])
            self.assertEqual(res.json()["message"], "NO_FACE_DETECTED")


if __name__ == "__main__":
    unittest.main()
