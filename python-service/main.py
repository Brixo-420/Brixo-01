import base64
import cv2
import numpy as np
import os
import logging
import urllib.request

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("brixo")

app = FastAPI(title="Brixo Identity Validator", version="3.0.0")

MODEL_DIR = os.path.dirname(os.path.abspath(__file__))
YUNET_PATH = os.path.join(MODEL_DIR, "yunet.onnx")
SFACE_PATH = os.path.join(MODEL_DIR, "sface.onnx")

YUNET_URL = "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
SFACE_URL  = "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"

HAAR = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

MAX_DIM = 1280
MIN_DIM = 60


def _download(url: str, dest: str):
    print(f"  Descargando {os.path.basename(dest)} ...")
    urllib.request.urlretrieve(url, dest)
    size = os.path.getsize(dest)
    if size < 10_000:
        os.remove(dest)
        raise RuntimeError(f"Archivo descargado demasiado pequeño ({size} bytes) — URL inválida")
    print(f"  OK ({size // 1024} KB)")


def _init_dnn():
    for url, path in [(YUNET_URL, YUNET_PATH), (SFACE_URL, SFACE_PATH)]:
        if not os.path.exists(path):
            _download(url, path)
    det = cv2.FaceDetectorYN.create(YUNET_PATH, "", (320, 320),
                                     score_threshold=0.4, nms_threshold=0.3)
    rec = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
    return det, rec


print("Iniciando motor de reconocimiento facial...")
try:
    _detector, _recognizer = _init_dnn()
    DNN_OK = True
    print("Motor DNN (YuNet + SFace) listo — alta precision")
except Exception as e:
    print(f"DNN no disponible ({e}), usando Haar cascade (menor precision)")
    DNN_OK = False
    _detector = _recognizer = None


# ── helpers ───────────────────────────────────────────────────────────────────

def decode_image(data: bytes) -> np.ndarray:
    """Decodifica bytes de imagen a BGR numpy array, normaliza tamaño y canales."""
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("No se pudo decodificar la imagen (formato no soportado)")
    if len(img.shape) == 3 and img.shape[2] == 4:
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
    h, w = img.shape[:2]
    if max(h, w) > MAX_DIM:
        scale = MAX_DIM / max(h, w)
        img = cv2.resize(img, (max(1, int(w * scale)), max(1, int(h * scale))))
    log.info("Imagen: %dx%d", img.shape[1], img.shape[0])
    return img


