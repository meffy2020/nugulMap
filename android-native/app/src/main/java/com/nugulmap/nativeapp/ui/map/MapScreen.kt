package com.nugulmap.nativeapp.ui.map

import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.R
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.LocationLetterDto
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.RecommendationIntent
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import com.nugulmap.nativeapp.data.dto.safePublicHttpUrlOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    oauthCallbackUri: Uri? = null,
    onOAuthCallbackConsumed: () -> Unit = {},
    viewModel: MapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val openExternalUri: (ExternalUriRequest) -> Unit = { request ->
        if (openExternalUriSafely(request, uriHandler::openUri) == ExternalUriOpenResult.Failed) {
            Toast.makeText(context, EXTERNAL_URI_FAILURE_MESSAGE, Toast.LENGTH_SHORT).show()
        }
    }
    val selectedZone = uiState.zones.firstOrNull { it.id == uiState.selectedZoneId }
    var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
    var zoneReportTarget by remember { mutableStateOf<ZoneDto?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var visibleBounds by remember { mutableStateOf(MapBounds.centralSeoul) }
    var currentLocationFocus by remember { mutableStateOf<MapCurrentLocationFocus?>(null) }
    var currentLocationRequestId by remember { mutableStateOf(0L) }
    var isMapLocationLoading by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentLocationProvider = remember(context.applicationContext) {
        CurrentLocationProvider(context.applicationContext)
    }
    val moveMapToCurrentLocationWithPermission: () -> Unit = {
        if (!isMapLocationLoading) {
            isMapLocationLoading = true
            scope.launch {
                runCatching { currentLocationProvider.currentLocation() }
                    .onSuccess { location ->
                        currentLocationRequestId += 1
                        currentLocationFocus = MapCurrentLocationFocus(
                            requestId = currentLocationRequestId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                        )
                        isMapLocationLoading = false
                    }
                    .onFailure { throwable ->
                        isMapLocationLoading = false
                        val message = (throwable as? IllegalStateException)?.message
                            ?: "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    val mapLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            moveMapToCurrentLocationWithPermission()
        } else {
            isMapLocationLoading = false
            Toast.makeText(context, "현재 위치로 이동하려면 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
    val moveMapToCurrentLocation: () -> Unit = {
        if (currentLocationProvider.hasPermission()) {
            moveMapToCurrentLocationWithPermission()
        } else {
            mapLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestVisibleBounds by rememberUpdatedState(visibleBounds)

    LaunchedEffect(oauthCallbackUri) {
        oauthCallbackUri?.let { callback ->
            viewModel.handleOAuthCallback(callback)
            onOAuthCallbackConsumed()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMapData(latestVisibleBounds, force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.22f),
        drawerContent = {
            MapMenuDrawer(
                isSignedIn = uiState.isSignedIn,
                userName = uiState.currentUser?.displayName,
                userEmail = uiState.currentUser?.email,
                myZoneCount = uiState.myZones.size,
                onAccount = {
                    activeSheet = HomeSheet.Account
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    activeSheet = HomeSheet.Settings
                    scope.launch { drawerState.close() }
                },
                onReport = {
                    activeSheet = if (uiState.isSignedIn) HomeSheet.Report else HomeSheet.Account
                    scope.launch { drawerState.close() }
                },
                onRefresh = {
                    viewModel.refreshMapData(visibleBounds, force = true)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                KakaoZoneMap(
                    zones = uiState.zones,
                    hotplaces = emptyList(),
                    events = emptyList(),
                    recommendations = emptyList(),
                    selectedZoneId = uiState.selectedZoneId,
                    selectedInsightFocus = null,
                    currentLocationFocus = currentLocationFocus,
                    onZoneSelected = viewModel::selectZone,
                    onHotplaceSelected = {},
                    onEventSelected = {},
                    onRecommendationSelected = {},
                    onVisibleBoundsChanged = { bounds ->
                        visibleBounds = bounds
                        viewModel.refreshMapData(bounds)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                TopMapChrome(
                    searchQuery = searchQuery,
                    isLoading = uiState.isLoading,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchSmokingZones(searchQuery) { zone ->
                            currentLocationRequestId += 1
                            currentLocationFocus = MapCurrentLocationFocus(
                                requestId = currentLocationRequestId,
                                latitude = zone.latitude,
                                longitude = zone.longitude,
                            )
                        }
                    },
                )
                BottomMapChrome(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    selectedZone = selectedZone,
                    reviewCount = uiState.selectedZoneReviews.size,
                    statusMessage = uiState.errorMessage
                        ?: sanitizeStatusMessage(uiState.authMessage, uiState.actionMessage),
                    primaryActionLabel = "+  제보하기",
                    primaryActionContentDescription = "흡연구역 제보하기",
                    isPrimaryActionEnabled = true,
                    showPeroDiceIcon = false,
                    onRouteToZone = { zone ->
                        openExternalUri(directionsUriRequest(zone.latitude, zone.longitude, zone.title))
                    },
                    onCloseZone = viewModel::dismissZoneDetail,
                    onReviews = { activeSheet = HomeSheet.Reviews },
                    onMenu = { scope.launch { drawerState.open() } },
                    isCurrentLocationLoading = isMapLocationLoading,
                    onCurrentLocation = moveMapToCurrentLocation,
                    onZoneReport = { zone ->
                        if (uiState.isSignedIn) {
                            zoneReportTarget = zone
                        } else {
                            viewModel.showZoneReportLoginRequired()
                            activeSheet = HomeSheet.Account
                        }
                    },
                    onPrimaryAction = {
                        activeSheet = if (uiState.isSignedIn) HomeSheet.Report else HomeSheet.Account
                    },
                )

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.88f))
                            .padding(18.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }


            }
        }
    }

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            containerColor = Color.White,
            tonalElevation = 12.dp,
            scrimColor = Color.Black.copy(alpha = 0.22f),
            dragHandle = { SheetDragHandle() },
        ) {
            when (activeSheet) {
                HomeSheet.Account -> AccountSheet(
                    isSignedIn = uiState.isSignedIn,
                    isAuthLoading = uiState.isAuthLoading,
                    isActionLoading = uiState.isActionLoading,
                    authMessage = uiState.authMessage,
                    actionMessage = uiState.actionMessage,
                    userName = uiState.currentUser?.displayName,
                    userEmail = uiState.currentUser?.email,
                    myZones = uiState.myZones,
                    onLogin = { provider ->
                        openExternalUri(ExternalUriRequest(viewModel.authorizationUrl(provider)))
                    },
                    onLogout = viewModel::logout,
                    onDeleteAccount = viewModel::deleteAccount,
                    onCompleteProfile = viewModel::completeProfile,
                    onSyncAccount = viewModel::refreshProfileAndMyZones,
                    onSelectZone = { zoneId ->
                        viewModel.selectZone(zoneId)
                        activeSheet = null
                    },
                )
                HomeSheet.Report -> ReportSheet(
                    userName = uiState.currentUser?.displayName,
                    isActionLoading = uiState.isActionLoading,
                    onOpenExternalUri = { uri -> openExternalUri(ExternalUriRequest(uri)) },
                    onCreateZone = { payload ->
                        viewModel.createZone(payload)
                        activeSheet = null
                    },
                )
                HomeSheet.Reviews -> ReviewSheet(
                    selectedZone = selectedZone,
                    reviews = uiState.selectedZoneReviews,
                    isLoading = uiState.isReviewLoading,
                    isSubmitting = uiState.isReviewSubmitting,
                    isModerating = uiState.isReviewModerating,
                    errorMessage = uiState.reviewErrorMessage,
                    moderationMessage = uiState.reviewModerationMessage,
                    isSignedIn = uiState.isSignedIn,
                    currentUserId = uiState.currentUser?.id,
                    onRetry = { uiState.selectedZoneId?.let(viewModel::selectZone) },
                    onCreateReview = viewModel::createReview,
                    onReportReview = viewModel::reportReview,
                    onBlockUser = viewModel::blockReviewAuthor,
                    onOpenExternalUri = { uri -> openExternalUri(ExternalUriRequest(uri)) },
                )
                HomeSheet.Settings -> SettingsSheet(
                    onOpenExternalUri = { uri -> openExternalUri(ExternalUriRequest(uri)) },
                )
                null -> Unit
            }
        }
    }

    zoneReportTarget?.let { zone ->
        ZoneReportDialog(
            zone = zone,
            isSubmitting = uiState.isActionLoading,
            onDismiss = { zoneReportTarget = null },
            onSubmit = { reason, details ->
                viewModel.reportZone(zone.id, reason.apiValue, details)
                zoneReportTarget = null
            },
        )
    }
}

private enum class HomeSheet {
    Account,
    Report,
    Reviews,
    Settings,
}

enum class Season2LayerMode(
    val label: String,
    val showsZones: Boolean,
    val showsHotplaces: Boolean,
    val showsEvents: Boolean,
    val showsRecommendations: Boolean,
) {
    Zones("흡연구역", showsZones = true, showsHotplaces = false, showsEvents = false, showsRecommendations = false),
    Hotplaces("실시간 핫플", showsZones = false, showsHotplaces = true, showsEvents = false, showsRecommendations = false),
    Events("팝업", showsZones = false, showsHotplaces = false, showsEvents = true, showsRecommendations = false),
    Pero("추천", showsZones = false, showsHotplaces = false, showsEvents = false, showsRecommendations = true);

    val showsPeroPicker: Boolean
        get() = this == Pero

    val filterLabel: String
        get() = when (this) {
            Zones -> "흡연구역"
            Hotplaces -> "핫플"
            Events -> "팝업"
            Pero -> "추천"
        }

    val filterColor: Color
        get() = when (this) {
            Zones -> Color(0xFF315A3D)
            Hotplaces -> Color(0xFFF0642D)
            Events -> Color(0xFF3478B8)
            Pero -> Color(0xFF202124)
        }

    fun showsInsight(type: InsightSelectionType): Boolean =
        when (type) {
            InsightSelectionType.Hotplace -> showsHotplaces
            InsightSelectionType.Event -> showsEvents
            InsightSelectionType.Recommendation -> showsRecommendations
        }
}

private val RecommendationIntent.filterLabel: String
    get() = when (this) {
        RecommendationIntent.CAFE -> "카페"
        RecommendationIntent.RESTAURANT -> "음식점"
        RecommendationIntent.DATE -> "데이트"
    }

private val RecommendationIntent.filterColor: Color
    get() = when (this) {
        RecommendationIntent.CAFE -> Color(0xFFB77945)
        RecommendationIntent.RESTAURANT -> Color(0xFFF0642D)
        RecommendationIntent.DATE -> Color(0xFFE25A7A)
    }

internal fun Season3SearchMode.toLayerMode(currentMode: Season2LayerMode): Season2LayerMode = when (this) {
    Season3SearchMode.Hotplaces -> Season2LayerMode.Hotplaces
    Season3SearchMode.Events -> Season2LayerMode.Events
    Season3SearchMode.All -> currentMode
        .takeUnless { it == Season2LayerMode.Zones || it == Season2LayerMode.Pero }
        ?: Season2LayerMode.Hotplaces
}

enum class InsightSelectionType {
    Hotplace,
    Event,
    Recommendation,
}

private data class InsightSelection(
    val mapKey: String,
    val type: InsightSelectionType,
    val title: String,
    val label: String,
    val detail: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val originalUrl: String? = null,
    val providerUrl: String? = null,
    val licenseUrl: String? = null,
    val recommendation: RecommendationPlaceDto? = null,
)

private data class InsightPickerRollRequest(
    val candidates: List<InsightPickerCandidate>,
    val bounds: MapBounds,
    val viewportKey: String?,
)

private enum class PeroDiceDrawPhase {
    Scanning,
    Shuffling,
    Landing,
    Revealed,
}

private data class PeroDiceDrawState(
    val position: NormalizedMapPoint,
    val rotation: Float,
    val scale: Float,
    val blur: Float = 0f,
    val impact: Float = 0f,
    val phase: PeroDiceDrawPhase,
)

@Composable
internal fun MapCategorySelector(
    modifier: Modifier = Modifier,
    activeMode: Season2LayerMode,
    intent: RecommendationIntent,
    isSignedIn: Boolean,
    personalizationEnabled: Boolean,
    preferenceAvailable: Boolean,
    isSavingPreference: Boolean,
    onModeSelected: (Season2LayerMode) -> Unit,
    onIntentSelected: (RecommendationIntent) -> Unit,
    onPersonalizationChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activeMode == Season2LayerMode.Pero) {
            MapFilterChip(
                title = Season2LayerMode.Pero.filterLabel,
                selected = true,
                accentColor = Season2LayerMode.Pero.filterColor,
                showSparkle = true,
                onClick = { onModeSelected(Season2LayerMode.Pero) },
                contentDescription = "추천 지도 보기",
            )

            RecommendationIntent.entries.forEach { option ->
                MapFilterChip(
                    title = option.filterLabel,
                    selected = option == intent,
                    accentColor = option.filterColor,
                    onClick = { onIntentSelected(option) },
                    contentDescription = "${option.filterLabel} 추천",
                )
            }

            if (isSignedIn) {
                MapFilterChip(
                    title = if (personalizationEnabled) "맞춤 켬" else "맞춤",
                    selected = personalizationEnabled,
                    accentColor = MaterialTheme.colorScheme.primary,
                    showSparkle = true,
                    enabled = preferenceAvailable && !isSavingPreference,
                    onClick = { onPersonalizationChanged(!personalizationEnabled) },
                    contentDescription = "맞춤 추천 ${if (personalizationEnabled) "끄기" else "켜기"}",
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.Black.copy(alpha = 0.12f)),
            )

            listOf(
                Season2LayerMode.Zones,
                Season2LayerMode.Hotplaces,
                Season2LayerMode.Events,
            ).forEach { mode ->
                MapFilterChip(
                    title = mode.filterLabel,
                    selected = false,
                    accentColor = mode.filterColor,
                    onClick = { onModeSelected(mode) },
                    contentDescription = "${mode.label} 지도 보기",
                )
            }
        } else {
            listOf(
                Season2LayerMode.Zones,
                Season2LayerMode.Pero,
                Season2LayerMode.Hotplaces,
                Season2LayerMode.Events,
            ).forEach { mode ->
                MapFilterChip(
                    title = mode.filterLabel,
                    selected = mode == activeMode,
                    accentColor = mode.filterColor,
                    showSparkle = mode == Season2LayerMode.Pero,
                    onClick = { onModeSelected(mode) },
                    contentDescription = "${mode.label} 지도 보기",
                )
            }
        }
    }
}

@Composable
private fun MapFilterChip(
    title: String,
    selected: Boolean,
    accentColor: Color,
    showSparkle: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier
            .height(FILTER_CHIP_VISUAL_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (selected) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFFF5F5F5) else Color.White.copy(alpha = if (enabled) 0.96f else 0.72f),
        shadowElevation = 7.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSparkle) {
                RecommendationSparkIcon(color = accentColor, modifier = Modifier.size(15.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(accentColor, CircleShape),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TopMapChrome(
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(MINIMUM_TOUCH_TARGET_DP.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEARCH_FIELD_VISUAL_HEIGHT_DP.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
        ) {}

        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = SEARCH_FIELD_ACCESSIBILITY_LABEL },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = MINIMUM_TOUCH_TARGET_DP.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (isLoading) "갱신 중" else "흡연구역·주소 검색",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(MINIMUM_TOUCH_TARGET_DP.dp)
                .clickable(
                    enabled = searchQuery.isNotBlank() && !isLoading,
                    role = Role.Button,
                    onClick = onSearch,
                )
                .semantics { contentDescription = SEARCH_ACTION_ACCESSIBILITY_LABEL },
            contentAlignment = Alignment.Center,
        ) {
            CompactSearchIcon(
                color = if (searchQuery.isNotBlank() && !isLoading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun CompactSearchIcon(
    color: Color,
) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.29f,
            center = Offset(size.width * 0.43f, size.height * 0.43f),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.64f),
            end = Offset(size.width * 0.86f, size.height * 0.86f),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun RecommendationSparkIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val center = Offset(size.width * 0.43f, size.height * 0.43f)
        drawLine(color, Offset(center.x, size.height * 0.04f), Offset(center.x, size.height * 0.82f), stroke)
        drawLine(color, Offset(size.width * 0.04f, center.y), Offset(size.width * 0.82f, center.y), stroke)
        drawLine(color, Offset(size.width * 0.18f, size.height * 0.18f), Offset(size.width * 0.68f, size.height * 0.68f), stroke * 0.72f)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.18f), Offset(size.width * 0.18f, size.height * 0.68f), stroke * 0.72f)
        drawCircle(color, radius = stroke * 0.9f, center = Offset(size.width * 0.84f, size.height * 0.82f))
    }
}

@Composable
private fun RecommendationToken(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 12.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RecommendationSparkIcon(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CompactMenuIcon(
    color: Color,
) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        listOf(0.28f, 0.50f, 0.72f).forEach { y ->
            drawLine(
                color = color,
                start = Offset(size.width * 0.18f, size.height * y),
                end = Offset(size.width * 0.82f, size.height * y),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun CurrentLocationIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.28f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = stroke))
        drawCircle(color = color, radius = size.minDimension * 0.08f, center = center)
        drawLine(color, Offset(center.x, 0f), Offset(center.x, center.y - radius), stroke)
        drawLine(color, Offset(center.x, center.y + radius), Offset(center.x, size.height), stroke)
        drawLine(color, Offset(0f, center.y), Offset(center.x - radius, center.y), stroke)
        drawLine(color, Offset(center.x + radius, center.y), Offset(size.width, center.y), stroke)
    }
}

@Composable
private fun MapMenuDrawer(
    isSignedIn: Boolean,
    userName: String?,
    userEmail: String?,
    myZoneCount: Int,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onRefresh: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 326.dp),
        drawerContainerColor = Color.White.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("너굴맵", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                if (isSignedIn) userName.orEmpty() else "로그인",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!userEmail.isNullOrBlank()) {
                Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))
            MenuActionRow(title = if (isSignedIn) "내 프로필 / 내 구역 ${myZoneCount}개" else "로그인 / 프로필", onClick = onAccount)
            MenuActionRow(title = "흡연구역 제보", onClick = onReport)
            MenuActionRow(title = "지도 새로고침", onClick = onRefresh)
            MenuActionRow(title = "설정", onClick = onSettings)

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MenuActionRow(
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFFF7F7F2))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Season2InsightDetailCard(
    modifier: Modifier = Modifier,
    insight: InsightSelection,
    onClose: () -> Unit,
    onRoute: () -> Unit,
    onOpenOriginal: (() -> Unit)?,
    onOpenSource: (() -> Unit)?,
    onOpenLicense: (() -> Unit)?,
    isSaved: Boolean,
    isRecommendationActionLoading: Boolean,
    onSave: (() -> Unit)?,
    onNotInterested: (() -> Unit)?,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                paneTitle = "선택한 지도 장소 상세"
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(5.dp)
                    .background(Color.Black.copy(alpha = 0.14f), RoundedCornerShape(999.dp)),
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        insight.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        insight.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("닫기")
                }
            }

            insight.address?.takeIf { it.isNotBlank() }?.let { address ->
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            insight.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Spacer(Modifier.weight(1f))
                onNotInterested?.let { dismissRecommendation ->
                    TextButton(onClick = dismissRecommendation) {
                        Text("관심없음")
                    }
                }
                onSave?.let { saveRecommendation ->
                    OutlinedButton(
                        onClick = saveRecommendation,
                        enabled = !isRecommendationActionLoading,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(if (isSaved) "저장됨" else "저장")
                    }
                }
                onOpenOriginal?.let { openOriginal ->
                    TextButton(
                        onClick = openOriginal,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text("원문")
                    }
                }
                onOpenSource?.let { openSource ->
                    TextButton(
                        onClick = openSource,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(if (onSave == null) "출처" else "정보")
                    }
                }
                onOpenLicense?.let { openLicense ->
                    TextButton(
                        onClick = openLicense,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text("이용조건")
                    }
                }
                Button(
                    onClick = onRoute,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("길찾기")
                }
            }
        }
    }
}

