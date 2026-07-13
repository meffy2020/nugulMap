# Collector code uses Python 3.10+ typing syntax; keep the container aligned
# with the tested local runtime.
FROM python:3.11-slim

# 작업 디렉토리 설정
WORKDIR /app

# PYTHONPATH 설정

ENV PYTHONPATH=/app


# requirements.txt 복사 및 의존성 설치
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# app 디렉토리 복사
COPY . /app/

# FastAPI 실행

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
