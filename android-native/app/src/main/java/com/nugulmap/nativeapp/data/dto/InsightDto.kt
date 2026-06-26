package com.nugulmap.nativeapp.data.dto

data class HotplaceInsightPayload(
    val places: List<HotplaceDto> = emptyList(),
    val dataFreshness: String? = null,
    val updatedAt: String? = null,
    val sources: List<String> = emptyList(),
)

data class HotplaceDto(
    val id: String,
    val name: String,
    val category: String? = null,
    val crowdLevel: String? = null,
    val crowdMessage: String? = null,
    val estimatedMinPeople: Int? = null,
    val estimatedMaxPeople: Int? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val source: String? = null,
    val sourcePlaceCode: String? = null,
    val updatedAt: String? = null,
) {
    val crowdLabel: String
        get() {
            val level = crowdLevel?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
            val minPeople = estimatedMinPeople
            val maxPeople = estimatedMaxPeople
            val count = if (minPeople != null && maxPeople != null) {
                " · %,d-%,d명".format(minPeople, maxPeople)
            } else {
                ""
            }
            return when {
                level != null -> level + count
                category == "popup" -> "팝업 후보"
                else -> "핫플 후보"
            }
        }
}

data class EventInsightPayload(
    val events: List<TrendEventDto> = emptyList(),
    val dataFreshness: String? = null,
    val updatedAt: String? = null,
    val sources: List<String> = emptyList(),
)

data class TrendEventDto(
    val id: String,
    val title: String,
    val kind: String? = null,
    val period: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val imageUrl: String? = null,
    val source: String? = null,
    val sourceContentId: String? = null,
) {
    val eventLabel: String
        get() {
            val kindLabel = when (kind) {
                "popup" -> "팝업"
                "festival" -> "축제"
                else -> "행사"
            }
            return period?.takeIf { it.isNotBlank() }?.let { "$kindLabel · $it" } ?: kindLabel
        }
}

data class InsightProviderStatusDto(
    val configured: Boolean = false,
    val qualityStatus: String? = null,
    val lastSuccessAt: String? = null,
    val lastFailureAt: String? = null,
    val detail: String? = null,
)

data class PopupTrendStatusDto(
    val fileConfigured: Boolean = false,
    val fileExists: Boolean = false,
    val recordCount: Int = 0,
    val latestCollectedAt: String? = null,
    val qualityStatus: String? = null,
    val detail: String? = null,
)

data class InsightStatusPayload(
    val seoulCityDataKeyConfigured: Boolean = false,
    val telecomCrowdKeyConfigured: Boolean = false,
    val telecomCrowdUrlTemplateConfigured: Boolean = false,
    val ktoTourApiKeyConfigured: Boolean = false,
    val seoulCultureApiKeyConfigured: Boolean = false,
    val hotplaceMode: String? = null,
    val eventMode: String? = null,
    val seoulCityData: InsightProviderStatusDto? = null,
    val telecomCrowd: InsightProviderStatusDto? = null,
    val ktoTourApi: InsightProviderStatusDto? = null,
    val seoulCultureApi: InsightProviderStatusDto? = null,
    val popupTrends: PopupTrendStatusDto? = null,
    val checkedAt: String? = null,
)

data class MapInsightPayload(
    val hotplaces: HotplaceInsightPayload = HotplaceInsightPayload(),
    val events: EventInsightPayload = EventInsightPayload(),
    val status: InsightStatusPayload? = null,
    val updatedAt: String? = null,
)