private fun HotplaceDto.toInsightSelection(): InsightSelection {
    val details = listOfNotNull(
        formatHotplaceFreshness(freshnessStatus, ageSeconds),
        crowdMessage
            ?.takeIf {
                it.isNotBlank() && (freshnessStatus == "CURRENT" || freshnessStatus == "DELAYED")
            },
        updatedAt?.takeIf { it.isNotBlank() }?.let { "갱신 $it" },
    )
    return InsightSelection(
        mapKey = "hot:$id",
        type = InsightSelectionType.Hotplace,
        title = name,
        label = crowdLabel,
        detail = details.joinToString(" · ").ifBlank { null },
        address = address,
        latitude = latitude,
        longitude = longitude,
    )
}

internal fun formatHotplaceFreshness(
    freshnessStatus: String?,
    ageSeconds: Long?,
): String? {
    val elapsed = ageSeconds
        ?.takeIf { it >= 0 }
        ?.let { "${it / 60}분 경과" }
    val status = when (freshnessStatus) {
        "CURRENT" -> "방금 업데이트"
        "DELAYED" -> "측정 지연"
        "STATIC" -> "참고 후보"
        "UNKNOWN" -> "참고 후보"
        else -> return null
    }
    return if (freshnessStatus == "CURRENT" || freshnessStatus == "DELAYED") {
        listOfNotNull(status, elapsed).joinToString(" · ")
    } else {
        status
    }
}

