import { useEffect, useMemo, useRef, useState } from "react"
import { ActivityIndicator, Image, StyleSheet, Text, View } from "react-native"
import WebView, { type WebViewMessageEvent } from "react-native-webview"
import Constants from "expo-constants"
import type { MapRegion, SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"

interface MapScreenProps {
  region: MapRegion
  zones: SmokingZone[]
  selectedZone: SmokingZone | null
  isLoading: boolean
  onRegionChangeComplete: (region: MapRegion) => void
  onSelectZone: (zone: SmokingZone) => void
}

const EXPO_EXTRA = (Constants.expoConfig?.extra || {}) as {
  kakaoJavascriptKey?: string
  kakaoWebviewBaseUrl?: string
}
const KAKAO_JS_KEY =
  process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY ||
  process.env.NEXT_PUBLIC_KAKAOMAP_APIKEY ||
  EXPO_EXTRA.kakaoJavascriptKey ||
  ""
const KAKAO_WEBVIEW_BASE_URL =
  process.env.EXPO_PUBLIC_KAKAO_WEBVIEW_BASE_URL ||
  EXPO_EXTRA.kakaoWebviewBaseUrl ||
  "https://nugulmap.local"
const REGION_SYNC_EPS = 0.00002

function isSimilarRegion(a: MapRegion, b: MapRegion): boolean {
  return (
    Math.abs(a.latitude - b.latitude) < REGION_SYNC_EPS &&
    Math.abs(a.longitude - b.longitude) < REGION_SYNC_EPS &&
    Math.abs(a.latitudeDelta - b.latitudeDelta) < REGION_SYNC_EPS &&
    Math.abs(a.longitudeDelta - b.longitudeDelta) < REGION_SYNC_EPS
  )
}

function buildMapHtml(appKey: string, initialRegion: MapRegion): string {
  return `
<!doctype html>
<html>
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
    <style>
      html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; overflow: hidden; background: #fff; }
    </style>
    <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false"></script>
  </head>
  <body>
    <div id="map"></div>
    <script>
      (function () {
        var map = null;
        var markers = [];
        var markerImage = null;
        var queued = [];

        function post(payload) {
          if (window.ReactNativeWebView) {
            window.ReactNativeWebView.postMessage(JSON.stringify(payload));
          }
        }

        function clearMarkers() {
          for (var i = 0; i < markers.length; i += 1) {
            markers[i].setMap(null);
          }
          markers = [];
        }

        function emitRegion() {
          if (!map) return;
          var center = map.getCenter();
          var bounds = map.getBounds();
          var sw = bounds.getSouthWest();
          var ne = bounds.getNorthEast();
          post({
            type: "regionChange",
            latitude: center.getLat(),
            longitude: center.getLng(),
            latitudeDelta: Math.abs(ne.getLat() - sw.getLat()) || 0.05,
            longitudeDelta: Math.abs(ne.getLng() - sw.getLng()) || 0.05,
          });
        }

        function setMarkerImage(uri) {
          if (!uri) {
            markerImage = null;
            return;
          }

          markerImage = new kakao.maps.MarkerImage(
            uri,
            new kakao.maps.Size(28, 28),
            { offset: new kakao.maps.Point(14, 28) }
          );
        }

        function renderMarkers(zones) {
          if (!map) return;
          clearMarkers();

          for (var i = 0; i < zones.length; i += 1) {
            var zone = zones[i];
            if (!zone) continue;
            var lat = Number(zone.latitude);
            var lng = Number(zone.longitude);
            if (!isFinite(lat) || !isFinite(lng)) continue;

            var marker = new kakao.maps.Marker({
              map: map,
              position: new kakao.maps.LatLng(lat, lng),
              title: zone.subtype || "흡연구역",
              image: markerImage || undefined,
            });

            (function (zoneId) {
              kakao.maps.event.addListener(marker, "click", function () {
                post({ type: "markerPress", id: zoneId });
              });
            })(zone.id);

            markers.push(marker);
          }
        }

        function moveCenter(center) {
          if (!map || !center) return;
          var lat = Number(center.latitude);
          var lng = Number(center.longitude);
          if (!isFinite(lat) || !isFinite(lng)) return;

          map.setCenter(new kakao.maps.LatLng(lat, lng));
        }

        function applyPayload(payload) {
          if (!map) {
            queued.push(payload);
            return;
          }

          if (payload.type === "SET_ZONES") {
            setMarkerImage(payload.markerImageUri);
            renderMarkers(Array.isArray(payload.zones) ? payload.zones : []);
            return;
          }

          if (payload.type === "MOVE_CENTER") {
            moveCenter(payload.center);
            return;
          }
        }

        function onMessage(raw) {
          try {
            var payload = JSON.parse(raw);
            applyPayload(payload);
          } catch (_error) {}
        }

        window.addEventListener("message", function (event) {
          onMessage(event.data);
        });

        document.addEventListener("message", function (event) {
          onMessage(event.data);
        });

        if (!window.kakao || !window.kakao.maps) {
          post({ type: "error", message: "Kakao SDK not loaded. Check app key and allowed domain." });
          return;
        }

        kakao.maps.load(function () {
          map = new kakao.maps.Map(document.getElementById("map"), {
            center: new kakao.maps.LatLng(${initialRegion.latitude}, ${initialRegion.longitude}),
            level: 4,
          });

          kakao.maps.event.addListener(map, "idle", emitRegion);

          if (queued.length) {
            for (var i = 0; i < queued.length; i += 1) {
              applyPayload(queued[i]);
            }
            queued = [];
          }

          emitRegion();
          post({ type: "ready" });
        });

        window.addEventListener("error", function (e) {
          post({ type: "error", message: e && e.message ? e.message : "Unknown script error" });
        });
      })();
    </script>
  </body>
</html>
`
}

export function MapScreen({
  region,
  zones,
  selectedZone,
  isLoading,
  onRegionChangeComplete,
  onSelectZone,
}: MapScreenProps) {
  const webViewRef = useRef<WebView>(null)
  const [isMapReady, setIsMapReady] = useState(false)
  const [mapError, setMapError] = useState<string | null>(null)
  const lastRegionFromMap = useRef<MapRegion | null>(null)
  const markerImageUri = useMemo(
    () => Image.resolveAssetSource(require("../../assets/images/pin.png")).uri,
    [],
  )

  const mapHtml = useMemo(() => buildMapHtml(KAKAO_JS_KEY, region), [])

  useEffect(() => {
    if (!KAKAO_JS_KEY) {
      setMapError("Kakao JavaScript key is missing. Check mobile/.env")
      return
    }
    setMapError(null)
  }, [])

  const postMessageToMap = (payload: unknown) => {
    if (!isMapReady || !webViewRef.current) return
    webViewRef.current.postMessage(JSON.stringify(payload))
  }

  useEffect(() => {
    if (!isMapReady) return

    postMessageToMap({
      type: "SET_ZONES",
      zones,
      markerImageUri,
    })
  }, [zones, isMapReady])

  useEffect(() => {
    if (!isMapReady) return

    const last = lastRegionFromMap.current
    if (last && isSimilarRegion(last, region)) {
      return
    }

    postMessageToMap({
      type: "MOVE_CENTER",
      center: region,
    })
  }, [region, isMapReady])

  useEffect(() => {
    if (!isMapReady || !selectedZone) return

    postMessageToMap({
      type: "MOVE_CENTER",
      center: {
        latitude: selectedZone.latitude,
        longitude: selectedZone.longitude,
      },
    })
  }, [selectedZone, isMapReady])

  const handleMessage = (event: WebViewMessageEvent) => {
    try {
      const data = JSON.parse(event.nativeEvent.data)
      if (data?.type === "ready") {
        setIsMapReady(true)
        setMapError(null)
        return
      }

      if (data?.type === "error") {
        setMapError(String(data.message || "Failed to load kakao map"))
        return
      }

      if (data?.type === "markerPress") {
        const zone = zones.find((item) => item.id === Number(data.id))
        if (zone) {
          onSelectZone(zone)
        }
        return
      }

      if (data?.type === "regionChange") {
        const nextRegion: MapRegion = {
          latitude: Number(data.latitude),
          longitude: Number(data.longitude),
          latitudeDelta: Number(data.latitudeDelta) || 0.05,
          longitudeDelta: Number(data.longitudeDelta) || 0.05,
        }

        if (Number.isNaN(nextRegion.latitude) || Number.isNaN(nextRegion.longitude)) {
          return
        }

        lastRegionFromMap.current = nextRegion
        onRegionChangeComplete(nextRegion)
      }
    } catch {
      // ignore malformed bridge messages
    }
  }

  return (
    <View style={styles.wrap}>
      <WebView
        ref={webViewRef}
        originWhitelist={["*"]}
        source={{ html: mapHtml, baseUrl: KAKAO_WEBVIEW_BASE_URL }}
        javaScriptEnabled
        domStorageEnabled
        onMessage={handleMessage}
        style={styles.map}
      />

      {isLoading ? (
        <View style={styles.loaderWrap}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      ) : null}

      {mapError ? (
        <View style={styles.errorWrap}>
          <Text style={styles.errorText}>{mapError}</Text>
        </View>
      ) : null}
    </View>
  )
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
  },
  map: {
    flex: 1,
    backgroundColor: colors.surface,
  },
  loaderWrap: {
    position: "absolute",
    top: 16,
    right: 16,
    width: 34,
    height: 34,
    borderRadius: radius.full,
    backgroundColor: "rgba(255,255,255,0.95)",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  errorWrap: {
    position: "absolute",
    left: 12,
    right: 12,
    top: 70,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: radius.md,
    backgroundColor: "rgba(38,38,38,0.92)",
  },
  errorText: {
    color: colors.surface,
    fontSize: 12,
    fontWeight: "700",
  },
})
