package com.nugulmap.nativeapp.data.dto

data class ZoneBoundsPayload(
    val zones: List<ZoneDto> = emptyList(),
    val count: Int = zones.size,
)

data class ZoneDto(
    val id: Int,
    val region: String? = null,
    val type: String? = null,
    val subtype: String? = null,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val size: String? = null,
    val date: String? = null,
    val address: String? = null,
    val user: String? = null,
    val image: String? = null,
    val imageUrl: String? = null,
) {
    val title: String
        get() = address?.takeIf { it.isNotBlank() }
            ?: subtype?.takeIf { it.isNotBlank() }
            ?: type?.takeIf { it.isNotBlank() }
            ?: "흡연구역"

    val summary: String
        get() = listOfNotNull(region, type, subtype)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    val preferredImageUrl: String?
        get() = imageUrl?.takeIf { it.isNotBlank() } ?: image?.takeIf { it.isNotBlank() }
}