internal fun formatHotplaceComparisonLabel(place: HotplaceDto): String = listOfNotNull(
    place.crowdLabel,
    formatHotplaceFreshness(place.freshnessStatus, place.ageSeconds),
).distinct().joinToString(" · ")

private fun TrendEventDto.toInsightSelection(): InsightSelection = InsightSelection(
    mapKey = "event:$id",
    type = InsightSelectionType.Event,
    title = title,
    label = eventLabel,
    detail = formatEventDetail(startDate, endDate, sourceLabel, licenseLabel),
    address = address,
    latitude = latitude,
    longitude = longitude,
    originalUrl = safeEventSourceUrl(detailUrl, sourceContentId),
    providerUrl = sourceUrl.safePublicHttpUrlOrNull(),
    licenseUrl = licenseUrl.safePublicHttpUrlOrNull(),
)

private fun RecommendationPlaceDto.toInsightSelection(): InsightSelection = InsightSelection(
    mapKey = mapKey,
    type = InsightSelectionType.Recommendation,
    title = name,
    label = category.ifBlank { "추천 장소" },
    detail = distanceMeters?.let { "거리 ${it}m" },
    address = address,
    latitude = latitude,
    longitude = longitude,
    providerUrl = sourceUrl
        .replaceFirst("http://place.map.kakao.com", "https://place.map.kakao.com")
        .safePublicHttpUrlOrNull(),
    recommendation = this,
)

