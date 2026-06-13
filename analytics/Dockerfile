FROM python:3.12-slim AS build

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-slim

RUN addgroup --system analytics && \
    adduser --system --ingroup analytics analytics

WORKDIR /app
COPY --from=build /install /usr/local
COPY app/ app/

USER analytics
EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001", "--timeout-graceful-shutdown", "30"]
