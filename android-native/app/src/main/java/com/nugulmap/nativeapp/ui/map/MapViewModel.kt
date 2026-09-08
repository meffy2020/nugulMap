package com.nugulmap.nativeapp.ui.map

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nugulmap.nativeapp.core.auth.AuthTokenStore
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.core.deeplink.OAuthDeepLink
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterDto
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.RecommendationEventType
import com.nugulmap.nativeapp.data.dto.RecommendationIntent
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import com.nugulmap.nativeapp.data.dto.RecommendationPlacesPayload
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.repository.AuthRepository
import com.nugulmap.nativeapp.data.repository.AuthSessionExpiredException
import com.nugulmap.nativeapp.data.repository.AuthenticatedRequestExecutor
import com.nugulmap.nativeapp.data.repository.LocationLetterRepository
import com.nugulmap.nativeapp.data.repository.RecommendationRepository
import com.nugulmap.nativeapp.data.repository.RecommendationRequestGate
import com.nugulmap.nativeapp.data.repository.RecommendationViewport
import com.nugulmap.nativeapp.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = AuthTokenStore(application)
    private val authenticatedRequests = AuthenticatedRequestExecutor(tokenStore)
    private val zoneRepository = ZoneRepository(
        tokenStore = tokenStore,
        authenticatedRequests = authenticatedRequests,
    )
    private val authRepository = AuthRepository(
        tokenStore = tokenStore,
        authenticatedRequests = authenticatedRequests,
    )
    private val recommendationRepository = RecommendationRepository(
        tokenStore = tokenStore,
        authenticatedRequests = authenticatedRequests,
    )
    private val locationLetterRepository = LocationLetterRepository(
        tokenStore = tokenStore,
        authenticatedRequests = authenticatedRequests,
    )
    private var lastLoadedMapBoundsKey: String? = null
    private var inFlightMapBoundsKey: String? = null
    private var mapRefreshJob: Job? = null
    private var searchJob: Job? = null
    private var recommendationJob: Job? = null
    private var recommendationAccountJob: Job? = null
    private var locationLetterJob: Job? = null
    private val recommendationRequestGate = RecommendationRequestGate()
    private var lastRecommendationBounds: MapBounds? = null
    private var mapRequestSequence = 0L
    private var searchRequestSequence = 0L
    private var locationLetterRequestSequence = 0L
    private var activeInsightSearch: Season3SearchRequest? = null

    private val _uiState = MutableStateFlow(
        MapUiState(isLoading = true, isSignedIn = authRepository.isSignedIn()),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        refreshMapData(MapBounds.centralSeoul, force = true)
        if (authRepository.isSignedIn()) {
            refreshProfileAndMyZones()
        }
    }

    fun refreshZones() {
        refreshZones(MapBounds.centralSeoul, force = true)
    }

    fun refreshMapData(bounds: MapBounds, force: Boolean = false) {
        val boundsKey = bounds.key()
        if (!force && (boundsKey == lastLoadedMapBoundsKey || boundsKey == inFlightMapBoundsKey)) return
        val requestId = ++mapRequestSequence
        ++searchRequestSequence
        searchJob?.cancel()
        mapRefreshJob?.cancel()
        inFlightMapBoundsKey = boundsKey
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        mapRefreshJob = viewModelScope.launch {
            val result = runCatching { zoneRepository.loadZones(bounds) }
            if (requestId != mapRequestSequence) return@launch
            inFlightMapBoundsKey = null
            if (result.isSuccess) lastLoadedMapBoundsKey = boundsKey
            _uiState.update { it.withSmokingZoneResult(result) }
        }
    }

    private fun refreshZones(bounds: MapBounds, force: Boolean = false) {
        refreshMapData(bounds, force)
    }

    fun searchSmokingZones(query: String, onFound: (ZoneDto) -> Unit) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return
        val requestId = ++searchRequestSequence
        ++mapRequestSequence
        mapRefreshJob?.cancel()
        searchJob?.cancel()
        inFlightMapBoundsKey = null
        lastLoadedMapBoundsKey = null
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionMessage = null) }
        searchJob = viewModelScope.launch {
            val result = runCatching { zoneRepository.searchZones(keyword) }
            if (requestId != searchRequestSequence) return@launch
            _uiState.update { it.withSmokingZoneResult(result) }
            result.getOrNull()?.firstOrNull()?.let { zone ->
                selectZone(zone.id)
                onFound(zone)
            }
        }
    }

    fun refreshInsights() {
        refreshMapData(MapBounds.centralSeoul, force = true)
    }

    fun searchSeason3(bounds: MapBounds, request: Season3SearchRequest) {
        activeInsightSearch = request
        val requestId = ++searchRequestSequence
        ++mapRequestSequence
        mapRefreshJob?.cancel()
        searchJob?.cancel()
        inFlightMapBoundsKey = null
        _uiState.update {
            it.copy(
                isLoading = false,
                isInsightLoading = true,
                errorMessage = null,
                actionMessage = "‘${request.displayKeyword}’ 검색 중",
            )
        }

        searchJob = viewModelScope.launch {
            runCatching {
                zoneRepository.loadMapInsights(
                    bounds = bounds,
                    keyword = request.keyword,
                    hotplaceLimit = request.hotplaceLimit,
                    eventLimit = request.eventLimit,
                )
            }.onSuccess { result ->
                if (requestId != searchRequestSequence) {
                    return@onSuccess
                }
                lastLoadedMapBoundsKey = bounds.key()
                _uiState.update { current -> current.withSeason3SearchResult(request, result) }
            }.onFailure { throwable ->
                if (requestId != searchRequestSequence) {
                    return@onFailure
                }
                val message = throwable.userFacingMessage("검색 결과를 불러오지 못했습니다.")
                _uiState.update { current -> current.withSeason3SearchFailure(message) }
            }
        }
    }

    fun clearSeason3Search() {
        activeInsightSearch = null
        ++searchRequestSequence
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isInsightLoading = false,
                actionMessage = null,
            )
        }
    }

    fun setRecommendationIntent(intent: RecommendationIntent) {
        if (_uiState.value.recommendationIntent == intent) return
        recommendationRequestGate.invalidate()
        recommendationJob?.cancel()
        _uiState.update {
            it.copy(
                recommendationIntent = intent,
                recommendations = RecommendationPlacesPayload.empty(intent),
                recommendationViewportKey = null,
                isRecommendationLoading = false,
                recommendationErrorMessage = null,
                recommendationMessage = null,
            )
        }
    }

    fun loadPeroRecommendations(bounds: MapBounds, force: Boolean = false) {
        val intent = _uiState.value.recommendationIntent
        val viewport = RecommendationViewport.from(bounds, intent)
        val current = _uiState.value
        if (!force && current.recommendationViewportKey == viewport.key &&
            (current.isRecommendationLoading || current.recommendationErrorMessage == null)
        ) {
            return
        }

        lastRecommendationBounds = bounds
        recommendationJob?.cancel()
        val requestSequence = recommendationRequestGate.begin(viewport.key)
        _uiState.update {
            it.copy(
                recommendations = RecommendationPlacesPayload.empty(intent),
                recommendationViewportKey = viewport.key,
                isRecommendationLoading = true,
                recommendationErrorMessage = null,
                recommendationMessage = null,
            )
        }

        recommendationJob = viewModelScope.launch {
            runCatching { recommendationRepository.loadPlaces(intent, bounds) }
                .onSuccess { result ->
                    if (!recommendationRequestGate.isCurrent(requestSequence, viewport.key)) {
                        return@onSuccess
                    }
                    val stillSignedIn = authRepository.isSignedIn()
                    _uiState.update {
                        it.copy(
                            recommendations = result,
                            isRecommendationLoading = false,
                            recommendationErrorMessage = null,
                            isSignedIn = stillSignedIn,
                            recommendationPreference = if (stillSignedIn) it.recommendationPreference else null,
                            savedRecommendationKeys = if (stillSignedIn) it.savedRecommendationKeys else emptySet(),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (!recommendationRequestGate.isCurrent(requestSequence, viewport.key)) {
                        return@onFailure
                    }
                    _uiState.update {
                        it.copy(
                            recommendations = RecommendationPlacesPayload.empty(intent),
                            isRecommendationLoading = false,
                            recommendationErrorMessage = throwable.userFacingMessage("맞춤 추천을 불러오지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun clearPeroRecommendations() {
        recommendationRequestGate.invalidate()
        recommendationJob?.cancel()
        _uiState.update {
            it.copy(
                recommendations = RecommendationPlacesPayload.empty(it.recommendationIntent),
                recommendationViewportKey = null,
                isRecommendationLoading = false,
                recommendationErrorMessage = null,
                recommendationMessage = null,
            )
        }
    }

    fun refreshRecommendationAccountData() {
        if (!recommendationRepository.hasSession()) {
            _uiState.update {
                it.copy(
                    recommendationPreference = null,
                    savedRecommendationKeys = emptySet(),
                )
            }
            return
        }

        recommendationAccountJob?.cancel()
        recommendationAccountJob = viewModelScope.launch {
            runCatching {
                recommendationRepository.getPreference() to recommendationRepository.getSavedItems()
            }.onSuccess { (preference, savedItems) ->
                _uiState.update {
                    it.copy(
                        recommendationPreference = preference,
                        savedRecommendationKeys = savedItems.mapTo(linkedSetOf()) { item -> item.stableKey },
                    )
                }
            }.onFailure { throwable ->
                updateAfterAuthenticatedFailure(throwable) { current ->
                    current.copy(
                        recommendationMessage = throwable.userFacingMessage("추천 설정을 불러오지 못했습니다."),
                    )
                }
            }
        }
    }

    fun setRecommendationPersonalizationEnabled(enabled: Boolean) {
        if (!_uiState.value.isSignedIn) {
            _uiState.update { it.copy(recommendationMessage = "개인화 설정은 로그인이 필요합니다.") }
            return
        }
        val currentPreference = _uiState.value.recommendationPreference ?: run {
            _uiState.update { it.copy(recommendationMessage = "개인화 설정을 불러오는 중입니다.") }
            refreshRecommendationAccountData()
            return
        }
        _uiState.update {
            it.copy(
                isRecommendationActionLoading = true,
                recommendationMessage = "개인화 설정 저장 중",
            )
        }
        viewModelScope.launch {
            runCatching {
                recommendationRepository.updatePreference(
                    currentPreference.withPersonalizationEnabled(enabled),
                )
            }.onSuccess { preference ->
                _uiState.update {
                    it.copy(
                        recommendationPreference = preference,
                        isRecommendationActionLoading = false,
                        recommendationMessage = if (preference.personalizationEnabled) {
                            "개인화 추천을 켰습니다."
                        } else {
                            "개인화 추천을 껐습니다."
                        },
                    )
                }
                lastRecommendationBounds?.let { loadPeroRecommendations(it, force = true) }
            }.onFailure { throwable ->
                updateAfterAuthenticatedFailure(throwable) { currentState ->
                    currentState.copy(
                        isRecommendationActionLoading = false,
                        recommendationMessage = throwable.userFacingMessage("개인화 설정을 저장하지 못했습니다."),
                    )
                }
            }
        }
    }

    fun openRecommendationDetail(item: RecommendationPlaceDto) {
        recordRecommendationInteraction(item, RecommendationEventType.DETAIL_OPEN)
    }

    fun routeToRecommendation(item: RecommendationPlaceDto) {
        recordRecommendationInteraction(item, RecommendationEventType.ROUTE)
    }

    fun markRecommendationNotInterested(item: RecommendationPlaceDto) {
        val recommendation = _uiState.value.recommendations
        _uiState.update {
            it.copy(
                recommendations = it.recommendations.copy(
                    items = it.recommendations.items.filterNot { candidate -> candidate.stableKey == item.stableKey },
                ),
                recommendationMessage = "이 장소를 이번 추천에서 제외했습니다.",
            )
        }
        recordRecommendationInteraction(item, RecommendationEventType.NOT_INTERESTED, recommendation)
    }

    fun toggleSavedRecommendation(item: RecommendationPlaceDto) {
        val state = _uiState.value
        if (!state.isSignedIn) {
            _uiState.update { it.copy(recommendationMessage = "장소 저장은 로그인이 필요합니다.") }
            return
        }
        val wasSaved = item.stableKey in state.savedRecommendationKeys
        val recommendation = state.recommendations
        _uiState.update {
            it.copy(
                isRecommendationActionLoading = true,
                recommendationMessage = if (wasSaved) "저장 해제 중" else "장소 저장 중",
            )
        }
        viewModelScope.launch {
            runCatching {
                if (wasSaved) {
                    recommendationRepository.deleteSavedItem(item)
                } else {
                    recommendationRepository.saveItem(item)
                }
            }.onSuccess {
                _uiState.update { currentState ->
                    val keys = currentState.savedRecommendationKeys.toMutableSet()
                    if (wasSaved) keys.remove(item.stableKey) else keys.add(item.stableKey)
                    currentState.copy(
                        savedRecommendationKeys = keys,
                        isRecommendationActionLoading = false,
                        recommendationMessage = if (wasSaved) "저장을 해제했습니다." else "장소를 저장했습니다.",
                    )
                }
                recordRecommendationInteraction(
                    item = item,
                    eventType = if (wasSaved) RecommendationEventType.UNSAVE else RecommendationEventType.SAVE,
                    recommendation = recommendation,
                )
            }.onFailure { throwable ->
                updateAfterAuthenticatedFailure(throwable) { currentState ->
                    currentState.copy(
                        isRecommendationActionLoading = false,
                        recommendationMessage = throwable.userFacingMessage(
                            if (wasSaved) "저장을 해제하지 못했습니다." else "장소를 저장하지 못했습니다.",
                        ),
                    )
                }
            }
        }
    }

    private fun recordRecommendationInteraction(
        item: RecommendationPlaceDto,
        eventType: RecommendationEventType,
        recommendation: RecommendationPlacesPayload = _uiState.value.recommendations,
    ) {
        if (!recommendation.isPersonalized || !_uiState.value.isSignedIn) return
        viewModelScope.launch {
            runCatching {
                recommendationRepository.recordInteraction(recommendation, item, eventType)
            }.onFailure { throwable ->
                if (throwable is AuthSessionExpiredException) {
                    updateAfterAuthenticatedFailure(throwable) { it }
                }
            }
        }
    }

    fun prepareLocationLetterLookup() {
        ++locationLetterRequestSequence
        locationLetterJob?.cancel()
        _uiState.update {
            it.copy(
                locationLetters = LocationLetterNearbyPayload.empty(),
                locationLetterLatitude = null,
                locationLetterLongitude = null,
                isLocationLetterLoading = true,
                isLocationLetterLikeUpdating = false,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
    }

    fun setLocationLetterLocationError(message: String) {
        ++locationLetterRequestSequence
        locationLetterJob?.cancel()
        _uiState.update {
            it.copy(
                isLocationLetterLoading = false,
                locationLetterErrorMessage = message,
                locationLetterMessage = null,
            )
        }
    }

    fun loadLocationLetters(latitude: Double, longitude: Double) {
        val requestSequence = ++locationLetterRequestSequence
        locationLetterJob?.cancel()
        _uiState.update {
            it.copy(
                locationLetterLatitude = latitude,
                locationLetterLongitude = longitude,
                isLocationLetterLoading = true,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
        locationLetterJob = viewModelScope.launch {
            runCatching { locationLetterRepository.loadNearby(latitude, longitude) }
                .onSuccess { payload ->
                    if (requestSequence != locationLetterRequestSequence) return@onSuccess
                    val stillSignedIn = authRepository.isSignedIn()
                    _uiState.update {
                        it.copy(
                            locationLetters = payload,
                            isLocationLetterLoading = false,
                            locationLetterErrorMessage = null,
                            isSignedIn = stillSignedIn,
                            currentUser = if (stillSignedIn) it.currentUser else null,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (requestSequence != locationLetterRequestSequence) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLocationLetterLoading = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("이곳의 편지를 불러오지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun refreshLocationLetters() {
        val state = _uiState.value
        val latitude = state.locationLetterLatitude
        val longitude = state.locationLetterLongitude
        if (latitude == null || longitude == null) {
            setLocationLetterLocationError("현재 위치를 다시 확인해 주세요.")
            return
        }
        loadLocationLetters(latitude, longitude)
    }

    fun createLocationLetter(content: String) {
        val state = _uiState.value
        if (!state.isSignedIn) {
            _uiState.update { it.copy(locationLetterErrorMessage = "편지를 남기려면 로그인이 필요합니다.") }
            return
        }
        val latitude = state.locationLetterLatitude
        val longitude = state.locationLetterLongitude
        if (latitude == null || longitude == null) {
            setLocationLetterLocationError("현재 위치를 다시 확인해 주세요.")
            return
        }
        val normalized = content.trim()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(locationLetterErrorMessage = "남길 문장을 입력해 주세요.") }
            return
        }
        _uiState.update {
            it.copy(
                isLocationLetterSubmitting = true,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { locationLetterRepository.create(latitude, longitude, normalized) }
                .onSuccess { letter ->
                    _uiState.update { current ->
                        val letters = listOf(letter) + current.locationLetters.letters
                            .filterNot { existing -> existing.id == letter.id }
                        current.copy(
                            locationLetters = current.locationLetters.copy(
                                letters = letters,
                                count = letters.size,
                            ),
                            isLocationLetterSubmitting = false,
                            locationLetterErrorMessage = null,
                            locationLetterMessage = "이곳에 편지를 남겼습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isLocationLetterSubmitting = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("편지를 남기지 못했습니다."),
                            locationLetterMessage = null,
                        )
                    }
                }
        }
    }

    fun deleteLocationLetter(letterId: Long) {
        _uiState.update {
            it.copy(
                isLocationLetterModerating = true,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { locationLetterRepository.delete(letterId) }
                .onSuccess {
                    _uiState.update { current ->
                        val letters = current.locationLetters.letters.filterNot { it.id == letterId }
                        current.copy(
                            locationLetters = current.locationLetters.copy(
                                letters = letters,
                                count = letters.size,
                            ),
                            isLocationLetterModerating = false,
                            locationLetterMessage = "편지를 거두었습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isLocationLetterModerating = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("편지를 거두지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun toggleLocationLetterLike(letter: LocationLetterDto) {
        val state = _uiState.value
        if (!state.isSignedIn) {
            _uiState.update { it.copy(locationLetterErrorMessage = "좋아요를 누르려면 로그인이 필요합니다.") }
            return
        }
        if (state.isLocationLetterLikeUpdating) return

        _uiState.update {
            it.copy(
                isLocationLetterLikeUpdating = true,
                locationLetterErrorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                locationLetterRepository.setLiked(letter.id, liked = !letter.likedByMe)
            }
                .onSuccess { likeState ->
                    _uiState.update { current ->
                        current.copy(
                            locationLetters = current.locationLetters.copy(
                                letters = current.locationLetters.letters.map { currentLetter ->
                                    if (currentLetter.id == likeState.letterId) {
                                        currentLetter.copy(
                                            likeCount = likeState.likeCount,
                                            likedByMe = likeState.likedByMe,
                                        )
                                    } else {
                                        currentLetter
                                    }
                                },
                            ),
                            isLocationLetterLikeUpdating = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isLocationLetterLikeUpdating = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("좋아요를 반영하지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun reportLocationLetter(letterId: Long, reason: String) {
        _uiState.update {
            it.copy(
                isLocationLetterModerating = true,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { locationLetterRepository.report(letterId, reason) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLocationLetterModerating = false,
                            locationLetterMessage = "신고가 접수되었습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isLocationLetterModerating = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("편지를 신고하지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun blockLocationLetterAuthor(authorId: Long?) {
        if (authorId == null) {
            _uiState.update { it.copy(locationLetterErrorMessage = "이 편지의 작성자를 숨길 수 없습니다.") }
            return
        }
        if (_uiState.value.currentUser?.id == authorId) {
            _uiState.update { it.copy(locationLetterErrorMessage = "내 편지는 숨길 수 없습니다.") }
            return
        }
        _uiState.update {
            it.copy(
                isLocationLetterModerating = true,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.blockUser(authorId) }
                .onSuccess {
                    _uiState.update { current ->
                        val letters = current.locationLetters.letters.filterNot { it.authorId == authorId }
                        current.copy(
                            locationLetters = current.locationLetters.copy(
                                letters = letters,
                                count = letters.size,
                            ),
                            isLocationLetterModerating = false,
                            locationLetterMessage = "이 사람의 편지를 숨겼습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isLocationLetterModerating = false,
                            locationLetterErrorMessage = throwable.userFacingMessage("편지를 숨기지 못했습니다."),
                        )
                    }
                }
        }
    }

    fun authorizationUrl(provider: OAuthProvider): String = authRepository.buildAuthorizationUrl(provider)

    fun handleOAuthCallback(uri: Uri?) {
        uri ?: return
        if (!OAuthDeepLink.isCallback(uri)) {
            return
        }

        _uiState.update { it.copy(isAuthLoading = true, authMessage = "로그인 처리 중") }
        viewModelScope.launch {
            runCatching { authRepository.exchangeCallback(uri) }
                .onSuccess { profileComplete ->
                    _uiState.update {
                        it.copy(
                            isSignedIn = true,
                            isAuthLoading = false,
                            authMessage = if (profileComplete) "로그인 완료" else "프로필 설정이 필요합니다.",
                        )
                    }
                    refreshProfileAndMyZones()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isAuthLoading = false,
                            authMessage = throwable.userFacingMessage("로그인에 실패했습니다."),
                        )
                    }
                }
        }
    }

    fun refreshProfileAndMyZones() {
        _uiState.update { it.copy(isActionLoading = true, actionMessage = "프로필/내 구역 동기화 중") }
        viewModelScope.launch {
            runCatching {
                val user = authRepository.currentUser()
                val myZones = zoneRepository.loadMyZones()
                user to myZones
            }.onSuccess { (user, myZones) ->
                _uiState.update {
                    it.copy(
                        isSignedIn = true,
                        isActionLoading = false,
                        currentUser = user,
                        myZones = myZones,
                        actionMessage = "프로필/내 구역 동기화 완료",
                    )
                }
            }.onFailure { throwable ->
                updateAfterAuthenticatedFailure(throwable) { current ->
                    current.copy(
                        isActionLoading = false,
                        actionMessage = throwable.userFacingMessage("프로필/내 구역 동기화 실패"),
                    )
                }
            }
        }
    }

    fun completeProfile(nickname: String) {
        _uiState.update { it.copy(isActionLoading = true, actionMessage = "프로필 저장 중") }
        viewModelScope.launch {
            runCatching { authRepository.completeProfile(nickname) }
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            currentUser = user,
                            authMessage = "프로필 설정 완료",
                            actionMessage = "${user.displayName} 프로필 저장 완료",
                        )
                    }
                    refreshProfileAndMyZones()
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isActionLoading = false,
                            actionMessage = throwable.userFacingMessage("프로필 저장 실패"),
                        )
                    }
                }
        }
    }

    fun createZone(payload: ZoneCreatePayload) {
        _uiState.update { it.copy(isActionLoading = true, actionMessage = "구역 등록 중") }
        viewModelScope.launch {
            runCatching { zoneRepository.createZone(payload) }
                .onSuccess { zone ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            zones = listOf(zone) + it.zones.filterNot { existing -> existing.id == zone.id },
                            myZones = listOf(zone) + it.myZones.filterNot { existing -> existing.id == zone.id },
                            selectedZoneId = zone.id,
                            actionMessage = "구역 등록 완료: ${zone.title}",
                        )
                    }
                    selectZone(zone.id)
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isActionLoading = false,
                            actionMessage = throwable.userFacingMessage("구역 등록 실패"),
                        )
                    }
                }
        }
    }

    fun selectZone(zoneId: Int) {
        _uiState.update {
            it.copy(
                selectedZoneId = zoneId,
                selectedZoneReviews = if (it.selectedZoneId == zoneId) it.selectedZoneReviews else emptyList(),
                isReviewLoading = true,
                reviewErrorMessage = null,
                reviewModerationMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.loadReviews(zoneId) }
                .onSuccess { reviews ->
                    _uiState.update { current ->
                        if (current.selectedZoneId == zoneId) {
                            current.copy(
                                selectedZoneReviews = reviews,
                                isReviewLoading = false,
                                reviewErrorMessage = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        if (current.selectedZoneId == zoneId) {
                            current.copy(
                                selectedZoneReviews = emptyList(),
                                isReviewLoading = false,
                                reviewErrorMessage = throwable.userFacingMessage("리뷰 조회 실패"),
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun dismissZoneDetail() {
        _uiState.update {
            it.copy(
                selectedZoneId = null,
                selectedZoneReviews = emptyList(),
                isReviewLoading = false,
                reviewErrorMessage = null,
                reviewModerationMessage = null,
            )
        }
    }

    fun setMapError(message: String) {
        _uiState.update { it.copy(errorMessage = message.toUserFacingStatus("지도를 표시하지 못했습니다.")) }
    }

    fun createReview(content: String) {
        val zoneId = _uiState.value.selectedZoneId ?: run {
            _uiState.update { it.copy(actionMessage = "리뷰를 남길 구역을 선택해 주세요.") }
            return
        }
        val normalized = content.trim()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(reviewErrorMessage = "리뷰 내용을 입력해 주세요.") }
            return
        }
        _uiState.update {
            it.copy(
                isReviewSubmitting = true,
                reviewErrorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.createReview(zoneId, normalized) }
                .onSuccess { review ->
                    _uiState.update { current ->
                        val nextReviews = if (current.selectedZoneId == zoneId) {
                            listOf(review) + current.selectedZoneReviews.filterNot { existing -> existing.id == review.id }
                        } else {
                            current.selectedZoneReviews
                        }
                        current.copy(
                            selectedZoneReviews = nextReviews,
                            isReviewSubmitting = false,
                            reviewErrorMessage = null,
                            actionMessage = "리뷰 등록 완료",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isReviewSubmitting = false,
                            reviewErrorMessage = throwable.userFacingMessage("리뷰 등록 실패"),
                        )
                    }
                }
        }
    }

    fun reportReview(reviewId: Int, reason: String, details: String? = null) {
        val zoneId = _uiState.value.selectedZoneId ?: run {
            _uiState.update { it.copy(reviewErrorMessage = "신고할 리뷰의 구역을 선택해 주세요.") }
            return
        }
        _uiState.update {
            it.copy(
                isReviewModerating = true,
                reviewErrorMessage = null,
                reviewModerationMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.reportReview(zoneId, reviewId, reason, details) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isReviewModerating = false,
                            reviewErrorMessage = null,
                            reviewModerationMessage = "신고가 접수되었습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isReviewModerating = false,
                            reviewErrorMessage = throwable.userFacingMessage("리뷰 신고에 실패했습니다."),
                            reviewModerationMessage = null,
                        )
                    }
                }
        }
    }

    fun reportZone(zoneId: Int, reason: String, details: String? = null) {
        if (!_uiState.value.isSignedIn) {
            showZoneReportLoginRequired()
            return
        }
        _uiState.update {
            it.copy(
                isActionLoading = true,
                actionMessage = "장소 신고 접수 중",
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.reportZone(zoneId, reason, details) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            actionMessage = "장소 신고가 접수되었습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isActionLoading = false,
                            actionMessage = zoneReportFailureMessage((throwable as? HttpException)?.code()),
                        )
                    }
                }
        }
    }

    fun showZoneReportLoginRequired() {
        _uiState.update {
            it.copy(
                isActionLoading = false,
                actionMessage = "장소 신고는 로그인이 필요합니다.",
            )
        }
    }

    fun blockReviewAuthor(authorId: Long) {
        if (_uiState.value.currentUser?.id == authorId) {
            _uiState.update { it.copy(reviewErrorMessage = "본인 계정은 차단할 수 없습니다.") }
            return
        }
        _uiState.update {
            it.copy(
                isReviewModerating = true,
                reviewErrorMessage = null,
                reviewModerationMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { zoneRepository.blockUser(authorId) }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            selectedZoneReviews = current.selectedZoneReviews.filterNot { review ->
                                review.authorId == authorId
                            },
                            isReviewModerating = false,
                            reviewErrorMessage = null,
                            reviewModerationMessage = "사용자를 차단했습니다. 해당 사용자의 리뷰는 더 이상 표시되지 않습니다.",
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isReviewModerating = false,
                            reviewErrorMessage = throwable.userFacingMessage("사용자 차단에 실패했습니다."),
                            reviewModerationMessage = null,
                        )
                    }
                }
        }
    }

    fun logout() {
        recommendationRequestGate.invalidate()
        recommendationJob?.cancel()
        recommendationAccountJob?.cancel()
        authRepository.logout()
        _uiState.update {
            it.copy(
                isSignedIn = false,
                currentUser = null,
                myZones = emptyList(),
                isActionLoading = false,
                authMessage = "로그아웃 완료",
                actionMessage = null,
                isReviewSubmitting = false,
                isReviewModerating = false,
                reviewModerationMessage = null,
                recommendationPreference = null,
                savedRecommendationKeys = emptySet(),
                recommendations = RecommendationPlacesPayload.empty(it.recommendationIntent),
                recommendationViewportKey = null,
                isRecommendationLoading = false,
                isRecommendationActionLoading = false,
                recommendationMessage = null,
                locationLetters = LocationLetterNearbyPayload.empty(),
                isLocationLetterLoading = false,
                isLocationLetterSubmitting = false,
                isLocationLetterLikeUpdating = false,
                isLocationLetterModerating = false,
                locationLetterErrorMessage = null,
                locationLetterMessage = null,
            )
        }
    }

    fun deleteAccount() {
        _uiState.update { it.copy(isActionLoading = true, actionMessage = "계정 삭제 중") }
        viewModelScope.launch {
            runCatching { authRepository.deleteAccount() }
                .onSuccess {
                    recommendationRequestGate.invalidate()
                    recommendationJob?.cancel()
                    recommendationAccountJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isSignedIn = false,
                            currentUser = null,
                            myZones = emptyList(),
                            isActionLoading = false,
                            authMessage = "계정 삭제 완료",
                            actionMessage = null,
                            isReviewSubmitting = false,
                            isReviewModerating = false,
                            reviewModerationMessage = null,
                            recommendationPreference = null,
                            savedRecommendationKeys = emptySet(),
                            recommendations = RecommendationPlacesPayload.empty(it.recommendationIntent),
                            recommendationViewportKey = null,
                            isRecommendationLoading = false,
                            isRecommendationActionLoading = false,
                            recommendationMessage = null,
                            locationLetters = LocationLetterNearbyPayload.empty(),
                            isLocationLetterLoading = false,
                            isLocationLetterSubmitting = false,
                            isLocationLetterLikeUpdating = false,
                            isLocationLetterModerating = false,
                            locationLetterErrorMessage = null,
                            locationLetterMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    updateAfterAuthenticatedFailure(throwable) { current ->
                        current.copy(
                            isActionLoading = false,
                            actionMessage = throwable.userFacingMessage("계정 삭제 실패"),
                        )
                    }
                }
        }
    }

    private fun updateAfterAuthenticatedFailure(
        throwable: Throwable,
        fallback: (MapUiState) -> MapUiState,
    ) {
        _uiState.update { current ->
            current.reduceAuthenticatedFailure(throwable, fallback)
        }
    }
}

internal fun MapUiState.reduceAuthenticatedFailure(
    throwable: Throwable,
    fallback: (MapUiState) -> MapUiState,
): MapUiState {
    if (throwable !is AuthSessionExpiredException) {
        return fallback(this)
    }

    val message = throwable.message ?: SESSION_EXPIRED_MESSAGE
    return copy(
        isSignedIn = false,
        currentUser = null,
        myZones = emptyList(),
        isAuthLoading = false,
        isActionLoading = false,
        authMessage = message,
        actionMessage = null,
        isReviewLoading = false,
        isReviewSubmitting = false,
        isReviewModerating = false,
        reviewErrorMessage = message,
        reviewModerationMessage = null,
        recommendationPreference = null,
        savedRecommendationKeys = emptySet(),
        recommendations = RecommendationPlacesPayload.empty(recommendationIntent),
        recommendationViewportKey = null,
        isRecommendationLoading = false,
        isRecommendationActionLoading = false,
        recommendationErrorMessage = null,
        recommendationMessage = message,
        isLocationLetterLoading = false,
        isLocationLetterSubmitting = false,
        isLocationLetterModerating = false,
        locationLetterErrorMessage = message,
        locationLetterMessage = null,
    )
}

private const val SESSION_EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인해 주세요."

internal fun MapUiState.withMapRefreshResults(
    zonesResult: Result<List<ZoneDto>>,
    insightResult: Result<MapInsightPayload>,
): MapUiState {
    val insight = insightResult.getOrNull()
    val zoneFailureMessage = zonesResult.exceptionOrNull()
        ?.userFacingMessage("흡연구역을 새로고침하지 못했습니다.")
    val insightFailureMessage = insightResult.exceptionOrNull()
        ?.userFacingMessage("핫플·팝업을 새로고침하지 못했습니다.")

    return copy(
        isLoading = false,
        zones = zonesResult.getOrNull() ?: zones,
        hotplaceInsight = insight?.hotplaces ?: hotplaceInsight,
        eventInsight = insight?.events ?: eventInsight,
        insightStatus = insight?.status ?: insightStatus,
        isInsightLoading = false,
        errorMessage = zoneFailureMessage,
        actionMessage = insightFailureMessage?.let { "$it 이전 결과를 유지합니다." },
    )
}

internal fun MapUiState.withSmokingZoneResult(result: Result<List<ZoneDto>>): MapUiState = result.fold(
    onSuccess = { zones ->
        copy(
            isLoading = false,
            zones = zones,
            selectedZoneId = selectedZoneId?.takeIf { id -> zones.any { it.id == id } },
            errorMessage = null,
            actionMessage = if (zones.isEmpty()) "등록된 흡연구역이 없습니다. 다른 지역을 검색하거나 지도를 이동해 주세요." else null,
        )
    },
    onFailure = { error ->
        copy(isLoading = false, errorMessage = error.userFacingMessage("흡연구역을 불러오지 못했습니다. 메뉴에서 새로고침해 주세요."))
    },
)
