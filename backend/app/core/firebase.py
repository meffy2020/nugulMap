import os
from dotenv import load_dotenv
from firebase_admin import credentials, initialize_app, firestore

# .env 파일 명시적으로 로드
load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), '../../.env'))

# Firebase 자격 증명 경로 로드
cred_path = os.getenv("FIREBASE_CREDENTIAL")
print("ENV 경로 확인:", cred_path)

# Firebase 초기화
cred = credentials.Certificate(cred_path)
firebase_app = initialize_app(cred)

# Firestore DB 인스턴스
db = firestore.client()