package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.UserProfileDto
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto

data class MapUiState(
    val isLoading: Boolean = false,
    val zones: List<ZoneDto> = emptyList(),
    val selectedZoneId: Int? = null,
    val selectedZoneReviews: List<ZoneReviewDto> = emptyList(),
    val isReviewLoading: Boolean = false,
    val isReviewSubmitting: Boolean = false,
    val reviewErrorMessage: String? = null,
    val myZones: List<ZoneDto> = emptyList(),
    val currentUser: UserProfileDto? = null,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false,
    val isAuthLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val authMessage: String? = null,
    val actionMessage: String? = null,
)
