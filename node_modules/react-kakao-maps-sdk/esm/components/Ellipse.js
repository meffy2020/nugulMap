import React, { useMemo, useImperativeHandle, useLayoutEffect } from 'react';
import { useKakaoEvent } from '../hooks/useKakaoEvent.js';
import { useMap } from '../hooks/useMap.js';
import { useKakaoMapsSetEffect } from '../hooks/useKakaoMapsSetEffect.js';

/**
 * Map 상에 타원을 그립니다.
 */
const Ellipse = /*#__PURE__*/React.forwardRef(function Ellipse(_ref, ref) {
  let {
    center,
    rx,
    ry,
    fillColor,
    fillOpacity,
    strokeColor,
    strokeOpacity,
    strokeStyle,
    strokeWeight,
    zIndex,
    onMouseover,
    onMouseout,
    onMousemove,
    onMousedown,
    onClick,
    onCreate
  } = _ref;
  const map = useMap(`Ellipse`);
  const ellipseCenter = useMemo(() => {
    return new kakao.maps.LatLng(center.lat, center.lng);
  }, [center.lat, center.lng]);
  const ellipse = useMemo(() => {
    return new kakao.maps.Ellipse({
      center: ellipseCenter,
      rx,
      ry,
      fillColor,
      fillOpacity,
      strokeColor,
      strokeOpacity,
      strokeStyle,
      strokeWeight,
      zIndex
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useImperativeHandle(ref, () => ellipse, [ellipse]);
  useLayoutEffect(() => {
    ellipse.setMap(map);
    return () => {
      ellipse.setMap(null);
    };
  }, [map, ellipse]);
  useLayoutEffect(() => {
    if (onCreate) onCreate(ellipse);
  }, [ellipse, onCreate]);
  useKakaoMapsSetEffect(ellipse, "setPosition", ellipseCenter);
  useKakaoMapsSetEffect(ellipse, "setRadius", rx, ry);
  useKakaoMapsSetEffect(ellipse, "setZIndex", zIndex);
  useLayoutEffect(() => {
    ellipse.setOptions({
      fillColor,
      fillOpacity,
      strokeColor,
      strokeOpacity,
      strokeStyle,
      strokeWeight
    });
  }, [ellipse, fillColor, fillOpacity, strokeColor, strokeOpacity, strokeStyle, strokeWeight]);
  useKakaoEvent(ellipse, "mouseover", onMouseover);
  useKakaoEvent(ellipse, "mouseout", onMouseout);
  useKakaoEvent(ellipse, "mousemove", onMousemove);
  useKakaoEvent(ellipse, "mousedown", onMousedown);
  useKakaoEvent(ellipse, "click", onClick);
  return null;
});

export { Ellipse };
