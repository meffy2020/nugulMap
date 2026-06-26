import { useEffect, useMemo, useRef, useState } from "react"
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from "react-native"
import WebView, { type WebViewMessageEvent } from "react-native-webview"
import Constants from "expo-constants"
import type { Hotplace, MapRegion, SmokingZone, TrendEvent } from "../types"
import { colors, radius } from "../theme/tokens"

interface MapScreenProps {
  region: MapRegion
  zones: SmokingZone[]
  hotplaces?: Hotplace[]
  events?: TrendEvent[]
  selectedZone: SmokingZone | null
  isLoading: boolean
  onRegionChangeComplete: (region: MapRegion) => void
  onSelectZone: (zone: SmokingZone) => void
  onSelectHotplace?: (place: Hotplace) => void
  onSelectEvent?: (event: TrendEvent) => void
}

type Season2LayerMode = "all" | "zones" | "hotplaces" | "events"

const LAYER_MODES: Array<{ mode: Season2LayerMode; label: string }> = [
  { mode: "all", label: "전체" },
  { mode: "zones", label: "흡연" },
  { mode: "hotplaces", label: "핫플" },
  { mode: "events", label: "행사" },
]

const EXPO_EXTRA = (Constants.expoConfig?.extra || {}) as {
  kakaoJavascriptKey?: string
  kakaoWebviewBaseUrl?: string
  kakaoMarkerImageUrl?: string
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
const KAKAO_MARKER_IMAGE_URL =
  process.env.EXPO_PUBLIC_KAKAO_MARKER_IMAGE_URL ||
  EXPO_EXTRA.kakaoMarkerImageUrl ||
  `${KAKAO_WEBVIEW_BASE_URL.replace(/\/$/, "")}/images/pin.png`
const REGION_SYNC_EPS = 0.00002
const MARKER_RENDER_FAIL_MESSAGE = "마커 렌더링에 실패했습니다. API URL 또는 좌표 데이터를 확인해 주세요."

function isSimilarRegion(a: MapRegion, b: MapRegion): boolean {
  return (
    Math.abs(a.latitude - b.latitude) < REGION_SYNC_EPS &&
    Math.abs(a.longitude - b.longitude) < REGION_SYNC_EPS &&
    Math.abs(a.latitudeDelta - b.latitudeDelta) < REGION_SYNC_EPS &&
    Math.abs(a.longitudeDelta - b.longitudeDelta) < REGION_SYNC_EPS
  )
}

async function readUriAsDataUrl(uri: string): Promise<string | null> {
  if (!uri) return null

  try {
    const response = await fetch(uri)
    const blob = await response.blob()
    return await new Promise((resolve) => {
      const reader = new FileReader()
      reader.onloadend = () => {
        resolve(typeof reader.result === "string" ? reader.result : null)
      }
      reader.onerror = () => resolve(null)
      reader.readAsDataURL(blob)
    })
  } catch {
    return null
  }
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
        var zoneMarkers = [];
        var insightOverlays = [];
        var markerImage = null;
        var markerImageReady = false;
        var lastZones = [];
        var lastInsights = { hotplaces: [], events: [] };
        var queued = [];

        function post(payload) {
          if (window.ReactNativeWebView) {
            window.ReactNativeWebView.postMessage(JSON.stringify(payload));
          }
        }

        function clearMarkers() {
          for (var i = 0; i < zoneMarkers.length; i += 1) {
            zoneMarkers[i].setMap(null);
          }
          zoneMarkers = [];
        }

        function clearInsightOverlays() {
          for (var i = 0; i < insightOverlays.length; i += 1) {
            insightOverlays[i].setMap(null);
          }
          insightOverlays = [];
        }

        function escapeText(value) {
          return String(value || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
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

        function setMarkerImage(input) {
          markerImage = null;
          markerImageReady = false;

          var candidates = [];
          if (Array.isArray(input)) {
            candidates = input;
          } else if (typeof input === "string" && input) {
            candidates = [input];
          }

          if (!candidates.length) {
            if (lastZones.length) {
              renderMarkers(lastZones);
            }
            return;
          }

          var index = 0;

          function loadNextCandidate() {
            if (index >= candidates.length) {
              markerImage = null;
              markerImageReady = false;
              if (lastZones.length) {
                renderMarkers(lastZones);
              }
              post({ type: "warn", message: "marker image load failed" });
              return;
            }

            var uri = candidates[index];
            index += 1;

            if (!uri || typeof uri !== "string") {
              loadNextCandidate();
              return;
            }

            try {
              var preload = new window.Image();
              preload.onload = function () {
                try {
                  markerImage = new kakao.maps.MarkerImage(
                    uri,
                    new kakao.maps.Size(40, 40),
                    { offset: new kakao.maps.Point(20, 40) }
                  );
                  markerImageReady = true;
                  if (lastZones.length) {
                    renderMarkers(lastZones);
                  }
                } catch (_error) {
                  loadNextCandidate();
                }
              };
              preload.onerror = function () {
                loadNextCandidate();
              };
              preload.src = uri;
            } catch (_error) {
              loadNextCandidate();
            }
          }

          try {
            loadNextCandidate();
          } catch (_error) {
            markerImage = null;
            markerImageReady = false;
            if (lastZones.length) {
              renderMarkers(lastZones);
            }
            post({ type: "warn", message: "marker image load failed" });
          }
        }

        function renderMarkers(zones) {
          if (!map) return;
          clearMarkers();
          lastZones = Array.isArray(zones) ? zones : [];

          for (var i = 0; i < lastZones.length; i += 1) {
            var zone = lastZones[i];
            if (!zone) continue;
            var lat = Number(zone.latitude);
            var lng = Number(zone.longitude);
            if (!isFinite(lat) || !isFinite(lng)) continue;

            var markerOptions = {
              map: map,
              position: new kakao.maps.LatLng(lat, lng),
              title: zone.subtype || "흡연구역",
            };
            if (markerImageReady && markerImage) {
              markerOptions.image = markerImage;
            }

            var marker;
            try {
              marker = new kakao.maps.Marker(markerOptions);
            } catch (_error) {
              marker = new kakao.maps.Marker({
                map: map,
                position: new kakao.maps.LatLng(lat, lng),
                title: zone.subtype || "흡연구역",
              });
            }

            (function (zoneId) {
              kakao.maps.event.addListener(marker, "click", function () {
                post({ type: "markerPress", id: zoneId });
              });
            })(zone.id);

            zoneMarkers.push(marker);
          }

          post({ type: "markersRendered", count: zoneMarkers.length });
        }

        function createInsightOverlay(item, kind) {
          var lat = Number(item && item.latitude);
          var lng = Number(item && item.longitude);
          if (!isFinite(lat) || !isFinite(lng)) return null;

          var label = kind === "event" ? "일정" : formatHotplaceOverlayLabel(item);
          var title = kind === "event" ? item.title : item.name;
          var className = kind === "event" ? "event" : "hotplace";
          var button = document.createElement("button");
          button.type = "button";
          button.setAttribute("aria-label", escapeText(title));
          button.style.cssText = [
            "min-width:56px",
            "height:32px",
            "padding:0 10px",
            "border:0",
            "border-radius:999px",
            "box-shadow:0 4px 12px rgba(15,23,42,0.22)",
            "font-weight:800",
            "font-size:11px",
            "color:#fff",
            "white-space:nowrap",
            "background:" + (className === "event" ? "#2563eb" : "#ef6c00")
          ].join(";");
          button.innerHTML = (className === "event" ? "일정" : "핫플") + " · " + escapeText(label);
          button.onclick = function () {
            post({ type: kind === "event" ? "eventPress" : "hotplacePress", id: item.id });
          };

          return new kakao.maps.CustomOverlay({
            map: map,
            position: new kakao.maps.LatLng(lat, lng),
            content: button,
            yAnchor: 1.3,
            zIndex: kind === "event" ? 9 : 8
          });
        }

        function formatHotplaceOverlayLabel(item) {
          if (!item) return "핫플";
          var crowd = item.crowdLevel && item.crowdLevel !== "UNKNOWN" ? item.crowdLevel : "핫플";
          var minPeople = Number(item.estimatedMinPeople);
          var maxPeople = Number(item.estimatedMaxPeople);
          if (isFinite(minPeople) && isFinite(maxPeople) && minPeople > 0 && maxPeople > 0) {
            return crowd + " " + formatPeopleRange(minPeople, maxPeople);
          }
          return crowd;
        }

        function formatPeopleRange(minPeople, maxPeople) {
          if (minPeople === maxPeople) {
            return formatPeopleCount(minPeople);
          }
          return formatPeopleCount(minPeople) + "-" + formatPeopleCount(maxPeople);
        }

        function formatPeopleCount(count) {
          if (count >= 10000) {
            var value = Math.round(count / 1000) / 10;
            return String(value).replace(/\\.0$/, "") + "만";
          }
          return String(Math.round(count / 1000)) + "천";
        }

        function renderInsights(payload) {
          if (!map) return;
          clearInsightOverlays();
          lastInsights = payload || { hotplaces: [], events: [] };

          var hotplaces = Array.isArray(lastInsights.hotplaces) ? lastInsights.hotplaces : [];
          var events = Array.isArray(lastInsights.events) ? lastInsights.events : [];

          for (var i = 0; i < hotplaces.length; i += 1) {
            var hotOverlay = createInsightOverlay(hotplaces[i], "hotplace");
            if (hotOverlay) insightOverlays.push(hotOverlay);
          }

          for (var j = 0; j < events.length; j += 1) {
            var eventOverlay = createInsightOverlay(events[j], "event");
            if (eventOverlay) insightOverlays.push(eventOverlay);
          }

          post({ type: "insightsRendered", count: insightOverlays.length });
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

          if (payload.type === "SET_MARKER_IMAGE") {
            setMarkerImage(payload.markerImageCandidates || payload.markerImageUri);
            return;
          }

          if (payload.type === "SET_ZONES") {
            renderMarkers(Array.isArray(payload.zones) ? payload.zones : []);
            return;
          }

          if (payload.type === "SET_INSIGHTS") {
            renderInsights(payload);
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
  hotplaces = [],
  events = [],
  selectedZone,
  isLoading,
  onRegionChangeComplete,
  onSelectZone,
  onSelectHotplace,
  onSelectEvent,
}: MapScreenProps) {
  const webViewRef = useRef<WebView>(null)
  const [isMapReady, setIsMapReady] = useState(false)
  const [mapError, setMapError] = useState<string | null>(null)
  const [layerMode, setLayerMode] = useState<Season2LayerMode>("all")
  const lastRegionFromMap = useRef<MapRegion | null>(null)
  const localMarkerImageUri = useMemo(
    () => Image.resolveAssetSource(require("../../assets/images/pin.png")).uri,
    [],
  )
  const markerImageBaseCandidates = useMemo(() => {
    const defaultWebMarkerUrl = `${KAKAO_WEBVIEW_BASE_URL.replace(/\/$/, "")}/images/pin.png`
    const values = [localMarkerImageUri, KAKAO_MARKER_IMAGE_URL, defaultWebMarkerUrl]
      .map((value) => String(value || "").trim())
      .filter(Boolean)
    return Array.from(new Set(values))
  }, [localMarkerImageUri])
  const [markerImageCandidates, setMarkerImageCandidates] = useState<string[]>(markerImageBaseCandidates)
  const visibleZones = layerMode === "all" || layerMode === "zones" ? zones : []
  const visibleHotplaces = layerMode === "all" || layerMode === "hotplaces" ? hotplaces : []
  const visibleEvents = layerMode === "all" || layerMode === "events" ? events : []

  const mapHtml = useMemo(() => buildMapHtml(KAKAO_JS_KEY, region), [])

  useEffect(() => {
    if (!KAKAO_JS_KEY) {
      setMapError("Kakao JavaScript key is missing. Check mobile/.env")
      return
    }
    setMapError(null)
  }, [])

  useEffect(() => {
    setMarkerImageCandidates(markerImageBaseCandidates)
  }, [markerImageBaseCandidates])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      const dataUri = await readUriAsDataUrl(localMarkerImageUri)
      if (cancelled || !dataUri) return

      setMarkerImageCandidates((prev) => {
        const next = Array.from(new Set([dataUri, ...prev]))
        if (next.length === prev.length && next.every((value, index) => value === prev[index])) {
          return prev
        }
        return next
      })
    })()

    return () => {
      cancelled = true
    }
  }, [localMarkerImageUri])

  const postMessageToMap = (payload: unknown) => {
    if (!isMapReady || !webViewRef.current) return
    webViewRef.current.postMessage(JSON.stringify(payload))
  }

  useEffect(() => {
    if (!isMapReady) return

    postMessageToMap({
      type: "SET_MARKER_IMAGE",
      markerImageCandidates,
    })
  }, [isMapReady, markerImageCandidates])

  useEffect(() => {
    if (!isMapReady) return

    postMessageToMap({
      type: "SET_ZONES",
      zones: visibleZones,
    })
  }, [visibleZones, isMapReady])

  useEffect(() => {
    if (!isMapReady) return

    postMessageToMap({
      type: "SET_INSIGHTS",
      hotplaces: visibleHotplaces,
      events: visibleEvents,
    })
  }, [visibleHotplaces, visibleEvents, isMapReady])

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

      if (data?.type === "warn") {
        return
      }

      if (data?.type === "markersRendered") {
        const rendered = Number(data.count)
        if (visibleZones.length > 0 && rendered === 0) {
          setMapError(MARKER_RENDER_FAIL_MESSAGE)
          return
        }

        setMapError((prev) => (prev === MARKER_RENDER_FAIL_MESSAGE ? null : prev))
        return
      }

      if (data?.type === "markerPress") {
        const zone = zones.find((item) => item.id === Number(data.id))
        if (zone) {
          onSelectZone(zone)
        }
        return
      }

      if (data?.type === "hotplacePress") {
        const place = hotplaces.find((item) => item.id === String(data.id))
        if (place) {
          onSelectHotplace?.(place)
        }
        return
      }

      if (data?.type === "eventPress") {
        const event = events.find((item) => item.id === String(data.id))
        if (event) {
          onSelectEvent?.(event)
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
        mixedContentMode="always"
        onMessage={handleMessage}
        style={styles.map}
      />

      <View style={styles.layerControl} testID="season2-layer-control">
        {LAYER_MODES.map((item) => {
          const isActive = item.mode === layerMode
          return (
            <Pressable
              key={item.mode}
              accessibilityRole="button"
              testID={`season2-layer-${item.mode}`}
              style={[styles.layerButton, isActive && styles.layerButtonActive]}
              onPress={() => setLayerMode(item.mode)}
            >
              <Text style={[styles.layerButtonText, isActive && styles.layerButtonTextActive]}>{item.label}</Text>
            </Pressable>
          )
        })}
      </View>

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
  layerControl: {
    position: "absolute",
    top: 14,
    left: 14,
    right: 14,
    height: 40,
    borderRadius: radius.lg,
    backgroundColor: "rgba(255,255,255,0.96)",
    borderWidth: 1,
    borderColor: colors.border,
    flexDirection: "row",
    padding: 4,
    gap: 4,
  },
  layerButton: {
    flex: 1,
    minWidth: 0,
    height: 30,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  layerButtonActive: {
    backgroundColor: colors.text,
  },
  layerButtonText: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "800",
  },
  layerButtonTextActive: {
    color: colors.surface,
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
