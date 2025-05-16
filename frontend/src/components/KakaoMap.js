import React, { useEffect, useMemo } from "react";

const KakaoMap = ({ smokingZones }) => {
  const stableSmokingZones = useMemo(() => smokingZones, [smokingZones]);

  useEffect(() => {
    // 카카오맵 API 로드
    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAOMAP_KEY}&autoload=false`;
    script.async = true;
    document.head.appendChild(script);

    script.onload = () => {
      window.kakao.maps.load(() => {
        const container = document.getElementById("map");
        const options = {
          center: new window.kakao.maps.LatLng(37.648841453089, 127.064317548529),
          level: 2,
        };
        const map = new window.kakao.maps.Map(container, options);

        //smokingZones 배열로 마커 추가
        stableSmokingZones.forEach((zone) => {
          const markerPosition = new window.kakao.maps.LatLng(zone.lat, zone.lng);
          const marker = new window.kakao.maps.Marker({
            position: markerPosition,
          });
          marker.setMap(map);

          // 마커 클릭 이벤트
          const infoWindow = new window.kakao.maps.InfoWindow({
            content: `<div style="padding:5px;">${zone.name}</div>`,
          });
          window.kakao.maps.event.addListener(marker, "click", () => {
            infoWindow.open(map, marker);
          });
        });
      });
    };

    return () => {
      script.remove();
    };
  }, [stableSmokingZones]); // 의존성 배열

  return <div id="map" style={{ margin: "0 auto", width: "50%", height: "80vh" }} />;
};

export default KakaoMap;