import re
import os

# 파일 경로
SOURCE_FILE = "data/page_source.txt"
OUTPUT_SCRIPT = "scripts/fast_download.sh"

def extract_pks():
    if not os.path.exists(SOURCE_FILE):
        print(f"❗ '{SOURCE_FILE}' 파일이 없습니다. 브라우저 소스를 복사해서 만들어주세요.")
        return

    with open(SOURCE_FILE, 'r', encoding='utf-8') as f:
        content = f.read()

    # publicDataPk 추출 (패턴: publicDataPk=15034166)
    pks = re.findall(r"publicDataPk=(\d+)", content)
    # 중복 제거 및 순서 유지
    unique_pks = list(dict.fromkeys(pks))

    if not unique_pks:
        print("❌ PK를 찾지 못했습니다. '페이지 소스 보기' 내용을 제대로 붙여넣으셨나요?")
        return

    print(f"🎯 총 {len(unique_pks)}개의 데이터셋 PK 추출 성공!")

    # 일괄 다운로드용 쉘 스크립트 작성
    with open(OUTPUT_SCRIPT, 'w') as f:
        f.write("#!/bin/bash\n")
        f.write("mkdir -p data/raw_csv\n")
        f.write("echo '🚀 대량 다운로드 시작...'\n")
        for i, pk in enumerate(unique_pks):
            f.write(f"echo '[{i+1}/{len(unique_pks)}] ID {pk} 받는 중...'\n")
            f.write(f"curl -L -H 'User-Agent: Mozilla/5.0' 'https://www.data.go.kr/tcs/dss/fileDownload.do?publicDataPk={pk}' -o 'data/raw_csv/data_{pk}.csv'\n")
            f.write("sleep 0.5\n")
        f.write("echo '✨ 모두 완료되었습니다!'\n")

    print(f"✅ 다운로드 설계도 완성: '{OUTPUT_SCRIPT}'")
    print(f"👉 터미널에서 'bash {OUTPUT_SCRIPT}'를 실행하면 500개가 촤르륵 받아집니다!")

if __name__ == "__main__":
    extract_pks()
