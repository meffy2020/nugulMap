/// <reference types="kakao.maps.d.ts" />
import { _Parameters } from "../types";
/**
 * kakao.maps.event.EventTarget의 메소드를 호출하는 hook!
 *
 * @param target
 * @param method 메소드 이름 (ex. "setCenter")
 * @param args 메소드 arguments 이때 넘겨지는 객체가 `undefined` 일 수 도있는 경우 `!` 연산자를 통해서 무시할 것.!
 */
export declare const useKakaoMapsSetEffect: <T extends kakao.maps.event.EventTarget, K extends keyof T>(target: T | undefined, method: K, ...args: _Parameters<T[K]>) => void;