def get_feature_dnn(img_bgr: np.ndarray):
    h, w = img_bgr.shape[:2]
    if min(h, w) < MIN_DIM:
        return None

    # Reducir imágenes grandes para mejorar detección (fotos de celular portrait)
    target_h = min(h, 640)
    if target_h < h:
        scale = target_h / h
        nw = max(int(w * scale) // 2 * 2, 2)
        img_bgr = cv2.resize(img_bgr, (nw, target_h))
        h, w = img_bgr.shape[:2]

    # YuNet requiere dimensiones par
    w2, h2 = (w // 2) * 2, (h // 2) * 2
    img_bgr = img_bgr[:h2, :w2]

    _detector.setInputSize((w2, h2))
    _, faces = _detector.detect(img_bgr)
    if faces is None or len(faces) == 0:
        return None

    best = max(faces, key=lambda f: f[2] * f[3])
    aligned = _recognizer.alignCrop(img_bgr, best)
    return _recognizer.feature(aligned)


def score_dnn(feat1, feat2) -> float:
    # FR_COSINE=0, FR_NORM_L2=1 — usar literal por compatibilidad entre versiones de cv2
    cosine = float(_recognizer.match(feat1, feat2, 0))
    return float(np.clip((cosine + 1) / 2, 0.0, 1.0))


# ── Haar fallback ──────────────────────────────────────────────────────────────

def get_face_haar(img_bgr: np.ndarray):
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.equalizeHist(gray)
    faces = []
    for scale, nb in [(1.05, 4), (1.1, 2), (1.15, 1)]:
        faces = HAAR.detectMultiScale(gray, scaleFactor=scale, minNeighbors=nb, minSize=(30, 30))
        if len(faces) > 0:
            break
    if len(faces) == 0:
        return None
    x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
    return cv2.resize(gray[y:y+h, x:x+w], (128, 128)).astype(np.float32)


def score_haar(f1, f2) -> float:
    f1n = f1 - f1.mean()
    f2n = f2 - f2.mean()
    denom = np.sqrt((f1n**2).sum() * (f2n**2).sum()) + 1e-10
    ncc = float((f1n * f2n).sum() / denom)
    ncc = (ncc + 1) / 2

    h1 = cv2.calcHist([f1.astype(np.uint8)], [0], None, [64], [0, 256])
    h2 = cv2.calcHist([f2.astype(np.uint8)], [0], None, [64], [0, 256])
    cv2.normalize(h1, h1)
    cv2.normalize(h2, h2)
    hist = max(0.0, float(cv2.compareHist(h1, h2, cv2.HISTCMP_CORREL)))

    mu1, mu2 = f1.mean(), f2.mean()
    s1, s2 = f1.std(), f2.std()
    cov = float(np.mean((f1 - mu1) * (f2 - mu2)))
    C1, C2 = 6.5, 58.5
    ssim = ((2*mu1*mu2 + C1) * (2*cov + C2)) / ((mu1**2 + mu2**2 + C1) * (s1**2 + s2**2 + C2))
    ssim = max(0.0, float(ssim))

    return float(np.clip(0.4*ncc + 0.35*hist + 0.25*ssim, 0.0, 1.0))


# ── endpoints ─────────────────────────────────────────────────────────────────

@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    body = await request.body()
    log.error("422 — errores: %s", exc.errors())
    log.error("422 — body recibido (primeros 200 chars): %s", body[:200])
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.get("/health")
def health():
    return {
        "status": "ok",
        "engine": "DNN (YuNet+SFace)" if DNN_OK else "Haar (fallback)",
        "service": "Brixo Identity Validator"
    }


class ValidarRequest(BaseModel):
    documento_b64: str
    selfie_b64: str


@app.post("/api/validar")
async def validar_identidad(req: ValidarRequest):
    log.info("Solicitud de validación recibida")
    try:
        doc_bytes    = base64.b64decode(req.documento_b64)
        selfie_bytes = base64.b64decode(req.selfie_b64)

        if not doc_bytes or not selfie_bytes:
            return {"match": False, "score": 0.0, "error": "Datos vacíos recibidos"}

        log.info("Bytes decodificados — doc=%d sel=%d", len(doc_bytes), len(selfie_bytes))

        img_doc = decode_image(doc_bytes)
        img_sel = decode_image(selfie_bytes)

        if DNN_OK:
            feat_doc = get_feature_dnn(img_doc)
            feat_sel = get_feature_dnn(img_sel)

            if feat_doc is None:
                return {"match": False, "score": 0.0,
                        "error": "No se detectó rostro en el documento"}
            if feat_sel is None:
                return {"match": False, "score": 0.0,
                        "error": "No se detectó rostro en la selfie"}

            score = score_dnn(feat_doc, feat_sel)
            match = score >= 0.68

        else:
            face_doc = get_face_haar(img_doc)
            face_sel = get_face_haar(img_sel)

            if face_doc is None:
                return {"match": False, "score": 0.0,
                        "error": "No se detectó rostro en el documento"}
            if face_sel is None:
                return {"match": False, "score": 0.0,
                        "error": "No se detectó rostro en la selfie"}

            score = score_haar(face_doc, face_sel)
            match = score >= 0.40

        log.info("Resultado: match=%s score=%.4f", match, score)
        return {
            "match": match,
            "score": round(score, 4),
            "engine": "DNN" if DNN_OK else "Haar",
            "error": None,
        }

    except Exception as e:
        log.exception("Error en validación")
        return JSONResponse(status_code=200,
                            content={"match": False, "score": 0.0, "error": str(e)})
