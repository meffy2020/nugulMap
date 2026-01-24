import requests
import pandas as pd
import os
import time
from urllib.parse import unquote
from dotenv import load_dotenv

# 최상위 .env 파일 로드
env_path = os.path.join(os.path.dirname(__file__), "../../../.env")
load_dotenv(dotenv_path=env_path)

# 🔑 설정
# 공공데이터 포털에서 받은 키 (Encoding/Decoding 상관없음, 아래에서 처리함)
RAW_KEY = os.getenv("DATA_GO_KR_API_KEY")

class SmartCollector:
    def __init__(self):
        self.results = []
        # 키가 인코딩되어 있다면 디코딩하여 순수 키 확보
        self.decoded_key = unquote(RAW_KEY) if RAW_KEY else None
        
    def fetch_source(self, name, url, params, mapper_func):
        print(f"📡 [{name}] 데이터 수집 시작...")
        if not self.decoded_key:
            print("❗ 에러: API 키가 없습니다.")
            return

        try:
            # 방법 1: 쿼리 파라미터에 직접 박기 (가장 원시적인 방법)
            # requests의 자동 인코딩을 피하기 위해 URL 문자열을 직접 만듭니다.
            param_str = "&".join([f"{k}={v}" for k, v in params.items()])
            full_url = f"{url}?serviceKey={RAW_KEY}&{param_str}"
            
            # 방법 2: 헤더에 넣기 (ODCloud 권장 방식)
            headers = {
                "Authorization": f"Infuser {self.decoded_key}",
                "accept": "*/*"
            }

            # 우선 헤더 방식으로 시도
            resp = requests.get(url, params=params, headers=headers, timeout=15)
            
            # 헤더 방식 실패 시 URL 파라미터 방식으로 재시도
            if resp.status_code != 200:
                resp = requests.get(full_url, timeout=15)

            if resp.status_code == 200:
                raw_data = resp.json()
                items = mapper_func(raw_data)
                
                count = 0
                for item in items:
                    processed = {
                        "region": item.get("region", name),
                        "address": item.get("address", ""),
                        "description": item.get("description", ""),
                        "type": "흡연구역",
                        "subtype": item.get("subtype", "일반"),
                        "latitude": item.get("lat"),
                        "longitude": item.get("lng")
                    }
                    if processed["address"] or processed["latitude"]:
                        self.results.append(processed)
                        count += 1
                print(f"✅ [{name}] 성공: {count}개 수집")
            else:
                print(f"❌ [{name}] 실패: HTTP {resp.status_code}")
                print(f"   응답: {resp.text[:150]}")
                print(f"   💡 팁: 공공데이터 포털에서 '{name}' API 활용신청을 하셨는지 확인해보세요.")
        except Exception as e:
            print(f"❌ [{name}] 에러: {e}")

def map_odcloud(data):
    """ODCloud 규격 파서"""
    return [{
        "address": i.get("소재지도로명주소") or i.get("주소") or i.get("설치 위치") or i.get("설치장소"),
        "description": i.get("흡연구역명") or i.get("시설명") or i.get("장소명"),
        "lat": i.get("위도") or i.get("Y좌표"),
        "lng": i.get("경도") or i.get("X좌표"),
        "subtype": i.get("구분") or i.get("형태")
    } for i in data.get("data", [])]

def main():
    collector = SmartCollector()
    
    # 실제 존재하는 공공데이터 API 리스트 (신청이 필요한 항목들)
    sources = [
        {
            "name": "영등포구 흡연구역",
            "url": "https://api.odcloud.kr/api/15069051/v1/uddi:702cc031-9013-40ad-a285-006cf0ed006d",
            "params": {"page": 1, "perPage": 100}
        },
        {
            "name": "성동구 흡연구역",
            "url": "https://api.odcloud.kr/api/15069051/v1/uddi:3eb03bc9-69ef-478b-ad09-000000000000",
            "params": {"page": 1, "perPage": 100}
        }
    ]
    
    for src in sources:
        collector.fetch_source(src["name"], src["url"], src["params"], map_odcloud)
    
    if collector.results:
        df = pd.DataFrame(collector.results)
        os.makedirs("backend/data-scripts/data", exist_ok=True)
        df.to_csv("backend/data-scripts/data/total_zones.csv", index=False, encoding="utf-8-sig")
        print(f"\n✨ 완료! {len(df)}개 저장됨.")
    else:
        print("\n❌ 수집된 데이터가 없습니다.")

if __name__ == "__main__":
    main()