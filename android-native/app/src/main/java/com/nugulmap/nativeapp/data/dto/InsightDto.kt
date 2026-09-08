package com.nugulmap.nativeapp.data.dto

import java.net.URI
import java.time.LocalDate
import java.time.ZoneId

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
    val freshnessStatus: String? = null,
    val ageSeconds: Long? = null,
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
    val detailUrl: String? = null,
    val collectedAt: String? = null,
    val attribution: String? = null,
    val sourceUrl: String? = null,
    val license: String? = null,
    val licenseUrl: String? = null,
) {
    val isPopup: Boolean
        get() = kind.equals("popup", ignoreCase = true)

    val isVerifiedSeongsuPopup: Boolean
        get() {
            if (!isPopup || !title.containsExplicitPopupTerm()) {
                return false
            }
            val location = listOfNotNull(title, address).joinToString(" ").lowercase()
            val hasSeongsuLocation = SEONGSU_LOCATION_TERMS.any(location::contains)
            val isInsideSeongsu = latitude in MapBounds.seongsu.minLat..MapBounds.seongsu.maxLat &&
                longitude in MapBounds.seongsu.minLng..MapBounds.seongsu.maxLng
            val hasCompleteDates = hasCurrentIsoDateRange(startDate, endDate)
            val hasAddress = !address.isNullOrBlank()
            val hasSafeDetailLink = detailUrl.safePublicHttpUrlOrNull() != null ||
                sourceContentId.safePublicHttpUrlOrNull() != null
            val hasApprovedProvenance = source == APPROVED_POPUP_SOURCE &&
                attribution == APPROVED_POPUP_ATTRIBUTION &&
                sourceUrl.safePublicHttpUrlOrNull() == APPROVED_POPUP_SOURCE_URL &&
                license == APPROVED_POPUP_LICENSE &&
                licenseUrl.safePublicHttpUrlOrNull() == APPROVED_POPUP_LICENSE_URL
            val metadata = listOfNotNull(
                title,
                kind,
                period,
                address,
                source,
                attribution,
                sourceUrl,
                license,
                licenseUrl,
                detailUrl,
                sourceContentId,
            ).joinToString(" ")
            val isTobaccoFree = !TOBACCO_TERM_REGEX.containsMatchIn(metadata)
            return hasSeongsuLocation &&
                isInsideSeongsu &&
                hasCompleteDates &&
                hasAddress &&
                hasSafeDetailLink &&
                hasApprovedProvenance &&
                isTobaccoFree
        }

    val eventLabel: String
        get() {
            val kindLabel = when (kind) {
                "popup" -> "팝업"
                "festival" -> "축제"
                else -> "행사"
            }
            return period?.takeIf { it.isNotBlank() }?.let { "$kindLabel · $it" } ?: kindLabel
        }

    val sourceLabel: String?
        get() = attribution?.trim()?.takeIf(String::isNotEmpty) ?: when {
            source.equals("KTO_TOUR_API", ignoreCase = true) -> "한국관광공사"
            source.equals("SEOUL_CULTURE_API", ignoreCase = true) -> APPROVED_POPUP_ATTRIBUTION
            source.equals("CRAWLED_POPUP_TREND", ignoreCase = true) -> "팝업 공식 채널"
            else -> null
        }

    val licenseLabel: String?
        get() = license?.trim()?.takeIf(String::isNotEmpty)
}

private fun String.containsExplicitPopupTerm(): Boolean {
    val normalized = lowercase()
    return "팝업" in normalized || POPUP_WORD_REGEX.containsMatchIn(normalized)
}

private fun hasCurrentIsoDateRange(startDate: String?, endDate: String?): Boolean {
    val start = startDate?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = endDate?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    return start != null && end != null && !start.isAfter(end) && !end.isBefore(today)
}

internal fun String?.safePublicHttpUrlOrNull(): String? {
    val candidate = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host
        ?.trim('[', ']')
        ?.lowercase()
        ?.removeSuffix(".")
        ?.takeIf(String::isNotBlank)
        ?: return null
    return candidate.takeIf {
        uri.isAbsolute &&
            scheme == "https" &&
            uri.rawUserInfo == null &&
            host.isPublicLinkHost()
    }
}

private fun String.isPublicLinkHost(): Boolean {
    if (this == "localhost" || endsWith(".localhost") || endsWith(".local") || endsWith(".internal")) {
        return false
    }

    // Literal IPv6 includes bracketed and IPv4-mapped spellings. Provider links
    // use DNS names, so raw IPv6 is rejected instead of attempting partial SSRF classification.
    if (':' in this) {
        return false
    }

    val ipv4 = parseIpv4Literal()
    if (ipv4 == null) {
        return !looksLikeIpv4Literal()
    }
    val first = ((ipv4 ushr 24) and 0xff).toInt()
    val second = ((ipv4 ushr 16) and 0xff).toInt()
    return first != 0 &&
        first != 10 &&
        first != 127 &&
        !(first == 100 && second in 64..127) &&
        !(first == 169 && second == 254) &&
        !(first == 172 && second in 16..31) &&
        !(first == 192 && second == 168) &&
        first < 224
}

private fun String.looksLikeIpv4Literal(): Boolean {
    val parts = split('.')
    return parts.size in 1..4 && parts.all(IPV4_COMPONENT_REGEX::matches)
}

private fun String.parseIpv4Literal(): Long? {
    if (!looksLikeIpv4Literal()) return null
    val parts = split('.')
    val values = parts.map { it.parseIpv4Component() ?: return null }
    val address = when (values.size) {
        1 -> values[0]
        2 -> {
            if (values[0] > 0xff || values[1] > 0xffffff) return null
            (values[0] shl 24) or values[1]
        }
        3 -> {
            if (values[0] > 0xff || values[1] > 0xff || values[2] > 0xffff) return null
            (values[0] shl 24) or (values[1] shl 16) or values[2]
        }
        4 -> {
            if (values.any { it > 0xff }) return null
            (values[0] shl 24) or (values[1] shl 16) or (values[2] shl 8) or values[3]
        }
        else -> return null
    }
    return address.takeIf { it in 0..0xffffffffL }
}

private fun String.parseIpv4Component(): Long? = when {
    startsWith("0x", ignoreCase = true) -> drop(2).toLongOrNull(16)
    length > 1 && startsWith('0') -> drop(1).toLongOrNull(8)
    else -> toLongOrNull(10)
}

private val POPUP_WORD_REGEX = Regex("(?<![a-z])popup(?![a-z])")
private val SEONGSU_LOCATION_TERMS = listOf("성수", "서울숲", "연무장")
private const val APPROVED_POPUP_SOURCE = "SEOUL_CULTURE_API"
private const val APPROVED_POPUP_ATTRIBUTION = "서울특별시 문화행사 정보"
private const val APPROVED_POPUP_SOURCE_URL = "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do"
private const val APPROVED_POPUP_LICENSE = "공공누리 제1유형"
private const val APPROVED_POPUP_LICENSE_URL = "https://www.kogl.or.kr/info/licenseType1.do"
private val IPV4_COMPONENT_REGEX = Regex("(?:0[xX][0-9a-fA-F]+|[0-9]+)")
private val TOBACCO_TERM_REGEX = Regex(
    "담배|니코틴|베이프|궐련|아이코스|(?<![a-z])(tobacco|nicotine|iqos|juul|vape|cigarette)(?![a-z])",
    RegexOption.IGNORE_CASE,
)

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
