# Python 3.9 이미지를 기반으로 사용
FROM python:3.9-slim

# 작업 디렉토리 설정
WORKDIR /app

# PYTHONPATH 설정
ENV PYTHONPATH=/app

# requirements.txt만 먼저 복사
COPY ./backend/requirements.txt /app/requirements.txt

# 의존성 설치
RUN pip install --no-cache-dir -r requirements.txt

# app 디렉토리만 복사
COPY ./backend/app /app

# 컨테이너 실행 시 실행할 명령어
CMD ["python", "main.py"]