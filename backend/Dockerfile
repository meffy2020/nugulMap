# Python 3.9 이미지를 기반으로 사용
FROM python:3.9-slim

# 작업 디렉토리 설정
WORKDIR /app

# 현재 디렉토리의 모든 파일을 컨테이너로 복사
COPY . /app

# PYTHONPATH 환경 변수 설정
ENV PYTHONPATH=/app

# 의존성 설치
RUN pip install --no-cache-dir -r requirements.txt

# 컨테이너 실행 시 실행할 명령어
CMD ["python", "app/scripts/upload_csv_to_firestore.py"]