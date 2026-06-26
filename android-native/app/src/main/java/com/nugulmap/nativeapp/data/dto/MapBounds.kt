package com.nugulmap.nativeapp.data.dto

data class MapBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun key(): String = "%.5f:%.5f:%.5f:%.5f".format(minLat, maxLat, minLng, maxLng)

    companion object {
        val centralSeoul = MapBounds(
            minLat = 37.48,
            maxLat = 37.60,
            minLng = 126.88,
            maxLng = 127.12,
        )
    }
}
