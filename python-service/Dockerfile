# syntax=docker/dockerfile:1
FROM python:3.12-slim

WORKDIR /app

# opencv-python-headless igual enlaza contra estas libs nativas en tiempo de carga;
# sin ellas cv2 importa "bien" pero le faltan símbolos (ej. CascadeClassifier)
RUN apt-get update && apt-get install -y --no-install-recommends \
      libglib2.0-0 \
      libgl1 \
      libsm6 \
      libxext6 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py yunet.onnx sface.onnx ./

RUN useradd --system --uid 1001 appuser && chown -R appuser /app
USER appuser

EXPOSE 8001

# Render inyecta PORT en runtime
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8001}"]