internal fun recommendationDataStatusMessage(
    dataStatus: String,
    isLoading: Boolean,
): String? {
    if (isLoading) return null
    return when (dataStatus) {
        "PROVIDER_NOT_CONFIGURED" -> "추천 데이터 연결이 필요합니다."
        "PROVIDER_UNAVAILABLE" -> "추천 데이터를 불러올 수 없습니다."
        else -> null
    }
}

private fun InsightSelection.toMapInsightFocus(): MapInsightFocus = MapInsightFocus(
    key = mapKey,
    latitude = latitude,
    longitude = longitude,
)

internal fun formatEventDetail(
    startDate: String?,
    endDate: String?,
    attribution: String? = null,
    license: String? = null,
): String? = listOfNotNull(
    startDate?.trim()?.takeIf { it.isNotEmpty() }?.let { "시작 $it" },
    endDate?.trim()?.takeIf { it.isNotEmpty() }?.let { "종료 $it" },
    attribution?.trim()?.takeIf { it.isNotEmpty() }?.let { "출처: $it" },
    license?.trim()?.takeIf { it.isNotEmpty() }?.let { "이용조건: $it" },
).joinToString(" · ").ifBlank { null }

internal fun formatEventListLabel(
    kind: String?,
    period: String?,
    startDate: String?,
    endDate: String?,
): String {
    val kindLabel = when (kind) {
        "popup" -> "팝업"
        "festival" -> "축제"
        else -> "행사"
    }
    val normalizedStart = startDate?.trim()?.takeIf(String::isNotEmpty)
    val normalizedEnd = endDate?.trim()?.takeIf(String::isNotEmpty)
    val dateLabel = when {
        normalizedStart != null && normalizedEnd != null && normalizedStart != normalizedEnd -> "$normalizedStart ~ $normalizedEnd"
        normalizedStart != null -> normalizedStart
        normalizedEnd != null -> normalizedEnd
        else -> period?.trim()?.takeIf(String::isNotEmpty)
    }
    return listOfNotNull(kindLabel, dateLabel).joinToString(" · ")
}

internal fun safeEventSourceUrl(
    detailUrl: String?,
    sourceContentId: String?,
): String? = sequenceOf(detailUrl, sourceContentId)
    .mapNotNull { it.safePublicHttpUrlOrNull() }
    .firstOrNull()

@Composable
private fun PeroDiceDrawOverlay(
    state: PeroDiceDrawState,
    modifier: Modifier = Modifier,
) {
    val phaseLabel = when (state.phase) {
        PeroDiceDrawPhase.Scanning -> "지도 후보 확인 중"
        PeroDiceDrawPhase.Shuffling -> "추천 장소를 고르는 중"
        PeroDiceDrawPhase.Landing -> "추천 장소를 선택하는 중"
        PeroDiceDrawPhase.Revealed -> "장소 선택 완료"
    }
    val density = LocalDensity.current
    val trailOffsetX = with(density) { 13.dp.roundToPx() }
    val trailOffsetY = with(density) { 8.dp.roundToPx() }
    val trailSize = with(density) { 48.dp.roundToPx() }
    val tokenSize = with(density) { 56.dp.roundToPx() }
    val landingSize = with(density) { 78.dp.roundToPx() }

    BoxWithConstraints(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = phaseLabel
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        repeat(PERO_DICE_TRAIL_COUNT) { index ->
            RecommendationToken(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (widthPx * state.position.x).roundToInt() - trailSize / 2 - index * trailOffsetX,
                            y = (heightPx * state.position.y).roundToInt() - trailSize / 2 + index * trailOffsetY,
                        )
                    }
                    .size(48.dp)
                    .blur((index + state.blur * 0.42f).dp)
                    .graphicsLayer {
                        alpha = 0.18f - index * 0.032f
                        rotationZ = state.rotation - index * 18f
                    },
            )
        }

        RecommendationToken(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (widthPx * state.position.x).roundToInt() - tokenSize / 2,
                        y = (heightPx * state.position.y).roundToInt() - tokenSize / 2,
                    )
                }
                .size(56.dp)
                .blur(state.blur.dp)
                .graphicsLayer {
                    rotationZ = state.rotation
                    scaleX = state.scale
                    scaleY = state.scale
                    shadowElevation = 14.dp.toPx() + state.impact * 6.dp.toPx()
                },
        )

        if (state.phase == PeroDiceDrawPhase.Landing || state.phase == PeroDiceDrawPhase.Revealed) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (widthPx * state.position.x).roundToInt() - landingSize / 2,
                            y = (heightPx * state.position.y).roundToInt() - landingSize / 2,
                        )
                    }
                    .size(78.dp)
                    .border(
                        width = 8.dp,
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (state.phase == PeroDiceDrawPhase.Revealed) 0.08f else 0.34f,
                        ),
                        shape = CircleShape,
                    )
                    .graphicsLayer {
                        scaleX = if (state.phase == PeroDiceDrawPhase.Revealed) 1.22f else 0.96f
                        scaleY = if (state.phase == PeroDiceDrawPhase.Revealed) 1.22f else 0.96f
                    },
            )
        }
    }
}

