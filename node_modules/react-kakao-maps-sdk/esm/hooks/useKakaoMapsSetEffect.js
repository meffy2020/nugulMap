import { useIsomorphicLayoutEffect } from './useIsomorphicLayoutEffect.js';

/**
 * kakao.maps.event.EventTarget의 메소드를 호출하는 hook!
 *
 * @param target
 * @param method 메소드 이름 (ex. "setCenter")
 * @param args 메소드 arguments 이때 넘겨지는 객체가 `undefined` 일 수 도있는 경우 `!` 연산자를 통해서 무시할 것.!
 */
const useKakaoMapsSetEffect = function (target, method) {
  for (var _len = arguments.length, args = new Array(_len > 2 ? _len - 2 : 0), _key = 2; _key < _len; _key++) {
    args[_key - 2] = arguments[_key];
  }
  useIsomorphicLayoutEffect(() => {
    if (!target || args.every(arg => typeof arg === "undefined")) return;
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    target[method].call(target, ...args);
  }, [target, method, ...args]);
};

export { useKakaoMapsSetEffect };
