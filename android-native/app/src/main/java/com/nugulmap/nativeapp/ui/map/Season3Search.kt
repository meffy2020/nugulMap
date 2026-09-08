package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.TrendEventDto

enum class Season3SearchMode {
    Hotplaces,
    Events,
    All,
}

data class Season3SearchRequest(
    val keyword: String,
    val mode: Season3SearchMode,
    val hotplaceLimit: Int = 8,
    val eventLimit: Int = 12,
    val allowsNonPopupEvents: Boolean = false,
    val displayKeyword: String = if (keyword == "hot-now") "실시간 핫플" else keyword,
)

internal fun seongsuPopupSearchRequest() = Season3SearchRequest(
    keyword = "성수 팝업",
    mode = Season3SearchMode.Events,
    hotplaceLimit = 1,
    eventLimit = 12,
    allowsNonPopupEvents = false,
)

internal fun hotNowSearchRequest() = Season3SearchRequest(
    keyword = "hot-now",
    mode = Season3SearchMode.Hotplaces,
    hotplaceLimit = 8,
    eventLimit = 1,
    displayKeyword = "실시간 핫플",
)

internal fun parseSeason3Search(rawQuery: String): Season3SearchRequest? {
    val normalized = rawQuery.trim().replace(WHITESPACE_REGEX, " ")
    if (normalized.isBlank()) {
        return null
    }

    val compact = normalized.lowercase().replace(NON_SEARCH_CHARACTER_REGEX, "")
    val asksForCrowd = CROWD_TERMS.any(compact::contains)
    val asksForEvent = EVENT_TERMS.any(compact::contains)
    val asksForNonPopupEvent = NON_POPUP_EVENT_TERMS.any(compact::contains)

    return when {
        asksForEvent && !asksForCrowd -> Season3SearchRequest(
            keyword = normalizeEventQuery(normalized),
            mode = Season3SearchMode.Events,
            hotplaceLimit = 1,
            eventLimit = 12,
            allowsNonPopupEvents = asksForNonPopupEvent,
        )
        asksForCrowd && !asksForEvent -> Season3SearchRequest(
            keyword = normalizeCrowdQuery(normalized),
            mode = Season3SearchMode.Hotplaces,
            hotplaceLimit = 8,
            eventLimit = 1,
        )
        else -> Season3SearchRequest(
            keyword = normalized,
            mode = Season3SearchMode.All,
            hotplaceLimit = 8,
            eventLimit = 12,
        )
    }
}

internal fun MapUiState.withSeason3SearchResult(
    request: Season3SearchRequest,
    result: MapInsightPayload,
): MapUiState {
    val visibleEventResults = deduplicateEventsPreferringVerifiedPopups(
        filterEventsForPopupLayer(
            events = result.events.events,
            allowsNonPopupEvents = request.allowsNonPopupEvents,
        ),
    )
    val normalizedEvents = result.events.copy(
        events = visibleEventResults,
    )
    val nextHotplaces = when (request.mode) {
        Season3SearchMode.Hotplaces,
        Season3SearchMode.All,
        -> result.hotplaces
        Season3SearchMode.Events -> hotplaceInsight
    }
    val nextEvents = when (request.mode) {
        Season3SearchMode.Events,
        Season3SearchMode.All,
        -> normalizedEvents
        Season3SearchMode.Hotplaces -> eventInsight
    }
    val visibleResultCount = when (request.mode) {
        Season3SearchMode.Hotplaces -> result.hotplaces.places.size
        Season3SearchMode.Events -> visibleEventResults.size
        Season3SearchMode.All -> result.hotplaces.places.size + visibleEventResults.size
    }

    return copy(
        hotplaceInsight = nextHotplaces,
        eventInsight = nextEvents,
        insightStatus = result.status ?: insightStatus,
        isInsightLoading = false,
        actionMessage = if (visibleResultCount == 0) {
            "‘${request.displayKeyword}’ 검색 결과가 없습니다."
        } else {
            "‘${request.displayKeyword}’ 결과 ${visibleResultCount}개"
        },
    )
}

internal fun filterEventsForPopupLayer(
    events: List<TrendEventDto>,
    allowsNonPopupEvents: Boolean,
): List<TrendEventDto> = events.filter { event ->
    if (event.isPopup) {
        event.isVerifiedSeongsuPopup
    } else {
        allowsNonPopupEvents
    }
}

