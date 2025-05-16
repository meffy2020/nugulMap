export const getAddress = (address) => {
  return new Promise((resolve, reject) => {
    // 방어 코드 추가
    if (
      !window.kakao ||
      !window.kakao.maps ||
      !window.kakao.maps.services
    ) {
      reject(new Error("카카오맵 서비스가 아직 준비되지 않았습니다."));
      return;
    }

    const geocoder = new window.kakao.maps.services.Geocoder();

    geocoder.addressSearch(address, (result, status) => {
      if (status === window.kakao.maps.services.Status.OK) {
        const coords = {
          lat: parseFloat(result[0].y),
          lng: parseFloat(result[0].x),
        };
        resolve({ name: "새로운 흡연구역", ...coords });
      } else {
        reject(new Error("주소를 찾을 수 없습니다."));
      }
    });
  });
};