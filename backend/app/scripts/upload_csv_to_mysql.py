import os
import pandas as pd
import requests
import sys
from sqlalchemy.exc import IntegrityError
from datetime import datetime

# 프로젝트 루트 경로를 sys.path에 추가
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from app.core.db import SessionLocal
from app.models.zone import Zone

# 공통 열 이름 매핑 패턴 (manager 관련 매핑 제거)
COLUMN_MAPPING_PATTERNS = {
    "자치구명": "region", "시도명": "region", "시설 구분": "type", "구분": "type",
    "시설형태": "subtype", "흡연구역범위상세": "subtype", "실외     실내": "subtype", "흡연실 형태": "subtype",
    "설치 위치": "description", "서울특별시 용산구 설치 위치": "description", "흡연구역명": "description",
    "시설명": "description", "건물명": "description", "설치도로명주소": "address", "소재지도로명주소": "address",
    "주소": "address", "영업소소재지(도로 명)": "address", "도로명주소": "address", "위도": "latitude",
    "경도": "longitude", "규모": "size", "규모_제곱미터": "size", "규모(제곱미터)": "size",
    "설치일": "date", "설치연월": "date", "데이터기준일자": "date",
}

KAKAO_API_KEY = "여기에_본인_카카오_REST_API_KEY_입력"  # 반드시 본인 키로 교체

def kakao_geocode(address, rest_api_key):
    if not address or pd.isna(address):
        return None, None
    url = "https://dapi.kakao.com/v2/local/search/address.json"
    headers = {"Authorization": f"KakaoAK {rest_api_key}"}
    params = {"query": address}
    try:
        resp = requests.get(url, headers=headers, params=params, timeout=5)
        if resp.status_code == 200:
            result = resp.json()
            if result["documents"]:
                lat = float(result["documents"][0]["y"])
                lng = float(result["documents"][0]["x"])
                return lat, lng
    except requests.exceptions.RequestException as e:
        print(f"Kakao API 요청 중 에러: {e}")
    return None, None

def auto_map_columns(df):
    mapped_columns = {col: COLUMN_MAPPING_PATTERNS[col] for col in df.columns if col in COLUMN_MAPPING_PATTERNS}
    return df.rename(columns=mapped_columns)

def clean_data(row):
    # 날짜 형식 변환
    if "date" in row and pd.notna(row["date"]):
        try:
            # SQLAlchemy Date 타입에 맞게 datetime.date 객체로 변환
            row["date"] = pd.to_datetime(row["date"], errors="coerce").date()
        except Exception:
            row["date"] = None # 변환 실패 시 None으로 처리

    # 위도와 경도 변환
    for col in ["latitude", "longitude"]:
        if col in row and pd.notna(row[col]):
            try:
                row[col] = float(row[col])
            except (ValueError, TypeError):
                row[col] = None

    # 주소로 위도/경도 변환 (둘 다 없을 때만)
    if (row.get("latitude") is None or row.get("longitude") is None) and pd.notna(row.get("address")):
        lat, lng = kakao_geocode(row["address"], KAKAO_API_KEY)
        if lat and lng:
            row["latitude"] = lat
            row["longitude"] = lng

    return row

def upload_csv_to_mysql(session, csv_file_path):
    try:
        df = pd.read_csv(csv_file_path, encoding="utf-8")
    except UnicodeDecodeError:
        df = pd.read_csv(csv_file_path, encoding="euc-kr")

    df = auto_map_columns(df)
    zones_to_add = []

    for _, row in df.iterrows():
        row_dict = row.to_dict()
        row_dict = clean_data(row_dict)

        if not all(k in row_dict for k in ["latitude", "longitude", "address", "date"]):
            print(f"필수 정보(위도/경도/주소/날짜)가 없어 건너뜁니다: {row_dict.get('description')}")
            continue

        zone = Zone(
            region=str(row_dict.get("region", "")) if pd.notna(row_dict.get("region")) else None,
            type=str(row_dict.get("type", "")) if pd.notna(row_dict.get("type")) else None,
            subtype=str(row_dict.get("subtype", "")) if pd.notna(row_dict.get("subtype")) else None,
            description=str(row_dict.get("description", "")) if pd.notna(row_dict.get("description")) else None,
            address=str(row_dict.get("address")) if pd.notna(row_dict.get("address")) else None,
            latitude=row_dict.get("latitude"),
            longitude=row_dict.get("longitude"),
            size=str(row_dict.get("size", "")) if pd.notna(row_dict.get("size")) else None,
            date=row_dict.get("date"),
            user=None, # CSV에 정보 없으므로 None
            image=None, # CSV에 정보 없으므로 None
        )
        zones_to_add.append(zone)

    try:
        session.add_all(zones_to_add)
        session.commit()
        print(f"파일 {os.path.basename(csv_file_path)}의 데이터 {len(zones_to_add)}건 처리 완료.")
    except IntegrityError:
        session.rollback()
        print(f"파일 {os.path.basename(csv_file_path)} 처리 중 주소 중복 오류 발생. 개별적으로 재시도합니다.")
        for zone in zones_to_add:
            try:
                session.add(zone)
                session.commit()
            except IntegrityError:
                print(f"주소 중복: {zone.address} 데이터는 이미 존재합니다. 건너뜁니다.")
                session.rollback()
    except Exception as e:
        print(f"데이터 저장 중 에러 발생: {e}")
        session.rollback()

def upload_all_csv_in_directory(directory_path):
    session = SessionLocal()
    try:
        for filename in os.listdir(directory_path):
            if filename.endswith(".csv"):
                csv_file_path = os.path.join(directory_path, filename)
                print(f"--- 파일 처리 시작: {filename} ---")
                upload_csv_to_mysql(session, csv_file_path)
    finally:
        session.close()

if __name__ == "__main__":
    data_directory = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'data'))
    print(f"데이터 디렉토리: {data_directory}")
    upload_all_csv_in_directory(data_directory)
    print("\n모든 CSV 파일 처리가 완료되었습니다.")