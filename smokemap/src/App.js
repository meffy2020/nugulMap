import { useEffect } from "react";

function MapPage() {
  useEffect(() => {
    const script = document.createElement("script");
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAO_MAP_KEY}&libraries=services`;
    script.async = true;
    script.onload = () => {
      const container = document.getElementById("map");
      const options = {
        center: new window.kakao.maps.LatLng(37.5665, 126.978),
        level: 3,
      };
      new window.kakao.maps.Map(container, options);
    };
    document.body.appendChild(script);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100 p-4">
      {/* 상단 헤더 */}
      <header className="w-full max-w-lg bg-white shadow-md rounded-lg p-4 text-center">
        <h1 className="text-xl font-bold text-gray-700">흡연구역 지도</h1>
      </header>=

      {/* 검색창 */}
      <div className="w-full max-w-lg mt-4">
        <input
          type="text"
          placeholder="장소 검색"
          className="w-full p-3 rounded-lg border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
      </div>

      {/* 지도 */}
      <div id="map" className="w-full max-w-lg h-96 bg-gray-300 mt-4 rounded-lg shadow-lg"></div>

      {/* 하단 버튼 */}
      <button className="mt-4 px-6 py-3 bg-blue-500 text-white rounded-lg shadow hover:bg-blue-600">
        내 위치에서 찾기
      </button>
    </div>
  );
}

export default MapPage;