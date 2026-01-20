import csv
import requests
import os
import glob

# API URL
BASE_URL = "https://api.nugulmap.com/api/test/zones"

def get_column_mapping(headers):
    mapping = {
        "address": None,
        "description": None,
        "region": None,
        "type": None,
        "subtype": None,
        "latitude": None,
        "longitude": None,
        "size": None
    }
    
    # Try to guess mapping based on keywords
    for h in headers:
        h_clean = h.strip()
        if any(kw in h_clean for kw in ['주소', '위치', 'Address']):
            if mapping["address"] is None: mapping["address"] = h
        if any(kw in h_clean for kw in ['구분', '시설', '형태', 'Type']):
            if mapping["type"] is None: mapping["type"] = h
        if any(kw in h_clean for kw in ['상세', '위치', 'Description']):
            if mapping["description"] is None: mapping["description"] = h
        if any(kw in h_clean for kw in ['위도', 'Latitude', 'Y']):
            if mapping["latitude"] is None: mapping["latitude"] = h
        if any(kw in h_clean for kw in ['경도', 'Longitude', 'X']):
            if mapping["longitude"] is None: mapping["longitude"] = h
        if any(kw in h_clean for kw in ['자치구', '지역', 'Region']):
            if mapping["region"] is None: mapping["region"] = h
        if any(kw in h_clean for kw in ['규모', 'Size']):
            if mapping["size"] is None: mapping["size"] = h
            
    return mapping

def upload_csv(file_path):
    print(f"\n📂 Processing: {os.path.basename(file_path)}")
    
    try:
        # Detect encoding
        encoding = 'utf-8-sig'
        with open(file_path, mode='r', encoding=encoding) as f:
            reader = csv.DictReader(f)
            mapping = get_column_mapping(reader.fieldnames)
            
            print(f"   Mapping: {mapping}")

            if not mapping["address"]:
                print(f"⚠️ Could not find address column in {file_path}. Skipping.")
                return

            count = 0
            duplicates = 0
            for row in reader:
                try:
                    address = row.get(mapping["address"], "").strip()
                    if not address or len(address) < 2 or address == "실외":
                        continue

                    # Basic data construction
                    data = {
                        "address": address,
                        "description": row.get(mapping["description"], "") if mapping["description"] else "",
                        "region": row.get(mapping["region"], "서울특별시") if mapping["region"] else "서울특별시",
                        "type": row.get(mapping["type"], "흡연구역") if mapping["type"] else "흡연구역",
                        "subtype": row.get(mapping["subtype"], "") if mapping["subtype"] else "",
                        "size": row.get(mapping["size"], "중형") if mapping["size"] else "중형",
                        "creator": "system@nugulmap.com"
                    }
                    
                    if mapping["latitude"] and mapping["longitude"]:
                        try:
                            data["latitude"] = float(row.get(mapping["latitude"], 0))
                            data["longitude"] = float(row.get(mapping["longitude"], 0))
                        except:
                            continue
                    else:
                        continue
                    
                    if data['latitude'] == 0 or data['longitude'] == 0:
                        continue

                    response = requests.post(BASE_URL, data=data, files={'image': (None, '')})
                    if response.status_code == 200:
                        count += 1
                    elif response.status_code == 409:
                        duplicates += 1
                    else:
                        print(f"❌ Failed: {address} ({response.status_code})")
                except Exception as e:
                    pass
            print(f"✅ Result: {count} uploaded, {duplicates} duplicates in {os.path.basename(file_path)}")
    except Exception as e:
        print(f"🔥 Error reading file: {e}")

def run_migration():
    csv_files = glob.glob("data/*.csv")
    for file in csv_files:
        upload_csv(file)

if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)) + "/..")
    run_migration()
