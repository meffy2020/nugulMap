# Python 3.9 이미지를 기반으로 사용
FROM python:3.9-slim

# 작업 디렉토리 설정
WORKDIR /app

# PYTHONPATH 설정

ENV PYTHONPATH=/app


# requirements.txt 복사 및 의존성 설치
COPY ./backend/data-scripts/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# app 디렉토리 복사
COPY ./backend/data-scripts/ /app/

# FastAPI 실행

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]