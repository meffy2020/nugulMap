import { useEffect, useMemo, useRef, useState } from "react"
import {
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native"
import * as Location from "expo-location"
import Constants from "expo-constants"
import { MaterialCommunityIcons } from "@expo/vector-icons"
import { useSafeAreaInsets } from "react-native-safe-area-context"
import WebView, { type WebViewMessageEvent } from "react-native-webview"
import { createZone, type CreateZonePayload } from "../services/nugulApi"
import type { SmokingZone } from "../types"
import { colors, radius } from "../theme/tokens"

interface AddZoneModalProps {
  visible: boolean
  accessToken: string | null
  onClose: () => void
  onCreated: (zone: SmokingZone) => void
  initialLatitude?: number
  initialLongitude?: number
  initialAddress?: string
  initialSubtype?: string
}

type ZoneType = "BOOTH" | "OPEN" | "INDOOR"
type AddStep = "location" | "details"

interface PickerCoords {
  latitude: number
  longitude: number
}

const DEFAULT_COORDS: PickerCoords = {
  latitude: 37.5665,
  longitude: 126.978,
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

const TYPE_OPTIONS: Array<{ type: ZoneType; label: string }> = [
  { type: "BOOTH", label: "부스" },
  { type: "OPEN", label: "개방" },
  { type: "INDOOR", label: "실내" },
]

function getInitialType(initialSubtype?: string): { type: ZoneType; label: string } {
  if (!initialSubtype) {
    return TYPE_OPTIONS[0]
  }

  if (initialSubtype.includes("실내")) {
    return { type: "INDOOR", label: "실내" }
  }
  if (initialSubtype.includes("개방") || initialSubtype.includes("실외")) {
    return { type: "OPEN", label: "개방" }
  }
  return { type: "BOOTH", label: "부스" }
}

function buildPickerMapHtml(appKey: string, coords: PickerCoords): string {
  return `
<!doctype html>
<html>
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
    <style>
      html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; overflow: hidden; background: #f3f4f6; }
    </style>
    <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services"></script>
  </head>
  <body>
    <div id="map"></div>
    <script>
      (function () {
        var map = null;
        var geocoder = null;
        var queued = [];

        function post(payload) {
          if (window.ReactNativeWebView) {
            window.ReactNativeWebView.postMessage(JSON.stringify(payload));
          }
        }

        function emitCenter() {
          if (!map) return;
          var center = map.getCenter();
          var lat = center.getLat();
          var lng = center.getLng();

          if (geocoder) {
            post({ type: "resolving" });
            geocoder.coord2Address(lng, lat, function (result, status) {
              var address = "";
              var region = "";
              if (status === kakao.maps.services.Status.OK && result && result[0] && result[0].address) {
                address = result[0].address.address_name || "";
                region = result[0].address.region_1depth_name || "";
              }

              post({
                type: "centerChange",
                latitude: lat,
                longitude: lng,
                address: address,
                region: region,
              });
            });
            return;
          }

          post({
            type: "centerChange",
            latitude: lat,
            longitude: lng,
            address: "",
            region: "",
          });
        }

        function moveCenter(center, level) {
          if (!map || !center) return;

          var lat = Number(center.latitude);
          var lng = Number(center.longitude);
          if (!isFinite(lat) || !isFinite(lng)) return;

          map.setCenter(new kakao.maps.LatLng(lat, lng));
          if (typeof level === "number") {
            map.setLevel(level);
          }
          emitCenter();
        }

        function applyPayload(payload) {
          if (!map) {
            queued.push(payload);
            return;
          }

          if (payload.type === "MOVE_CENTER") {
            moveCenter(payload.center, payload.level);
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
            center: new kakao.maps.LatLng(${coords.latitude}, ${coords.longitude}),
            level: 3,
          });

          if (kakao.maps.services && kakao.maps.services.Geocoder) {
            geocoder = new kakao.maps.services.Geocoder();
          }

          kakao.maps.event.addListener(map, "idle", emitCenter);

          if (queued.length) {
            for (var i = 0; i < queued.length; i += 1) {
              applyPayload(queued[i]);
            }
            queued = [];
          }

          emitCenter();
          post({ type: "ready" });
        });

        window.addEventListener("error", function (e) {
          post({ type: "error", message: e && e.message ? e.message : "Unknown map error" });
        });
      })();
    </script>
  </body>
</html>
`
}

export function AddZoneModal({
  visible,
  accessToken,
  onClose,
  onCreated,
  initialLatitude,
  initialLongitude,
  initialAddress,
  initialSubtype,
}: AddZoneModalProps) {
  const insets = useSafeAreaInsets()
  const webViewRef = useRef<WebView>(null)
  const [step, setStep] = useState<AddStep>("location")
  const [type, setType] = useState<ZoneType>("BOOTH")
  const [subtype, setSubtype] = useState("부스")
  const [description, setDescription] = useState("")
  const [coords, setCoords] = useState<PickerCoords>(DEFAULT_COORDS)
  const [address, setAddress] = useState("")
  const [region, setRegion] = useState("서울특별시")
  const [isAddressLoading, setIsAddressLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isMapReady, setIsMapReady] = useState(false)
  const [mapError, setMapError] = useState<string | null>(null)
  const [mapKey, setMapKey] = useState(0)

  const seedCoords = useMemo<PickerCoords>(
    () => ({
      latitude: typeof initialLatitude === "number" ? initialLatitude : DEFAULT_COORDS.latitude,
      longitude: typeof initialLongitude === "number" ? initialLongitude : DEFAULT_COORDS.longitude,
    }),
    [initialLatitude, initialLongitude],
  )

  const mapHtml = useMemo(
    () => buildPickerMapHtml(KAKAO_JS_KEY, seedCoords),
    [seedCoords.latitude, seedCoords.longitude],
  )

  useEffect(() => {
    if (!visible) return

    const initialType = getInitialType(initialSubtype)
    setStep("location")
    setType(initialType.type)
    setSubtype(initialType.label)
    setDescription("")
    setCoords(seedCoords)
    setAddress(initialAddress || "")
    setRegion("서울특별시")
    setIsAddressLoading(!Boolean(initialAddress))
    setIsSubmitting(false)
    setIsMapReady(false)
    setMapError(KAKAO_JS_KEY ? null : "Kakao JavaScript key is missing. Check mobile/.env")
    setMapKey((prev) => prev + 1)
  }, [visible, seedCoords.latitude, seedCoords.longitude, initialAddress, initialSubtype])

  const postMessageToMap = (payload: unknown) => {
    if (!isMapReady || !webViewRef.current) return
    webViewRef.current.postMessage(JSON.stringify(payload))
  }

  useEffect(() => {
    if (!visible || !isMapReady || !webViewRef.current) return

    webViewRef.current.postMessage(
      JSON.stringify({
        type: "MOVE_CENTER",
        center: coords,
        level: 3,
      }),
    )
  }, [visible, isMapReady, mapKey])

  const handleMapMessage = (event: WebViewMessageEvent) => {
    try {
      const data = JSON.parse(event.nativeEvent.data)
      if (data?.type === "ready") {
        setIsMapReady(true)
        setMapError(null)
        return
      }

      if (data?.type === "error") {
        setMapError(String(data.message || "지도를 불러오지 못했습니다."))
        setIsAddressLoading(false)
        return
      }

      if (data?.type === "resolving") {
        setIsAddressLoading(true)
        return
      }

      if (data?.type === "centerChange") {
        const latitude = Number(data.latitude)
        const longitude = Number(data.longitude)
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
          return
        }

        setCoords({ latitude, longitude })

        const nextAddress = typeof data.address === "string" ? data.address.trim() : ""
        const nextRegion = typeof data.region === "string" ? data.region.trim() : ""
        if (nextAddress) {
          setAddress(nextAddress)
        }
        if (nextRegion) {
          setRegion(nextRegion)
        }
        setIsAddressLoading(false)
      }
    } catch {
      // ignore malformed bridge payload
    }
  }

  const moveToCurrentLocation = async () => {
    const permission = await Location.requestForegroundPermissionsAsync()
    if (permission.status !== "granted") {
      Alert.alert("권한 필요", "현재 위치를 사용하려면 위치 권한이 필요합니다.")
      return
    }

    const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
    const next = {
      latitude: current.coords.latitude,
      longitude: current.coords.longitude,
    }
    setCoords(next)
    setAddress("")
    setIsAddressLoading(true)
    postMessageToMap({
      type: "MOVE_CENTER",
      center: next,
      level: 3,
    })
  }

  const submit = async () => {
    if (!accessToken) {
      Alert.alert("로그인 필요", "로그인 후 제보 기능을 사용할 수 있습니다.")
      return
    }

    const normalizedAddress = address.trim()
    if (!normalizedAddress) {
      Alert.alert("위치 확인", "지도의 위치를 먼저 확인해주세요.")
      setStep("location")
      return
    }

    setIsSubmitting(true)
    try {
      const payload: CreateZonePayload = {
        region: region || "모바일",
        type,
        subtype,
        description: description.trim() || `${subtype} 흡연구역`,
        latitude: coords.latitude,
        longitude: coords.longitude,
        size: "M",
        address: normalizedAddress,
        user: "mobile-user",
      }

      const created = await createZone(payload, accessToken)
      onCreated(created)
      Alert.alert("등록 완료", "흡연구역이 등록되었습니다.")
      onClose()
    } catch (error) {
      Alert.alert("등록 실패", "등록 중 오류가 발생했습니다.")
      console.warn("create zone failed", error)
    } finally {
      setIsSubmitting(false)
    }
  }

  const renderLocationStep = () => {
    return (
      <View style={styles.stepRoot}>
        <View style={styles.mapWrap}>
          <WebView
            key={`add-zone-map-${mapKey}`}
            ref={webViewRef}
            originWhitelist={["*"]}
            source={{ html: mapHtml, baseUrl: KAKAO_WEBVIEW_BASE_URL }}
            javaScriptEnabled
            domStorageEnabled
            onMessage={handleMapMessage}
            style={styles.map}
          />

          <View pointerEvents="none" style={styles.centerPinWrap}>
            <Image source={require("../../assets/images/pin.png")} style={styles.centerPin} resizeMode="contain" />
          </View>

          <Pressable style={styles.currentLocationButton} onPress={() => void moveToCurrentLocation()}>
            <MaterialCommunityIcons name="crosshairs-gps" size={20} color={colors.text} />
          </Pressable>

          {mapError ? (
            <View style={styles.mapErrorWrap}>
              <Text style={styles.mapErrorText}>{mapError}</Text>
            </View>
          ) : null}
        </View>

        <View style={styles.locationBody}>
          <View style={styles.identityCard}>
            <View style={styles.identityIconWrap}>
              <Image source={require("../../assets/images/pin.png")} style={styles.identityIcon} resizeMode="contain" />
            </View>
            <View style={styles.identityTextWrap}>
              <Text style={styles.identityKicker}>NugulMap</Text>
              <Text style={styles.identityHeadline}>우리 동네 흡연구역 제보</Text>
            </View>
          </View>

          <Text style={styles.locationTitle}>핀 위치를 정확하게 맞춰주세요</Text>
          <Text style={styles.locationDescription}>지도를 움직여 위치를 확인한 뒤 다음 단계로 진행합니다.</Text>

          <View style={styles.currentAddressCard}>
            <View style={styles.currentAddressHeader}>
              <MaterialCommunityIcons name="map-marker-outline" size={17} color={colors.textMuted} />
              <Text style={styles.currentAddressLabel}>선택된 주소</Text>
            </View>
            <Text style={styles.currentAddressText}>
              {isAddressLoading ? "주소 확인 중..." : address || "주소를 불러오지 못했습니다."}
            </Text>
          </View>

          <View style={styles.warningCard}>
            <MaterialCommunityIcons name="information-outline" size={18} color={colors.textMuted} />
            <Text style={styles.warningText}>건물명보다 도로명 주소 기준으로 한 번 더 확인해 주세요.</Text>
          </View>

          <Pressable
            style={[
              styles.locationConfirmButton,
              (!address.trim() || isAddressLoading) && styles.locationConfirmButtonDisabled,
            ]}
            disabled={!address.trim() || isAddressLoading}
            onPress={() => setStep("details")}
          >
            <Text style={styles.locationConfirmText}>이 위치로 다음 단계 진행</Text>
          </Pressable>
        </View>
      </View>
    )
  }

  const renderDetailsStep = () => {
    return (
      <ScrollView contentContainerStyle={styles.detailBody} showsVerticalScrollIndicator={false}>
        <View style={styles.selectedLocationCard}>
          <Text style={styles.selectedLocationLabel}>선택된 위치</Text>
          <Text style={styles.selectedLocationAddress}>{address || "주소 미확인"}</Text>
          <Text style={styles.selectedLocationCoords}>
            {coords.latitude.toFixed(6)}, {coords.longitude.toFixed(6)}
          </Text>
        </View>

        <Text style={styles.fieldLabel}>유형</Text>
        <View style={styles.typeRow}>
          {TYPE_OPTIONS.map((option) => (
            <Pressable
              key={option.type}
              style={[styles.typeChip, type === option.type && styles.typeChipActive]}
              onPress={() => {
                setType(option.type)
                setSubtype(option.label)
              }}
            >
              <Text style={[styles.typeChipText, type === option.type && styles.typeChipTextActive]}>
                {option.label}
              </Text>
            </Pressable>
          ))}
        </View>

        <Text style={styles.fieldLabel}>설명 (선택)</Text>
        <TextInput
          value={description}
          onChangeText={setDescription}
          style={styles.descriptionInput}
          multiline
          placeholder="현장 상태를 간단히 적어주세요."
          textAlignVertical="top"
        />

        <Text style={styles.fieldLabel}>이미지</Text>
        <View style={styles.imagePlaceholder}>
          <MaterialCommunityIcons name="image-outline" size={22} color={colors.textMuted} />
          <Text style={styles.imagePlaceholderText}>이미지 업로드 기능 준비중</Text>
        </View>

        <View style={styles.detailActionRow}>
          <Pressable style={styles.outlineButton} onPress={() => setStep("location")}>
            <Text style={styles.outlineButtonText}>위치 다시 선택</Text>
          </Pressable>
          <Pressable style={styles.submitButton} onPress={() => void submit()} disabled={isSubmitting}>
            <Text style={styles.submitButtonText}>{isSubmitting ? "등록 중..." : "흡연구역 등록"}</Text>
          </Pressable>
        </View>
      </ScrollView>
    )
  }

  return (
    <Modal visible={visible} animationType="slide" transparent={false} onRequestClose={onClose}>
      <View style={styles.root}>
        <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
          <Pressable
            style={styles.headerBackButton}
            onPress={step === "location" ? onClose : () => setStep("location")}
          >
            <MaterialCommunityIcons name="arrow-left" size={29} color={colors.text} />
          </Pressable>
          <Text style={styles.headerTitle}>흡연구역 제보</Text>
          <View style={styles.headerRightBadge}>
            <Image source={require("../../assets/images/pin.png")} style={styles.headerRightIcon} resizeMode="contain" />
          </View>
        </View>

        {step === "location" ? renderLocationStep() : renderDetailsStep()}
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.surfaceMuted,
  },
  header: {
    backgroundColor: colors.surface,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 12,
    paddingBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  headerBackButton: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
  },
  headerTitle: {
    fontSize: 21,
    fontWeight: "800",
    color: colors.text,
  },
  headerRightBadge: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primarySoft,
    borderWidth: 1,
    borderColor: colors.border,
  },
  headerRightIcon: {
    width: 18,
    height: 18,
  },
  stepRoot: {
    flex: 1,
  },
  mapWrap: {
    width: "100%",
    height: 380,
    backgroundColor: colors.surfaceMuted,
    borderBottomWidth: 1,
    borderColor: colors.border,
    overflow: "hidden",
  },
  map: {
    flex: 1,
  },
  centerPinWrap: {
    position: "absolute",
    top: "50%",
    left: "50%",
    width: 58,
    height: 58,
    marginLeft: -29,
    marginTop: -52,
    alignItems: "center",
    justifyContent: "center",
  },
  centerPin: {
    width: 58,
    height: 58,
  },
  currentLocationButton: {
    position: "absolute",
    right: 14,
    bottom: 14,
    width: 44,
    height: 44,
    borderRadius: radius.full,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.95)",
    borderWidth: 1,
    borderColor: colors.border,
  },
  mapErrorWrap: {
    position: "absolute",
    left: 12,
    right: 12,
    top: 12,
    paddingVertical: 9,
    paddingHorizontal: 11,
    borderRadius: radius.md,
    backgroundColor: "rgba(38,38,38,0.9)",
  },
  mapErrorText: {
    color: colors.surface,
    fontSize: 12,
    fontWeight: "700",
  },
  locationBody: {
    flex: 1,
    marginTop: -10,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    backgroundColor: colors.surface,
    paddingHorizontal: 16,
    paddingTop: 18,
    paddingBottom: 26,
  },
  identityCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.primarySoft,
    padding: 10,
  },
  identityIconWrap: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  identityIcon: {
    width: 22,
    height: 22,
  },
  identityTextWrap: {
    flex: 1,
    gap: 1,
  },
  identityKicker: {
    fontSize: 11,
    color: colors.textMuted,
    fontWeight: "700",
  },
  identityHeadline: {
    fontSize: 15,
    color: colors.text,
    fontWeight: "800",
  },
  locationTitle: {
    marginTop: 14,
    fontSize: 20,
    fontWeight: "800",
    color: colors.text,
  },
  locationDescription: {
    marginTop: 5,
    fontSize: 14,
    fontWeight: "600",
    color: colors.textMuted,
  },
  currentAddressCard: {
    marginTop: 14,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    paddingVertical: 16,
    paddingHorizontal: 14,
    gap: 8,
  },
  currentAddressHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  currentAddressLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.textMuted,
  },
  currentAddressText: {
    fontSize: 16,
    lineHeight: 23,
    color: colors.text,
    fontWeight: "700",
  },
  warningCard: {
    marginTop: 10,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    paddingVertical: 14,
    paddingHorizontal: 14,
  },
  warningText: {
    flex: 1,
    color: colors.textMuted,
    fontSize: 13,
    fontWeight: "600",
    lineHeight: 19,
  },
  locationConfirmButton: {
    marginTop: 12,
    borderRadius: radius.lg,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 17,
  },
  locationConfirmButtonDisabled: {
    opacity: 0.5,
  },
  locationConfirmText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: "800",
  },
  detailBody: {
    padding: 16,
    gap: 12,
    paddingBottom: 42,
  },
  selectedLocationCard: {
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    padding: 14,
    gap: 6,
  },
  selectedLocationLabel: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "700",
  },
  selectedLocationAddress: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700",
  },
  selectedLocationCoords: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "600",
  },
  fieldLabel: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "700",
    marginTop: 6,
  },
  typeRow: {
    flexDirection: "row",
    gap: 8,
  },
  typeChip: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 13,
  },
  typeChipActive: {
    borderColor: colors.text,
    backgroundColor: colors.primarySoft,
  },
  typeChipText: {
    color: colors.textMuted,
    fontWeight: "700",
  },
  typeChipTextActive: {
    color: colors.text,
  },
  descriptionInput: {
    minHeight: 110,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    color: colors.text,
    paddingHorizontal: 12,
    paddingVertical: 11,
    fontSize: 14,
  },
  imagePlaceholder: {
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    borderStyle: "dashed",
    backgroundColor: colors.surfaceMuted,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 24,
    gap: 6,
  },
  imagePlaceholderText: {
    color: colors.textMuted,
    fontWeight: "600",
  },
  detailActionRow: {
    gap: 8,
    marginTop: 4,
  },
  outlineButton: {
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 13,
  },
  outlineButtonText: {
    color: colors.text,
    fontWeight: "700",
  },
  submitButton: {
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 14,
  },
  submitButtonText: {
    color: colors.surface,
    fontWeight: "800",
  },
})
