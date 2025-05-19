import os
import json
from firebase_admin import credentials, initialize_app, firestore

# Firebase 자격 증명 로드
cred_json = os.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON")  # JSON 문자열
cred_path = os.getenv("FIREBASE_CREDENTIAL")  # 파일 경로

if cred_json:
    # 환경 변수에서 JSON 문자열로 인증서 로드
    print("Firebase 인증서를 JSON 문자열로 로드합니다.")
    cred = credentials.Certificate(json.loads(cred_json))
elif cred_path and os.path.exists(cred_path):
    # 환경 변수에서 파일 경로로 인증서 로드
    print("Firebase 인증서를 파일 경로로 로드합니다.")
    cred = credentials.Certificate(cred_path)
else:
    raise ValueError("Firebase 인증서가 설정되지 않았습니다. 환경 변수를 확인하세요.")

# Firebase 초기화
firebase_app = initialize_app(cred)

# Firestore DB 인스턴스
db = firestore.client()