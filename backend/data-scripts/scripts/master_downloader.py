import requests
from bs4 import BeautifulSoup
import os
import re
import time

# 설정
# 사용자님이 주신 500개씩 보기 링크 활용
SEARCH_URL = "https://www.data.go.kr/tcs/dss/selectDataSetList.do"
BASE_PARAMS = {
    "dType": "FILE",
    "keyword": "흡연구역",
    "detailKeyword": "",
    "sort": "_score",
    "perPage": "100", # 서버 차단을 방지하기 위해 100개씩 쪼개서 가져옵니다.
}
SAVE_DIR = "data/raw_csv"
os.makedirs(SAVE_DIR, exist_ok=True)

class MasterDownloader:
    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer": "https://www.data.go.kr/"
        }

    def get_pks_from_page(self, page_num):
        """검색 결과 페이지에서 publicDataPk 리스트 추출"""
        params = BASE_PARAMS.copy()
        params["currentPage"] = str(page_num)
        
        print(f"🔍 검색 결과 {page_num}페이지 분석 중...")
        resp = requests.get(SEARCH_URL, params=params, headers=self.headers)
        if resp.status_code != 200:
            print(f"❌ 페이지 로드 실패: {resp.status_code}")
            return []

        soup = BeautifulSoup(resp.text, 'html.parser')
        # 타이틀과 PK가 포함된 링크들 찾기
        items = soup.select(".result-list > li")
        
        found_data = []
        for item in items:
            title_tag = item.select_one(".title")
            link_tag = item.select_one("dt > a")
            
            if title_tag and link_tag:
                title = title_tag.get_text(strip=True)
                # href에서 PK 추출 (예: /tcs/dss/selectFileDataDetailView.do?publicDataPk=15034166)
                pk_match = re.search(r"publicDataPk=(\d+)", link_tag['href'])
                if pk_match:
                    found_data.append({"title": title, "pk": pk_match.group(1)})
        
        return found_data

    def download_file(self, title, pk):
        """PK를 이용해 실제 CSV 다운로드"""
        # 파일명에서 특수문자 제거
        clean_title = re.sub(r'[\\/*?:">|<]', "", title).replace(" ", "_")
        file_path = os.path.join(SAVE_DIR, f"{clean_title}.csv")
        
        if os.path.exists(file_path):
            print(f"⏩ 스킵 (이미 존재): {clean_title}")
            return

        download_url = f"https://www.data.go.kr/tcs/dss/fileDownload.do?publicDataPk={pk}"
        
        try:
            resp = requests.get(download_url, headers=self.headers, timeout=30)
            if resp.status_code == 200 and "html" not in resp.headers.get("Content-Type", ""):
                with open(file_path, 'wb') as f:
                    f.write(resp.content)
                print(f"✅ 다운로드 성공: {clean_title}")
                return True
            else:
                print(f"❌ 다운로드 실패 (데이터 없음): {clean_title}")
        except Exception as e:
            print(f"🔥 에러 발생: {clean_title} - {e}")
        return False

def main():
    downloader = MasterDownloader()
    
    # 1. 상위 3페이지(300개 데이터셋)만 먼저 공략해봅니다.
    all_targets = []
    for p in range(1, 4):
        targets = downloader.get_pks_from_page(p)
        all_targets.extend(targets)
        time.sleep(1)

    print(f"\n🎯 총 {len(all_targets)}개의 다운로드 대상 발견!")
    
    # 2. 일괄 다운로드 시작
    success = 0
    for target in all_targets:
        if downloader.download_file(target['title'], target['pk']):
            success += 1
        time.sleep(0.5) # 서버 매너 대기 시간

    print(f"\n✨ 작업 완료! {success}개의 CSV 파일이 '{SAVE_DIR}'에 저장되었습니다.")

if __name__ == "__main__":
    main()
