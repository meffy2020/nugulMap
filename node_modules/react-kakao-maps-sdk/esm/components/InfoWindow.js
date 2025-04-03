import React, { useMemo, useImperativeHandle, useLayoutEffect } from 'react';
import ReactDOM from 'react-dom';
import { useKakaoMapsSetEffect } from '../hooks/useKakaoMapsSetEffect.js';

const InfoWindow = /*#__PURE__*/React.forwardRef(function InfoWindow(_ref, ref) {
  let {
    map,
    position,
    marker,
    children,
    altitude,
    disableAutoPan,
    range,
    removable,
    zIndex,
    onCreate
  } = _ref;
  const infoWindow = useMemo(() => {
    const container = document.createElement("div");
    container.style.display = "none";
    const kakaoInfoWindow = new kakao.maps.InfoWindow({
      altitude: altitude,
      disableAutoPan: disableAutoPan,
      range: range,
      removable: removable,
      zIndex: zIndex,
      content: container,
      position: position
    });
    return kakaoInfoWindow;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disableAutoPan, removable]);
  const container = useMemo(() => infoWindow.getContent(), [infoWindow]);
  useImperativeHandle(ref, () => infoWindow, [infoWindow]);
  useLayoutEffect(() => {
    infoWindow.open(map, marker);
    return () => {
      infoWindow.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, marker]);
  useLayoutEffect(() => {
    if (onCreate) onCreate(infoWindow);
  }, [infoWindow, onCreate]);
  useKakaoMapsSetEffect(infoWindow, "setPosition", position);
  useKakaoMapsSetEffect(infoWindow, "setAltitude", altitude);
  useKakaoMapsSetEffect(infoWindow, "setRange", range);
  useKakaoMapsSetEffect(infoWindow, "setZIndex", zIndex);
  return /*#__PURE__*/ReactDOM.createPortal(children, container.parentElement ?? container);
});

export { InfoWindow };