private fun deduplicateEventsPreferringVerifiedPopups(events: List<TrendEventDto>): List<TrendEventDto> {
    val uniqueEvents = linkedMapOf<String, TrendEventDto>()
    events.forEach { event ->
        val identity = eventIdentityKey(event)
        val existing = uniqueEvents[identity]
        if (existing == null || (!existing.isVerifiedSeongsuPopup && event.isVerifiedSeongsuPopup)) {
            uniqueEvents[identity] = event
        }
    }
    return uniqueEvents.values.toList()
}

private fun eventIdentityKey(event: TrendEventDto): String {
    val title = event.title.trim().lowercase()
    val startDate = event.startDate?.trim().orEmpty()
    val endDate = event.endDate?.trim().orEmpty()
    val address = event.address?.trim()?.lowercase().orEmpty()
    val hasStableIdentity = startDate.isNotBlank() || endDate.isNotBlank() || address.isNotBlank()
    return if (hasStableIdentity) {
        listOf(title, startDate, endDate, address).joinToString("|")
    } else {
        event.id
    }
}

internal fun MapUiState.withSeason3SearchFailure(message: String): MapUiState = copy(
    isInsightLoading = false,
    actionMessage = "$message · 이전 결과를 유지합니다.",
)

private fun normalizeCrowdQuery(query: String): String {
    val meaningfulTokens = tokenize(query).filterNot { token ->
        CROWD_FILLER_TOKEN_PATTERNS.any { pattern -> pattern.matches(token) }
    }
    return if (meaningfulTokens.isEmpty()) "hot-now" else query
}

private fun normalizeEventQuery(query: String): String {
    val meaningfulTokens = tokenize(query).filterNot { token ->
        EVENT_FILLER_TOKENS.contains(token)
    }.map(::normalizeEventToken)
    return meaningfulTokens.joinToString(" ").ifBlank { "팝업 행사" }
}

private fun normalizeEventToken(token: String): String = EVENT_PARTICLE_REGEX
    .matchEntire(token)
    ?.groupValues
    ?.get(1)
    ?: token

private fun tokenize(value: String): List<String> = value
    .lowercase()
    .split(NON_SEARCH_CHARACTER_REGEX)
    .map(String::trim)
    .filter(String::isNotBlank)

private val WHITESPACE_REGEX = Regex("\\s+")
private val NON_SEARCH_CHARACTER_REGEX = Regex("[^\\p{L}\\p{N}]+")
private val EVENT_PARTICLE_REGEX = Regex("(팝업|행사|축제|공연|전시|콘서트|플리마켓)(은|는|이|가|을|를)?")

private val CROWD_TERMS = listOf(
    "혼잡",
    "사람많",
    "붐빔",
    "붐비",
    "붐벼",
    "인파",
    "핫한곳",
    "핫플",
    "crowd",
    "busy",
)

private val EVENT_TERMS = listOf(
    "팝업",
    "popup",
    "행사",
    "event",
    "축제",
    "festival",
    "페스티벌",
    "공연",
    "전시",
    "콘서트",
    "플리마켓",
    "뭐하지",
    "가볼만한",
)

private val NON_POPUP_EVENT_TERMS = listOf(
    "행사",
    "event",
    "축제",
    "festival",
    "페스티벌",
    "공연",
    "전시",
    "콘서트",
    "플리마켓",
)

private val CROWD_FILLER_TOKEN_PATTERNS = listOf(
    Regex("지금|현재|서울|어디|어디가|어디에|어디서"),
    Regex("혼잡.*|붐비.*|붐벼.*|붐빔.*|사람.*|인파.*|핫한곳|핫플"),
    Regex("알려줘|보여줘|찾아줘|궁금해|확인해줘"),
)

private val EVENT_FILLER_TOKENS = setOf(
    "어디서",
    "어디에",
    "언제",
    "언제부터",
    "언제까지",
    "열려",
    "열리니",
    "열리는지",
    "하는지",
    "해",
    "있어",
    "알려줘",
    "보여줘",
    "찾아줘",
    "이번",
    "이번주",
    "주말",
    "오늘",
    "내일",
    "요즘",
)
