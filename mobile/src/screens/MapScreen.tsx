import { useEffect, useRef } from "react"
import { ActivityIndicator, StyleSheet, Text, View } from "react-native"
import MapView, { Marker, PROVIDER_GOOGLE, type Region as MapRegionType } from "react-native-maps"
import type { MapRegion, SmokingZone } from "../types"

interface MapScreenProps {
  region: MapRegion
  zones: SmokingZone[]
  selectedZone: SmokingZone | null
  isLoading: boolean
  onRegionChangeComplete: (region: MapRegion) => void
  onSelectZone: (zone: SmokingZone) => void
}

export function MapScreen({
  region,
  zones,
  selectedZone,
  isLoading,
  onRegionChangeComplete,
  onSelectZone,
}: MapScreenProps) {
  const mapRef = useRef<MapView>(null)

  useEffect(() => {
    if (selectedZone && mapRef.current) {
      const camera = {
        center: {
          latitude: selectedZone.latitude,
          longitude: selectedZone.longitude,
        },
        zoom: 15,
      }
      void mapRef.current.animateToRegion(
        {
          latitude: camera.center.latitude,
          longitude: camera.center.longitude,
          latitudeDelta: 0.01,
          longitudeDelta: 0.01,
        },
        350,
      )
    }
  }, [selectedZone])

  const toRegion = (next: MapRegionType): MapRegion => ({
    latitude: next.latitude,
    longitude: next.longitude,
    latitudeDelta: next.latitudeDelta,
    longitudeDelta: next.longitudeDelta,
  })

  return (
    <View style={styles.wrap}>
      <MapView
        ref={mapRef}
        style={styles.map}
        provider={PROVIDER_GOOGLE}
        region={region}
        onRegionChangeComplete={(next) => onRegionChangeComplete(toRegion(next))}
        showsUserLocation
        showsMyLocationButton
      >
        {zones.map((zone) => (
          <Marker
            key={zone.id}
            coordinate={{
              latitude: zone.latitude,
              longitude: zone.longitude,
            }}
            title={zone.subtype}
            description={zone.address}
            onPress={() => onSelectZone(zone)}
          />
        ))}
      </MapView>

      {isLoading ? (
        <View style={styles.loaderWrap}>
          <ActivityIndicator size="large" color="#0f172a" />
          <Text style={styles.loadingText}>구역 조회 중...</Text>
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
  },
  loaderWrap: {
    position: "absolute",
    top: 16,
    left: 16,
    right: 16,
    padding: 10,
    borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.9)",
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  loadingText: {
    color: "#0f172a",
  },
})
