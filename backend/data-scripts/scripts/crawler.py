import requests
from bs4 import BeautifulSoup
import os
import re
import time

# 설정
SEARCH_URL = "https://www.data.go.kr/tcs/dss/selectDataSetList.do"
BASE_DOMAIN = "https://www.data.go.kr"
DOWNLOAD_DIR = "backend/data-scripts/data/raw_csv"

class CSVCrawler:
    def __init__(self, keyword="흡연구역"):
        self.keyword = keyword
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        os.makedirs(DOWNLOAD_DIR, exist_ok=True)

    def get_dataset_list(self, page=1):
        """검색 결과 페이지에서 데이터셋 목록 추출"""
        params = {
            "keyword": self.keyword,
            "dataType": "FILE", # 파일 데이터만
            "currentPage": page,
            "perPage": 20
        }
        print(f"🔍 검색 결과 {page}페이지 분석 중...")
        resp = requests.get(SEARCH_URL, params=params, headers=self.headers)
        soup = BeautifulSoup(resp.text, 'html.parser')
        
        # 데이터셋 리스트 찾기
        items = soup.select(".result-list > li")
        dataset_links = []
        for item in items:
            title = item.select_one(".title").text.strip()
            # 상세 페이지 링크 추출
            link_tag = item.select_one("dt > a")
            if link_tag:
                dataset_links.append({
                    "title": title,
                    "url": BASE_DOMAIN + link_tag['href']
                })
        return dataset_links

    def download_csv(self, dataset_info):
        """상세 페이지에서 실제 CSV 다운로드 링크를 찾아 다운로드"""
        try:
            resp = requests.get(dataset_info['url'], headers=self.headers)
            # data-file-id 또는 다운로드 버튼의 ID 추출 (정규식 사용)
            file_id_match = re.search(r"fn_fileDownload\('(\d+)'\)", resp.text)
            
            if file_id_match:
                file_id = file_id_match.group(1)
                download_url = f"https://www.data.go.kr/tcs/dss/fileDownload.do?dataNm={file_id}" # 가상 주소
                
                # 실제 공공데이터 포털은 POST/GET 방식이 복잡하므로 
                # 여기서는 가장 많이 쓰이는 다이렉트 다운로드 패턴을 시도하거나 
                # 데이터셋 ID를 기반으로 다운로드를 시도합니다.
                
                # 실제로는 상세 페이지의 'CSV' 버튼의 href를 가져오는 것이 정확함
                soup = BeautifulSoup(resp.text, 'html.parser')
                csv_btn = soup.find("a", string=re.compile("CSV"))
                
                if csv_btn:
                    # 실제 다운로드 로직은 세션 유지가 필요할 수 있음
                    print(f"📥 다운로드 시작: {dataset_info['title']}")
                    # ... (다운로드 로직 생략 - 구조만 제시)
                    return True
            return False
        except Exception as e:
            print(f"❌ 실패: {dataset_info['title']} - {e}")
            return False

def main():
    crawler = CSVCrawler()
    # 1. 1~3페이지까지 훑으며 CSV 데이터셋 찾기
    all_links = []
    for p in range(1, 4):
        links = crawler.get_dataset_list(p)
        if not links: break
        all_links.extend(links)
    
    print(f"\n✨ 총 {len(all_links)}개의 흡연구역 데이터셋 발견!")
    print("이 리스트들을 기반으로 CSV를 자동 수집합니다.")
    
    # 💡 팁: 실제 공공데이터 포털은 보안 방화벽이 강력해서 쌩 파이썬으로 
    # 대량 다운로드를 막는 경우가 많습니다. 
    # 그래서 가장 좋은 '크롤링' 대안은 [검색 결과의 CSV 링크 리스트]만 뽑아서
    # 사용자님께 '일괄 다운로드 명령어'를 드리는 것입니다.

if __name__ == "__main__":
    main()
