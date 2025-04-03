import React, { useMemo, useImperativeHandle, useLayoutEffect } from 'react';
import ReactDOM from 'react-dom';
import { useRoadview } from '../hooks/useRoadview.js';
import { useKakaoMapsSetEffect } from '../hooks/useKakaoMapsSetEffect.js';

/**
 * Roadview에 CustomOverlay를 올릴 때 사용하는 컴포넌트 입니다.
 * `onCreate` 이벤트 또는 `ref` 객체를 통해서 `CustomOverlay` 객체에 직접 접근 및 초기 설정 작업을 지정할 수 있습니다.
 */
const CustomOverlayRoadview = /*#__PURE__*/React.forwardRef(function CustomOverlayRoadview(_ref, ref) {
  let {
    position,
    children,
    clickable,
    xAnchor,
    yAnchor,
    zIndex,
    altitude,
    range,
    onCreate
  } = _ref;
  const roadview = useRoadview(`CustomOverlayRoadview`);
  const overlayPosition = useMemo(() => {
    if ("lat" in position) {
      return new kakao.maps.LatLng(position.lat, position.lng);
    }
    return new kakao.maps.Viewpoint(position.pan, position.tilt, position.zoom, position.panoId);
    /* eslint-disable react-hooks/exhaustive-deps */
  }, [/* eslint-disable @typescript-eslint/ban-ts-comment */
  /* @ts-ignore */
  position.lat, /* @ts-ignore */
  position.lng, /* @ts-ignore */
  position.pan, /* @ts-ignore */
  position.tilt, /* @ts-ignore */
  position.zoom, /* @ts-ignore */
  position.panoId
  /* eslint-enable @typescript-eslint/ban-ts-comment */]);
  /* eslint-enable react-hooks/exhaustive-deps */

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
    if (!roadview) return;
    overlay.setMap(roadview);
    return () => {
      overlay.setMap(null);
    };
  }, [overlay, roadview]);
  useLayoutEffect(() => {
    if (onCreate) onCreate(overlay);
  }, [overlay, onCreate]);
  useKakaoMapsSetEffect(overlay, "setPosition", overlayPosition);
  useKakaoMapsSetEffect(overlay, "setZIndex", zIndex);
  useKakaoMapsSetEffect(overlay, "setAltitude", altitude);
  useKakaoMapsSetEffect(overlay, "setRange", range);
  return container.parentElement && /*#__PURE__*/ReactDOM.createPortal(children, container.parentElement);
});

export { CustomOverlayRoadview };
