// frontend/lib/api.ts

// 백엔드의 ZoneResponse DTO와 일치하는 타입 정의
export interface SmokingZone {
  id: number;
  region: string;
  type: string;
  subtype: string;
  description: string;
  latitude: number;
  longitude: number;
  address: string;
  user: string;
  image: string | null;
}


// Zone 생성 시 요청 DTO에 맞는 타입 정의
export type CreateZonePayload = Omit<SmokingZone, 'id' | 'image'>;

/**
 * 특정 위치 주변의 흡연구역 목록을 서버에서 가져옵니다.
 * @param lat - 검색 중심의 위도
 * @param lon - 검색 중심의 경도
 * @param radius - 검색 반경 (km)
 * @returns SmokingZone 객체의 배열
 */
export async function fetchZones(lat: number, lon: number, radius: number = 1.0): Promise<SmokingZone[]> {
  const response = await fetch(`/api/zones?latitude=${lat}&longitude=${lon}&radius=${radius}`);

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  return response.json();
}

/**
 * 새로운 흡연구역을 서버에 생성합니다.
 * @param zoneData - 생성할 흡연구역의 데이터
 * @param imageFile - 업로드할 이미지 파일 (선택 사항)
 * @returns 생성된 SmokingZone 객체
 */
export async function createZone(zoneData: CreateZonePayload, imageFile?: File): Promise<SmokingZone> {
  const formData = new FormData();

  // ‼️‼️‼️ 수정된 부분: 파트 이름을 'request'에서 'data'로 변경 ‼️‼️‼️
  formData.append('data', new Blob([JSON.stringify(zoneData)], { type: 'application/json' }));

  // 이미지 파일이 있으면 'image' 파트에 추가
  if (imageFile) {
    formData.append('image', imageFile);
  }

  // multipart/form-data 요청
  const response = await fetch('/api/zones', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API call failed: ${response.status} ${errorText}`);
  }

  return response.json();
}