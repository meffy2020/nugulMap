import requests
import os
import time

# 저장 경로
SAVE_DIR = "data/raw_csv" # 터미널 위치(data-scripts) 기준
os.makedirs(SAVE_DIR, exist_ok=True)

# 📡 전국 지자체별 흡연구역 '파일 데이터(CSV)' 전용 ID 리스트
# 이 ID들은 오픈API ID가 아니라 '파일 다운로드' 전용 PK입니다.
DATASETS = {
    "서울_영등포구": "15034166",
    "서울_강남구": "3070834",
    "서울_서초구": "15034544",
    "경기_수원시": "15034544", # 수원시는 다른 데이터셋에 묶여있을 수 있음
    "서울_성동구": "15034166",
    "전국_표준데이터": "15041174" # 유저분이 없다고 하셨지만 포털엔 ID가 존재하므로 시도
}

def download_files():
    # 공공데이터 포털 다운로드 시 필요한 최소한의 헤더
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://www.data.go.kr/tcs/dss/selectDataSetList.do"
    }
    
    print("🚀 전국 흡연구역 CSV 일괄 다운로드 시작 (파일 전용 ID 사용)...")
    
    success_count = 0
    for name, pk in DATASETS.items():
        # 파일 다운로드 공식 엔드포인트
        url = f"https://www.data.go.kr/tcs/dss/fileDownload.do?publicDataPk={pk}"
        file_path = os.path.join(SAVE_DIR, f"{name}.csv")
        
        print(f"📥 다운로드 시도: {name} (ID: {pk})...")
        try:
            # stream=True를 사용해 대용량 파일 대응
            resp = requests.get(url, headers=headers, timeout=30, stream=True)
            
            if resp.status_code == 200:
                # 응답 내용이 HTML이면(에러 페이지) 실패로 간주
                if "html" in resp.headers.get("Content-Type", ""):
                    print(f"❌ 실패: {name} - 파일을 찾을 수 없거나 에러 페이지가 반환됨.")
                    continue
                    
                with open(file_path, 'wb') as f:
                    for chunk in resp.iter_content(chunk_size=8192):
                        f.write(chunk)
                
                # 파일 크기 확인 (0바이트면 실패)
                if os.path.getsize(file_path) > 100:
                    print(f"✅ 저장 완료: {file_path} ({os.path.getsize(file_path) // 1024} KB)")
                    success_count += 1
                else:
                    os.remove(file_path)
                    print(f"⚠️ 실패: {name} - 다운로드된 파일이 너무 작습니다.")
            else:
                print(f"❌ 실패: {name} (HTTP {resp.status_code})")
            
            time.sleep(1.5) # 서버 차단 방지
        except Exception as e:
            print(f"🔥 에러 발생 ({name}): {e}")

    print(f"\n✨ 작업 종료: {success_count}개의 파일 수집 완료.")

if __name__ == "__main__":
    download_files()