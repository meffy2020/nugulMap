import React, { useEffect, useState } from "react";

const Map = () => {
  const [isLoaded, setIsLoaded] = useState(false);
  const [userLocation, setUserLocation] = useState(null); // 사용자 위치 저장

  useEffect(() => {
    if (!window.kakao || !window.kakao.maps) {
      const script = document.createElement("script");
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAO_MAP_KEY}&libraries=services`;
      script.async = true;
      script.onload = () => setIsLoaded(true);
      document.head.appendChild(script);
    } else {
      setIsLoaded(true);
    }
  }, []);

  useEffect(() => {
    if (isLoaded && window.kakao && window.kakao.maps) {
      const container = document.getElementById("map");
      const options = {
        center: new window.kakao.maps.LatLng(37.5665, 126.9780), // 기본 위치: 서울시청
        level: 5,
      };
      const map = new window.kakao.maps.Map(container, options);

      // 🚀 사용자 위치 가져오기
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            const lat = position.coords.latitude;
            const lng = position.coords.longitude;
            setUserLocation({ lat, lng });

            // 사용자 위치 마커 추가
            const userMarker = new window.kakao.maps.Marker({
              position: new window.kakao.maps.LatLng(lat, lng),
              map: map,
            });

            // 지도 중심을 사용자 위치로 이동
            map.setCenter(new window.kakao.maps.LatLng(lat, lng));
          },
          (error) => {
            console.error("사용자 위치를 가져올 수 없습니다.", error);
          }
        );
      }

      // 🚬 흡연구역 마커 데이터 (나중에 백엔드에서 가져올 것)
      const smokeZones = [
        { lat: 37.5665, lng: 126.9780, name: "서울시청 앞" },
        { lat: 37.5700, lng: 126.9768, name: "덕수궁 돌담길" },
      ];

      smokeZones.forEach((zone) => {
        const marker = new window.kakao.maps.Marker({
          position: new window.kakao.maps.LatLng(zone.lat, zone.lng),
          map: map,
        });

        const infowindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:5px;font-size:14px;">${zone.name}</div>`,
        });

        window.kakao.maps.event.addListener(marker, "click", () => {
          infowindow.open(map, marker);
        });
      });
    }
  }, [isLoaded]);

  return <div id="map" style={{ width: "100%", height: "500px", backgroundColor: "#f0f0f0" }} />;
};

export default Map;