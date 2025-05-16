import React, { useState, useEffect, useRef } from "react";
import { getAddress } from "./getAddress";

const MainPage = () => {
  const [smokingZones, setSmokingZones] = useState([
    { name: "흡연구역", lat: 37.64906963482072, lng: 127.0630514473348 },
    { name: "흡연구역", lat: 37.64992958530332, lng: 127.06395870684 },
    { name: "흡연구역", lat: 37.64992559411937, lng: 127.06300401143832 },
  ]);
  const mapRef = useRef(null);
  const markersRef = useRef([]);

  useEffect(() => {
    // 카카오맵 API 로드
    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAOMAP_KEY}&autoload=false&libraries=services`;
    script.async = true;
    document.head.appendChild(script);

    script.onload = () => {
      window.kakao.maps.load(async () => {
        const container = document.getElementById("map");
        const options = {
          center: new window.kakao.maps.LatLng(37.648841453089, 127.064317548529),
          level: 2,
        };
        mapRef.current = new window.kakao.maps.Map(container, options);
        drawMarkers(smokingZones);

        // 주소로 새로운 흡연구역 좌표 입력
        try {
          const newCoords = await getAddress("서울 노원구 상계동 205-4");
          setSmokingZones((newZones) => [
            ...newZones,
            { name: "새로운 흡연구역", ...newCoords },
          ]);
        } catch (error) {
          console.error("주소 검색 실패:", error.message);
        }
      });
    };

    return () => {
      script.remove();
      markersRef.current.forEach(marker => marker.setMap(null));
      markersRef.current = [];
    };
    // eslint-disable-next-line
  }, []);

  useEffect(() => {
    if (mapRef.current) {
      drawMarkers(smokingZones);
    }
    // eslint-disable-next-line
  }, [smokingZones]);

  function drawMarkers(zones) {
    markersRef.current.forEach(marker => marker.setMap(null));
    markersRef.current = [];

    zones.forEach((zone) => {
      const markerPosition = new window.kakao.maps.LatLng(zone.lat, zone.lng);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
      });
      marker.setMap(mapRef.current);

      const infoWindow = new window.kakao.maps.InfoWindow({
        content: `<div style="padding:5px;">${zone.name}</div>`,
      });
      window.kakao.maps.event.addListener(marker, "click", () => {
        infoWindow.open(mapRef.current, marker);
      });

      markersRef.current.push(marker);
    });
  }

  return (
    <div>
      <h1 style={{ textAlign: "center", margin: "20px 0" }}>Neogul Map</h1>
      <div id="map" style={{ margin: "0 auto", width: "50%", height: "80vh" }} />
    </div>
  );
};

export default MainPage;