import React, { useState, useEffect, useRef } from "react";
import { getAddress } from "./getAddress";

const MainPage = () => {
  const [smokingZones, setSmokingZones] = useState([
    { name: "흡연구역", lat: 37.64906963482072, lng: 127.0630514473348 },
    { name: "흡연구역", lat: 37.64992958530332, lng: 127.06395870684 },
    { name: "흡연구역", lat: 37.64992559411937, lng: 127.06300401143832 },
  ]);
  const [address, setAddress] = useState(""); // 주소 입력값 상태
  const [clickedAddress, setClickedAddress] = useState(""); // 클릭한 위치의 주소
  const mapRef = useRef(null);
  const markersRef = useRef([]);

  useEffect(() => {
    // 카카오맵 API 로드
    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAOMAP_KEY}&autoload=false&libraries=services`;
    script.async = true;
    document.head.appendChild(script);

    script.onload = () => {
      window.kakao.maps.load(() => {
        const container = document.getElementById("map");
        const options = {
          center: new window.kakao.maps.LatLng(37.648841453089, 127.064317548529), // 시작 중심 좌표
          level: 2,
        };
        const map = new window.kakao.maps.Map(container, options);
        mapRef.current = map;
        drawMarkers(smokingZones);

        // 지도 클릭 이벤트 등록
        window.kakao.maps.event.addListener(map, "click", function(mouseEvent) {
          const latlng = mouseEvent.latLng;
          const geocoder = new window.kakao.maps.services.Geocoder();
          geocoder.coord2Address(latlng.getLng(), latlng.getLat(), function(result, status) {
            if (status === window.kakao.maps.services.Status.OK) {
              const addr = result[0].address.address_name;
              setClickedAddress(addr);
            } else {
              setClickedAddress("주소를 찾을 수 없습니다.");
            }
          });
        });
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

  // 주소 입력 후 버튼 클릭 시 실행되는 함수
  const handleAddZone = async () => {
    if (!address.trim()) return;
    try {
      const newCoords = await getAddress(address);
      setSmokingZones((zones) => [
        ...zones,
        { name: "새로운 흡연구역", ...newCoords },
      ]);
      setAddress(""); // 입력창 초기화
    } catch (error) {
      alert("주소 검색 실패: " + error.message);
    }
  };

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
      <div style={{ textAlign: "center", marginTop: "10px", color: "#333" }}>
        {clickedAddress && (
          <div>
            <b>클릭한 위치 주소:</b> {clickedAddress}
          </div>
        )}
      </div>
      <div style={{ textAlign: "center", marginBottom: "10px" }}>
        <input
          type="text"
          value={address}
          onChange={e => setAddress(e.target.value)}
          placeholder="주소를 입력하세요"
          style={{ width: "300px", padding: "8px" }}
        />
        <button onClick={handleAddZone} style={{ marginLeft: "8px", padding: "8px 16px" }}>
          흡연구역 추가
        </button>
      </div>
    </div>
  );
};

export default MainPage;