@Composable
private fun LocationLetterMapOverlay(
    payload: LocationLetterNearbyPayload,
    hasLocation: Boolean,
    isLoading: Boolean,
    isSubmitting: Boolean,
    isLikeUpdating: Boolean,
    isModerating: Boolean,
    errorMessage: String?,
    message: String?,
    isSignedIn: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onToggleLike: (LocationLetterDto) -> Unit,
    onDelete: (Long) -> Unit,
    onReport: (Long, String) -> Unit,
    onBlock: (Long?) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var isComposing by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<LocationLetterDto?>(null) }
    var reportTarget by remember { mutableStateOf<LocationLetterDto?>(null) }
    val letters = payload.letters
    val selectedLetter = letters.getOrNull(selectedIndex.coerceIn(0, (letters.size - 1).coerceAtLeast(0)))

    LaunchedEffect(message) {
        if (message == "이곳에 편지를 남겼습니다.") {
            draft = ""
            isComposing = false
            selectedIndex = 0
        }
    }

    LaunchedEffect(letters.map { it.id }) {
        selectedIndex = if (letters.isEmpty()) 0 else selectedIndex.coerceAtMost(letters.lastIndex)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.66f))
            .semantics { paneTitle = "주변 편지" },
    ) {
        val overlayHeight = maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(overlayHeight * 0.24f))
            LocationLetterCallout(
                letter = selectedLetter,
                hasLocation = hasLocation,
                isLoading = isLoading,
                isSubmitting = isSubmitting,
                isLikeUpdating = isLikeUpdating,
                errorMessage = errorMessage,
                isComposing = isComposing,
                draft = draft,
                onDraftChange = { draft = it.take(500) },
                onSubmit = { onCreate(draft) },
                onToggleLike = {
                    if (isSignedIn) selectedLetter?.let(onToggleLike) else onLogin()
                },
                showsMenu = selectedLetter?.mine == true || isSignedIn,
                onMore = { selectedLetter?.let { actionTarget = it } },
                onRetry = onRetry,
                onRefresh = onRefresh,
            )
            Canvas(
                modifier = Modifier
                    .offset(y = (-1).dp)
                    .size(width = 28.dp, height = 18.dp),
            ) {
                val pointer = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(pointer, color = Color.White)
                drawPath(
                    pointer,
                    color = Color.Black.copy(alpha = 0.78f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier
                    .offset(y = (-3).dp)
                    .size(14.dp)
                    .background(Color(0xFFC7655E), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
            if (letters.size > 1) {
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    letters.indices.take(9).forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (index == selectedIndex) Color.Black.copy(alpha = 0.76f)
                                    else Color.White.copy(alpha = 0.90f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }

        if (letters.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LocationLetterNavigationButton(
                    symbol = "‹",
                    description = "이전 편지",
                    onClick = {
                        selectedIndex = (selectedIndex - 1 + letters.size) % letters.size
                    },
                )
                LocationLetterNavigationButton(
                    symbol = "›",
                    description = "다음 편지",
                    onClick = {
                        selectedIndex = (selectedIndex + 1) % letters.size
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocationLetterNavigationButton(
                symbol = "×",
                description = "편지 닫기",
                onClick = onDismiss,
            )
            if (hasLocation) {
                LocationLetterNavigationButton(
                    symbol = if (isComposing) "−" else "+",
                    description = if (isComposing) "작성 취소" else "편지 쓰기",
                    onClick = {
                        if (isComposing) {
                            isComposing = false
                        } else if (isSignedIn) {
                            isComposing = true
                        } else {
                            onLogin()
                        }
                    },
                )
            }
        }
    }

    actionTarget?.let { letter ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text("편지 메뉴", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (letter.mine) {
                        TextButton(
                            onClick = {
                                onDelete(letter.id)
                                actionTarget = null
                            },
                            enabled = !isModerating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("내 편지 거두기", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    } else if (isSignedIn) {
                        TextButton(
                            onClick = {
                                reportTarget = letter
                                actionTarget = null
                            },
                            enabled = !isModerating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("신고하기", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                        TextButton(
                            onClick = {
                                onBlock(letter.authorId)
                                actionTarget = null
                            },
                            enabled = !isModerating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("이 사람의 편지 숨기기", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("취소") }
            },
        )
    }

    reportTarget?.let { letter ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("이 편지를 신고할까요?", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    locationLetterReportReasons.forEach { (label, reason) ->
                        TextButton(
                            onClick = {
                                onReport(letter.id, reason)
                                reportTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                color = Color.Black.copy(alpha = 0.82f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reportTarget = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun LocationLetterCallout(
    letter: LocationLetterDto?,
    hasLocation: Boolean,
    isLoading: Boolean,
    isSubmitting: Boolean,
    isLikeUpdating: Boolean,
    errorMessage: String?,
    isComposing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleLike: () -> Unit,
    showsMenu: Boolean,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 54.dp)
            .widthIn(max = 520.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.78f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                isComposing -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color.Black.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 11.dp, vertical = 10.dp),
                        enabled = !isSubmitting,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                        cursorBrush = SolidColor(Color.Black),
                        decorationBox = { innerTextField ->
                            Box {
                                if (draft.isBlank()) {
                                    Text("남길 문장", color = Color.Black.copy(alpha = 0.40f))
                                }
                                innerTextField()
                            }
                        },
                    )
                    Button(
                        onClick = onSubmit,
                        enabled = draft.isNotBlank() && !isSubmitting,
                        modifier = Modifier
                            .size(40.dp)
                            .semantics { contentDescription = "편지 남기기" },
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("↑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                isLoading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                    Text("불러오는 중", color = Color.Black.copy(alpha = 0.64f))
                }

                letter != null -> {
                    Text(
                        text = letter.content,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                        color = Color.Black.copy(alpha = 0.90f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(letter.authorLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            locationLetterDate(letter.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.48f),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onToggleLike,
                            enabled = !isLikeUpdating,
                            modifier = Modifier
                                .height(32.dp)
                                .semantics {
                                    contentDescription = if (letter.likedByMe) "좋아요 취소" else "좋아요"
                                },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            LocationLetterHeartIcon(
                                filled = letter.likedByMe,
                                color = if (letter.likedByMe) Color(0xFFD34C45) else Color.Black.copy(alpha = 0.66f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                letter.likeCount.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black.copy(alpha = 0.68f),
                            )
                        }
                        if (showsMenu) {
                            TextButton(
                                onClick = onMore,
                                modifier = Modifier
                                    .height(32.dp)
                                    .semantics { contentDescription = "편지 메뉴" },
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                            ) {
                                Text("•••", color = Color.Black.copy(alpha = 0.58f))
                            }
                        }
                    }
                }

                !hasLocation || errorMessage != null -> {
                    Text(
                        errorMessage ?: "현재 위치를 확인하지 못했습니다.",
                        color = Color.Black.copy(alpha = 0.74f),
                    )
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Text("현재 위치 다시 확인", color = Color.Black)
                    }
                }

                else -> {
                    Text(
                        "아직 남겨진 문장이 없습니다.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                        color = Color.Black.copy(alpha = 0.84f),
                    )
                    TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) {
                        Text("새로 보기", color = Color.Black.copy(alpha = 0.62f))
                    }
                }
            }

            errorMessage?.takeIf { (letter != null || isComposing) && it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB2453D),
                )
            }
        }
    }
}

@Composable
private fun LocationLetterNavigationButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall, color = Color.Black.copy(alpha = 0.64f))
        }
    }
}

@Composable
private fun LocationLetterEnvelopeIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(strokeWidth, strokeWidth * 1.5f),
            end = Offset(size.width / 2f, size.height * 0.58f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(size.width - strokeWidth, strokeWidth * 1.5f),
            end = Offset(size.width / 2f, size.height * 0.58f),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun LocationLetterHeartIcon(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val heart = Path().apply {
            moveTo(size.width / 2f, size.height * 0.88f)
            cubicTo(
                size.width * 0.43f,
                size.height * 0.80f,
                size.width * 0.10f,
                size.height * 0.58f,
                size.width * 0.10f,
                size.height * 0.34f,
            )
            cubicTo(
                size.width * 0.10f,
                size.height * 0.12f,
                size.width * 0.36f,
                size.height * 0.08f,
                size.width / 2f,
                size.height * 0.27f,
            )
            cubicTo(
                size.width * 0.64f,
                size.height * 0.08f,
                size.width * 0.90f,
                size.height * 0.12f,
                size.width * 0.90f,
                size.height * 0.34f,
            )
            cubicTo(
                size.width * 0.90f,
                size.height * 0.58f,
                size.width * 0.57f,
                size.height * 0.80f,
                size.width / 2f,
                size.height * 0.88f,
            )
            close()
        }
        drawPath(
            path = heart,
            color = color,
            style = if (filled) {
                androidx.compose.ui.graphics.drawscope.Fill
            } else {
                Stroke(width = 1.7.dp.toPx())
            },
        )
    }
}

private fun locationLetterDate(rawValue: String?): String {
    if (rawValue.isNullOrBlank()) return ""
    val date = runCatching { Instant.parse(rawValue).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(rawValue).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(rawValue.take(10)) }.getOrNull()
        ?: return rawValue.take(10).replace('-', '.')
    return "${date.year}. ${date.monthValue}. ${date.dayOfMonth}."
}

private val locationLetterReportReasons = listOf(
    "공격적인 표현" to "OFFENSIVE",
    "괴롭힘" to "HARASSMENT",
    "도배 또는 광고" to "SPAM",
    "개인정보 노출" to "PERSONAL_INFORMATION",
)

@Composable
private fun BottomMapChrome(
    modifier: Modifier = Modifier,
    selectedZone: ZoneDto?,
    reviewCount: Int,
    statusMessage: String?,
    primaryActionLabel: String,
    primaryActionContentDescription: String,
    isPrimaryActionEnabled: Boolean,
    showPeroDiceIcon: Boolean,
    onRouteToZone: (ZoneDto) -> Unit,
    onCloseZone: () -> Unit,
    onReviews: () -> Unit,
    onMenu: () -> Unit,
    isCurrentLocationLoading: Boolean,
    onCurrentLocation: () -> Unit,
    onZoneReport: (ZoneDto) -> Unit,
    onPrimaryAction: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!statusMessage.isNullOrBlank()) {
            StatusToast(statusMessage)
        }

        if (selectedZone != null) {
            SelectedZoneCard(
                modifier = Modifier.fillMaxWidth(0.92f),
                zone = selectedZone,
                reviewCount = reviewCount,
                onRoute = { onRouteToZone(selectedZone) },
                onClose = onCloseZone,
                onReviews = onReviews,
                onReport = { onZoneReport(selectedZone) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier
                        .size(MINIMUM_TOUCH_TARGET_DP.dp)
                        .clickable(role = Role.Button, onClick = onMenu)
                        .semantics { contentDescription = "메뉴 열기" },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 10.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CompactMenuIcon(color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(MINIMUM_TOUCH_TARGET_DP.dp)
                        .clickable(
                            enabled = !isCurrentLocationLoading,
                            role = Role.Button,
                            onClick = onCurrentLocation,
                        )
                        .semantics { contentDescription = CURRENT_LOCATION_ACCESSIBILITY_LABEL },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 10.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCurrentLocationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            CurrentLocationIcon(
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

            }
            Button(
                onClick = onPrimaryAction,
                enabled = isPrimaryActionEnabled,
                modifier = Modifier.semantics {
                    contentDescription = primaryActionContentDescription
                },
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (showPeroDiceIcon) {
                    RecommendationSparkIcon(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(primaryActionLabel, fontWeight = FontWeight.Black)
            }
        }
    }
}


@Composable
private fun StatusToast(message: String) {
    val fontScale = LocalDensity.current.fontScale

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 10.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = statusToastMaxLines(fontScale),
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun SelectedZoneCard(
    modifier: Modifier = Modifier,
    zone: ZoneDto,
    reviewCount: Int,
    onClose: () -> Unit,
    onReviews: () -> Unit,
    onReport: () -> Unit,
    onRoute: () -> Unit,
) {
    Surface(
        modifier = modifier.semantics {
            paneTitle = "선택한 흡연구역 상세"
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("흡연구역", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(zone.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onClose) { Text("닫기") }
            }
            if (zone.summary.isNotBlank()) {
                Text(zone.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(zone.address ?: "주소 정보 없음", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!zone.description.isNullOrBlank()) {
                Text(zone.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onRoute, modifier = Modifier.fillMaxWidth()) {
                Text("길찾기")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("리뷰 ${reviewCount}개", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onReport,
                        modifier = Modifier.semantics {
                            contentDescription = ZONE_REPORT_ACCESSIBILITY_LABEL
                        },
                    ) { Text("장소 신고") }
                    OutlinedButton(onClick = onReviews, shape = RoundedCornerShape(14.dp)) { Text("리뷰 보기") }
                }
            }
        }
    }
}

@Composable
private fun ZoneReportDialog(
    zone: ZoneDto,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ZoneReportReason, String?) -> Unit,
) {
    var reason by remember(zone.id) { mutableStateOf(ZoneReportReason.Inaccurate) }
    var details by remember(zone.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("장소 신고") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    zone.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "지도 정보에 문제가 있는 이유를 선택해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ZoneReportReason.entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == item,
                                onClick = { reason = item },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reason == item,
                            onClick = null,
                        )
                        Text(item.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (reason == ZoneReportReason.Other) {
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it.take(REPORT_DETAILS_MAX_LENGTH) },
                        label = { Text("상세 내용") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = { Text("${details.trim().length}/$REPORT_DETAILS_MAX_LENGTH") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        reason,
                        details.trim().takeIf { it.isNotBlank() },
                    )
                },
                enabled = !isSubmitting && (reason != ZoneReportReason.Other || details.trim().isNotBlank()),
                modifier = Modifier.semantics { contentDescription = "장소 신고 접수" },
            ) {
                Text(if (isSubmitting) "접수 중" else "신고 접수")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("취소") }
        },
    )
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(42.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFD6D8D2)),
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
}

@Composable
private fun SettingsSheet(onOpenExternalUri: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTitle("설정")
        Text("너굴맵", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "서비스 정책과 계정 삭제 안내를 웹에서 확인할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MenuActionRow(title = "개인정보 처리방침") {
            onOpenExternalUri(PRIVACY_POLICY_URL)
        }
        MenuActionRow(title = "이용약관 · 커뮤니티 운영정책") {
            onOpenExternalUri(TERMS_OF_USE_URL)
        }
        MenuActionRow(title = "계정 삭제 안내") {
            onOpenExternalUri(ACCOUNT_DELETION_URL)
        }
        MenuActionRow(title = "로그인 연결 관리") {
            onOpenExternalUri(LOGIN_CONNECTIONS_URL)
        }
        MenuActionRow(title = "고객지원") {
            onOpenExternalUri(SUPPORT_URL)
        }
    }
}

@Composable
private fun AccountSheet(
    isSignedIn: Boolean,
    isAuthLoading: Boolean,
    isActionLoading: Boolean,
    authMessage: String?,
    actionMessage: String?,
    userName: String?,
    userEmail: String?,
    myZones: List<ZoneDto>,
    onLogin: (OAuthProvider) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCompleteProfile: (String) -> Unit,
    onSyncAccount: () -> Unit,
    onSelectZone: (Int) -> Unit,
) {
    var nickname by remember(userName) { mutableStateOf(userName.orEmpty()) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("계정을 삭제할까요?") },
            text = {
                Text(
                    "계정과 로그인 정보, 작성한 리뷰와 등록한 흡연구역, 연결된 구역 이미지와 프로필 이미지가 모두 삭제됩니다. 삭제 후 복구할 수 없습니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    enabled = !isActionLoading,
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("취소") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetTitle("프로필")
        Text(
            if (isSignedIn) userName.orEmpty() else "로그인",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!userEmail.isNullOrBlank()) Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (isSignedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSyncAccount, enabled = !isAuthLoading && !isActionLoading) { Text("내 정보 동기화") }
                OutlinedButton(onClick = onLogout, enabled = !isAuthLoading) { Text("로그아웃") }
            }
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("닉네임") },
                enabled = !isActionLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onCompleteProfile(nickname) },
                enabled = !isActionLoading && nickname.trim().length in 2..20,
            ) { Text(if (isActionLoading) "처리 중" else "프로필 저장") }

            OutlinedButton(
                onClick = { showDeleteAccountDialog = true },
                enabled = !isActionLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("계정 삭제", color = MaterialTheme.colorScheme.error) }
        } else {
            OAuthProvider.entries.forEach { provider ->
                OutlinedButton(
                    onClick = { onLogin(provider) },
                    enabled = !isAuthLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("${provider.displayName} 로그인") }
            }
        }

        if (!authMessage.isNullOrBlank()) Text(authMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!actionMessage.isNullOrBlank()) Text(actionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(2.dp))
        Text("내가 등록한 구역 ${myZones.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        when {
            !isSignedIn -> Text("로그인하면 내 구역이 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            myZones.isEmpty() -> Text("아직 등록한 구역이 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> myZones.take(5).forEach { zone -> MyZoneRow(zone = zone, onClick = { onSelectZone(zone.id) }) }
        }
    }
}

@Composable
private fun ReportSheet(
    userName: String?,
    isActionLoading: Boolean,
    onOpenExternalUri: (String) -> Unit,
    onCreateZone: (ZoneCreatePayload) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var policyAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTitle("흡연구역 제보")
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("구역 주소") }, enabled = !isActionLoading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("설명") }, enabled = !isActionLoading, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("위도") }, enabled = !isActionLoading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("경도") }, enabled = !isActionLoading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        if (!validationMessage.isNullOrBlank()) Text(validationMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        UgcPolicyAcceptance(
            accepted = policyAccepted,
            onAcceptedChange = { policyAccepted = it },
            onOpenPolicy = { onOpenExternalUri(TERMS_OF_USE_URL) },
        )
        Button(
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lng = longitude.toDoubleOrNull()
                validationMessage = when {
                    address.trim().length < 5 -> "주소는 5자 이상 입력해 주세요."
                    description.trim().isBlank() -> "설명을 입력해 주세요."
                    lat == null || lat !in -90.0..90.0 -> "위도는 -90~90 사이 숫자여야 합니다."
                    lng == null || lng !in -180.0..180.0 -> "경도는 -180~180 사이 숫자여야 합니다."
                    else -> null
                }
                if (validationMessage == null && lat != null && lng != null) {
                    onCreateZone(
                        ZoneCreatePayload(
                            region = "서울특별시",
                            type = "실외",
                            subtype = "기타",
                            description = description.trim(),
                            latitude = lat,
                            longitude = lng,
                            size = "소형",
                            address = address.trim(),
                            user = userName?.takeIf { it.isNotBlank() } ?: "사용자",
                        ),
                    )
                }
            },
            enabled = canSubmitZoneUgc(policyAccepted = policyAccepted, isBusy = isActionLoading),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (isActionLoading) "처리 중" else "제보 등록") }
    }
}

