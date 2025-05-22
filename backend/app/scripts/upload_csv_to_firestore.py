import os
import pandas as pd
import requests
from app.core.firebase import db

# 공통 열 이름 매핑 패턴
COLUMN_MAPPING_PATTERNS = {
    "자치구명": "region",
    "시도명": "region",
    "시설 구분": "type",
    "구분": "type",
    "시설형태": "subtype",
    "흡연구역범위상세": "subtype",
    "실외     실내": "subtype",
    "흡연실 형태": "subtype",
    "설치 위치": "description",
    "서울특별시 용산구 설치 위치": "description",
    "흡연구역명": "description",
    "시설명": "description",
    "건물명": "description",
    "설치도로명주소": "address",
    "소재지도로명주소": "address",
    "주소": "address",
    "영업소소재지(도로 명)": "address",
    "도로명주소": "address",
    "위도": "latitude",
    "경도": "longitude",
    "규모": "size",
    "규모_제곱미터": "size",
    "규모(제곱미터)": "size",
    "설치일": "date",
    "설치연월": "date",
    "데이터기준일자": "date",
    "설치 주체": "manager",
    "관리기관명": "manager",
    "관리기관": "manager",
    "운영관리": "manager",
    "관리여부": "manager",
}

KAKAO_API_KEY = "여기에_본인_카카오_REST_API_KEY_입력"  # 반드시 본인 키로 교체

def kakao_geocode(address, rest_api_key):
    url = "https://dapi.kakao.com/v2/local/search/address.json"
    headers = {"Authorization": f"KakaoAK {rest_api_key}"}
    params = {"query": address}
    resp = requests.get(url, headers=headers, params=params)
    if resp.status_code == 200:
        result = resp.json()
        if result["documents"]:
            lat = float(result["documents"][0]["y"])
            lng = float(result["documents"][0]["x"])
            return lat, lng
    return None, None

def auto_map_columns(df):
    """
    자동으로 열 이름을 공통 데이터 구조로 매핑합니다.
    """
    mapped_columns = {col: COLUMN_MAPPING_PATTERNS[col] for col in df.columns if col in COLUMN_MAPPING_PATTERNS}
    return df.rename(columns=mapped_columns)

def clean_data(row):
    """
    Firestore에 업로드하기 전에 데이터를 정리합니다.
    """
    # 날짜 형식 변환
    if "date" in row and isinstance(row["date"], str):
        try:
            row["date"] = pd.to_datetime(row["date"], errors="coerce").strftime("%Y-%m-%d")
        except Exception:
            row["date"] = None

    # 위도와 경도 변환
    if "latitude" in row and isinstance(row["latitude"], str):
        try:
            row["latitude"] = float(row["latitude"])
        except ValueError:
            row["latitude"] = None

    if "longitude" in row and isinstance(row["longitude"], str):
        try:
            row["longitude"] = float(row["longitude"])
        except ValueError:
            row["longitude"] = None

    # 주소로 위도/경도 변환 (둘 다 없을 때만)
    if (not row.get("latitude") or not row.get("longitude")) and row.get("address"):
        lat, lng = kakao_geocode(row["address"], KAKAO_API_KEY)
        if lat and lng:
            row["latitude"] = lat
            row["longitude"] = lng

    # 기타 문자열 처리
    for key in ["region", "type", "subtype", "description", "address", "manager", "size"]:
        if key in row and isinstance(row[key], pd.Series):
            # 여러 값을 쉼표로 병합하거나 첫 번째 값만 사용
            row[key] = ", ".join(row[key].astype(str)) if len(row[key]) > 1 else row[key].iloc[0]

    return row

def upload_csv_to_firestore(csv_file_path):
    """
    CSV 파일을 읽고 Firestore에 업로드합니다.
    """
    # CSV 파일 읽기
    try:
        df = pd.read_csv(csv_file_path, encoding="utf-8")
    except UnicodeDecodeError:
        try:
            df = pd.read_csv(csv_file_path, encoding="euc-kr")
        except Exception as e:
            print(f"파일 {csv_file_path}를 읽는 중 에러 발생: {e}")
            return

    # 열 이름 자동 매핑
    df = auto_map_columns(df)

    # Firestore에 데이터 저장
    for _, row in df.iterrows():
        row = clean_data(row.to_dict())  # 데이터 정리
        doc_id = row.get("description", f"doc_{_}")  # 문서 ID로 흡연구역 설명 사용, 없으면 임의 ID 생성

        # Firestore에서 문서 존재 여부 확인
        doc_ref = db.collection("markers").document(doc_id)
        if doc_ref.get().exists:
            print(f"문서 {doc_id}는 이미 존재합니다. 업로드를 건너뜁니다.")
            continue

        # 데이터 정리 및 업로드
        data = {
            "region": row.get("region"),
            "type": row.get("type"),
            "subtype": row.get("subtype"),
            "description": row.get("description"),
            "address": row.get("address"),
            "latitude": row.get("latitude"),
            "longitude": row.get("longitude"),
            "manager": row.get("manager"),
            "size": row.get("size"),
            "date": row.get("date"),
        }
        doc_ref.set(data)

    print(f"파일 {csv_file_path}의 데이터를 Firestore에 업로드 완료!")

def upload_all_csv_in_directory(directory_path):
    """
    디렉토리 내 모든 CSV 파일을 Firestore에 업로드합니다.
    """
    for filename in os.listdir(directory_path):
        if filename.endswith(".csv"):
            csv_file_path = os.path.join(directory_path, filename)
            print(f"업로드 중: {csv_file_path}")
            try:
                upload_csv_to_firestore(csv_file_path)
            except Exception as e:
                print(f"파일 {csv_file_path} 처리 중 에러 발생: {e}")

# 실행
if __name__ == "__main__":
    directory_path = "/Users/meffy/Desktop/nugulmap/backend/app/data"
    upload_all_csv_in_directory(directory_path)