import React, { useContext, useMemo, useImperativeHandle, useLayoutEffect } from 'react';
import ReactDOM from 'react-dom';
import { useMap } from '../hooks/useMap.js';
import { KakaoMapMarkerClustererContext } from './MarkerClusterer.js';
import { useKakaoMapsSetEffect } from '../hooks/useKakaoMapsSetEffect.js';

/**
 * Map에 CustomOverlay를 올릴 때 사용하는 컴포넌트 입니다.
 * `onCreate` 이벤트 또는 `ref` 객체를 통해서 `CustomOverlay` 객체에 직접 접근 및 초기 설정 작업을 지정할 수 있습니다.
 */
const CustomOverlayMap = /*#__PURE__*/React.forwardRef(function CustomOverlayMap(_ref, ref) {
  let {
    position,
    children,
    clickable,
    xAnchor,
    yAnchor,
    zIndex,
    onCreate
  } = _ref;
  const markerCluster = useContext(KakaoMapMarkerClustererContext);
  const map = useMap(`CustomOverlayMap`);
  const overlayPosition = useMemo(() => {
    return new kakao.maps.LatLng(position.lat, position.lng);
  }, [position.lat, position.lng]);
  const overlay = useMemo(() => {
    const container = document.createElement("div");
    container.style.display = "none";
    const KakaoCustomOverlay = new kakao.maps.CustomOverlay({
      clickable: clickable,
      xAnchor: xAnchor,
      yAnchor: yAnchor,
      zIndex: zIndex,
      position: overlayPosition,
      content: container
    });
    return KakaoCustomOverlay;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clickable, xAnchor, yAnchor]);
  const container = useMemo(() => overlay.getContent(), [overlay]);
  useImperativeHandle(ref, () => overlay, [overlay]);
  useLayoutEffect(() => {
    if (!map) return;
    if (markerCluster) {
      markerCluster.addMarker(overlay, true);
    } else {
      overlay.setMap(map);
    }
    return () => {
      if (markerCluster) {
        markerCluster.removeMarker(overlay, true);
      } else {
        overlay.setMap(null);
      }
    };
  }, [map, markerCluster, overlay]);
  useLayoutEffect(() => {
    if (onCreate) onCreate(overlay);
  }, [overlay, onCreate]);
  useKakaoMapsSetEffect(overlay, "setPosition", overlayPosition);
  useKakaoMapsSetEffect(overlay, "setZIndex", zIndex);
  return container.parentElement && /*#__PURE__*/ReactDOM.createPortal(children, container.parentElement);
});

export { CustomOverlayMap };