@Composable
private fun MyZoneRow(zone: ZoneDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFFF7F7F2))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(zone.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (zone.summary.isNotBlank()) Text(zone.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sanitizeStatusMessage(authMessage: String?, actionMessage: String?): String? =
    firstStatusMessage(actionMessage, authMessage)
        ?.toUserFacingStatus("지도를 표시하지 못했습니다.")

@Composable
private fun ReviewSheet(
    selectedZone: ZoneDto?,
    reviews: List<ZoneReviewDto>,
    isLoading: Boolean,
    isSubmitting: Boolean,
    isModerating: Boolean,
    errorMessage: String?,
    moderationMessage: String?,
    isSignedIn: Boolean,
    currentUserId: Long?,
    onRetry: () -> Unit,
    onCreateReview: (String) -> Unit,
    onReportReview: (Int, String, String?) -> Unit,
    onBlockUser: (Long) -> Unit,
    onOpenExternalUri: (String) -> Unit,
) {
    var reviewText by remember(selectedZone?.id) { mutableStateOf("") }
    var reportTarget by remember(selectedZone?.id) { mutableStateOf<ZoneReviewDto?>(null) }
    var blockTarget by remember(selectedZone?.id) { mutableStateOf<ZoneReviewDto?>(null) }
    var reportReason by remember(reportTarget?.id) { mutableStateOf(ReviewReportReason.Offensive) }
    var reportDetails by remember(reportTarget?.id) { mutableStateOf("") }
    var policyAccepted by remember(selectedZone?.id) { mutableStateOf(false) }
    val normalizedReview = reviewText.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTitle("리뷰")
        Text(selectedZone?.title ?: "구역을 선택하면 리뷰를 볼 수 있습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            selectedZone == null -> Text("지도 마커에서 구역을 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            isLoading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("리뷰 불러오는 중", style = MaterialTheme.typography.bodySmall)
            }
            !errorMessage.isNullOrBlank() -> {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRetry) { Text("다시 시도") }
            }
            reviews.isEmpty() -> Text("아직 등록된 리뷰가 없습니다. 첫 리뷰를 남겨보세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> reviews.forEach { review ->
                ReviewRow(
                    review = review,
                    canReport = canReportReview(review.authorId, currentUserId),
                    canBlock = canBlockReviewAuthor(review.authorId, currentUserId),
                    isSignedIn = isSignedIn,
                    isModerating = isModerating,
                    onReport = { reportTarget = review },
                    onBlock = { blockTarget = review },
                )
            }
        }
        if (!moderationMessage.isNullOrBlank()) {
            Text(
                moderationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = reviewText,
            onValueChange = { reviewText = it.take(REVIEW_MAX_LENGTH) },
            label = { Text(if (isSignedIn) "리뷰를 남겨주세요." else "로그인이 필요합니다") },
            enabled = isSignedIn && selectedZone != null && !isSubmitting,
            minLines = 2,
            maxLines = 4,
            supportingText = { Text("${normalizedReview.length}/$REVIEW_MAX_LENGTH") },
            modifier = Modifier.fillMaxWidth(),
        )
        UgcPolicyAcceptance(
            accepted = policyAccepted,
            onAcceptedChange = { policyAccepted = it },
            onOpenPolicy = { onOpenExternalUri(TERMS_OF_USE_URL) },
        )
        Button(
            onClick = {
                onCreateReview(normalizedReview)
                reviewText = ""
            },
            enabled = canSubmitUgc(
                isSignedIn = isSignedIn,
                hasTarget = selectedZone != null,
                content = normalizedReview,
                policyAccepted = policyAccepted,
                isBusy = isSubmitting || isModerating,
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(if (isSubmitting) "등록 중" else "리뷰 등록")
        }
    }

    reportTarget?.let { review ->
        AlertDialog(
            onDismissRequest = { if (!isModerating) reportTarget = null },
            title = { Text("리뷰 · 사용자 신고") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "선택한 리뷰와 작성자가 함께 검토됩니다. 신고 사유를 선택해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ReviewReportReason.entries.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = reportReason == reason,
                                    onClick = { reportReason = reason },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = reportReason == reason,
                                onClick = null,
                            )
                            Text(reason.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (reportReason == ReviewReportReason.Other) {
                        OutlinedTextField(
                            value = reportDetails,
                            onValueChange = { reportDetails = it.take(REPORT_DETAILS_MAX_LENGTH) },
                            label = { Text("상세 사유") },
                            minLines = 2,
                            maxLines = 3,
                            supportingText = { Text("${reportDetails.trim().length}/$REPORT_DETAILS_MAX_LENGTH") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReportReview(
                            review.id,
                            reportReason.apiValue,
                            reportDetails.trim().takeIf { it.isNotBlank() },
                        )
                        reportTarget = null
                    },
                    enabled = !isModerating && (
                        reportReason != ReviewReportReason.Other || reportDetails.trim().isNotBlank()
                    ),
                ) {
                    Text("신고 접수")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }, enabled = !isModerating) {
                    Text("취소")
                }
            },
        )
    }

    blockTarget?.let { review ->
        AlertDialog(
            onDismissRequest = { if (!isModerating) blockTarget = null },
            title = { Text("사용자 차단") },
            text = { Text("${review.displayAuthor}님의 리뷰를 앞으로 표시하지 않습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        review.authorId?.let(onBlockUser)
                        blockTarget = null
                    },
                    enabled = !isModerating,
                ) {
                    Text("차단")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }, enabled = !isModerating) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun ReviewRow(
    review: ZoneReviewDto,
    canReport: Boolean,
    canBlock: Boolean,
    isSignedIn: Boolean,
    isModerating: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF7F7F2))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                review.displayAuthor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (canReport) {
                TextButton(
                    onClick = onReport,
                    enabled = isSignedIn && !isModerating,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) {
                    Text("리뷰 신고", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (canBlock) {
                TextButton(
                    onClick = onBlock,
                    enabled = isSignedIn && !isModerating,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) {
                    Text("사용자 차단", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(review.content, style = MaterialTheme.typography.bodyMedium)
        if (!review.createdAt.isNullOrBlank()) Text(review.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun canReportReview(authorId: Long?, currentUserId: Long?): Boolean =
    authorId == null || authorId != currentUserId

internal fun canBlockReviewAuthor(authorId: Long?, currentUserId: Long?): Boolean =
    authorId != null && authorId != currentUserId

@Composable
private fun UgcPolicyAcceptance(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onOpenPolicy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF7F7F2),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAcceptedChange,
                modifier = Modifier.semantics {
                    contentDescription = "이용약관과 커뮤니티 운영정책 동의"
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "이용약관과 커뮤니티 운영정책에 동의합니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = onOpenPolicy,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Text("내용 보기", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun canSubmitUgc(
    isSignedIn: Boolean,
    hasTarget: Boolean,
    content: String,
    policyAccepted: Boolean,
    isBusy: Boolean,
): Boolean = isSignedIn &&
    hasTarget &&
    content.trim().isNotEmpty() &&
    policyAccepted &&
    !isBusy

internal fun canSubmitZoneUgc(
    policyAccepted: Boolean,
    isBusy: Boolean,
): Boolean = policyAccepted && !isBusy

internal enum class ZoneReportReason(val apiValue: String, val label: String) {
    Inaccurate("INACCURATE", "위치 또는 정보가 정확하지 않음"),
    Duplicate("DUPLICATE", "같은 장소가 중복 등록됨"),
    Closed("CLOSED", "폐쇄되었거나 더 이상 이용할 수 없음"),
    Offensive("OFFENSIVE", "부적절하거나 불쾌한 내용"),
    PersonalInformation("PERSONAL_INFORMATION", "개인정보가 포함됨"),
    Other("OTHER", "기타"),
}

internal enum class ReviewReportReason(val apiValue: String, val label: String) {
    Offensive("OFFENSIVE", "불쾌하거나 공격적인 내용"),
    Harassment("HARASSMENT", "괴롭힘 또는 혐오 표현"),
    Spam("SPAM", "스팸 또는 홍보"),
    PersonalInformation("PERSONAL_INFORMATION", "개인정보 노출"),
    Other("OTHER", "기타"),
}

private const val SEASON3_AUTO_REFRESH_MILLIS = 3 * 60 * 1000L
private const val RECOMMENDATION_VIEWPORT_DEBOUNCE_MILLIS = 300L
private const val PERO_DICE_TRAIL_COUNT = 4
private const val EXTERNAL_URI_FAILURE_MESSAGE = "연결할 앱을 찾지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val REVIEW_MAX_LENGTH = 500
private const val REPORT_DETAILS_MAX_LENGTH = 500
internal const val ZONE_REPORT_ACCESSIBILITY_LABEL = "선택한 흡연구역 신고"
internal const val SEARCH_FIELD_ACCESSIBILITY_LABEL = "흡연구역 주소 검색어"
internal const val SEARCH_ACTION_ACCESSIBILITY_LABEL = "검색 실행"
internal const val CURRENT_LOCATION_ACCESSIBILITY_LABEL = "현재 위치로 지도 이동"
internal const val MINIMUM_TOUCH_TARGET_DP = 48
internal const val SEARCH_FIELD_VISUAL_HEIGHT_DP = 48
internal const val FILTER_CHIP_VISUAL_HEIGHT_DP = 48
private const val LARGE_TEXT_FONT_SCALE_THRESHOLD = 1.5f
private const val DEFAULT_STATUS_TOAST_MAX_LINES = 2
private const val LARGE_TEXT_STATUS_TOAST_MAX_LINES = 4

internal fun statusToastMaxLines(fontScale: Float): Int =
    if (fontScale >= LARGE_TEXT_FONT_SCALE_THRESHOLD) {
        LARGE_TEXT_STATUS_TOAST_MAX_LINES
    } else {
        DEFAULT_STATUS_TOAST_MAX_LINES
    }

private const val PRIVACY_POLICY_URL = "https://nugulmap.com/privacy"
private const val ACCOUNT_DELETION_URL = "https://nugulmap.com/account-deletion"
private const val SUPPORT_URL = "https://nugulmap.com/support"
private const val TERMS_OF_USE_URL = "https://nugulmap.com/terms"
internal const val LOGIN_CONNECTIONS_URL = "https://nugulmap.com/connected-accounts"
