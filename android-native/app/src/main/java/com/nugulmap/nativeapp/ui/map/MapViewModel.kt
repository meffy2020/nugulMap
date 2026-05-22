package com.nugulmap.nativeapp.ui.map

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nugulmap.nativeapp.core.auth.AuthTokenStore
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.core.deeplink.OAuthDeepLink
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.repository.AuthRepository
import com.nugulmap.nativeapp.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = AuthTokenStore(application)
    private val zoneRepository = ZoneRepository(tokenStore = tokenStore)
    private val authRepository = AuthRepository(tokenStore)

    private val _uiState = MutableStateFlow(
        MapUiState(isLoading = true, isSignedIn = authRepository.isSignedIn()),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        refreshZones()
        if (authRepository.isSignedIn()) {
            refreshProfileAndMyZones()
        }
    }

    fun refreshZones() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching { zoneRepository.loadCentralSeoulZones() }
                .onSuccess { zones ->
                    _uiState.update {
                        it.copy(isLoading = false, zones = zones, errorMessage = null)
                    }
                    val shouldSelectFirstZone = _uiState.value.selectedZoneId == null
                    if (shouldSelectFirstZone) {
                        zones.firstOrNull()?.id?.let { selectZone(it) }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "구역을 불러오지 못했습니다.",
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
                            authMessage = throwable.localizedMessage ?: "로그인에 실패했습니다.",
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
                _uiState.update {
                    it.copy(
                        isActionLoading = false,
                        actionMessage = throwable.localizedMessage ?: "프로필/내 구역 동기화 실패",
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
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            actionMessage = throwable.localizedMessage ?: "프로필 저장 실패",
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
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            actionMessage = throwable.localizedMessage ?: "구역 등록 실패",
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
                    _uiState.update { current ->
                        if (current.selectedZoneId == zoneId) {
                            current.copy(
                                selectedZoneReviews = emptyList(),
                                isReviewLoading = false,
                                reviewErrorMessage = throwable.localizedMessage ?: "리뷰 조회 실패",
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
            )
        }
    }

    fun setMapError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
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
                    _uiState.update {
                        it.copy(
                            isReviewSubmitting = false,
                            reviewErrorMessage = throwable.localizedMessage ?: "리뷰 등록 실패",
                        )
                    }
                }
        }
    }

    fun logout() {
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
            )
        }
    }
}
