package com.nugulmap.nativeapp.ui.map

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.InsightStatusPayload
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    oauthCallbackUri: Uri? = null,
    viewModel: MapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val selectedZone = uiState.zones.firstOrNull { it.id == uiState.selectedZoneId }
    var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
    var selectedInsight by remember { mutableStateOf<InsightSelection?>(null) }
    var layerMode by remember { mutableStateOf(Season2LayerMode.All) }
    var pendingShortcut by remember { mutableStateOf<Season2Shortcut?>(null) }
    var visibleBounds by remember { mutableStateOf(MapBounds.centralSeoul) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val visibleZones = if (layerMode.showsZones) uiState.zones else emptyList()
    val visibleHotplaces = if (layerMode.showsHotplaces) uiState.hotplaceInsight.places else emptyList()
    val visibleEvents = if (layerMode.showsEvents) uiState.eventInsight.events else emptyList()
    val visibleSelectedZone = if (layerMode.showsZones) selectedZone else null
    val visibleSelectedZoneId = if (layerMode.showsZones) uiState.selectedZoneId else null

    LaunchedEffect(oauthCallbackUri) {
        viewModel.handleOAuthCallback(oauthCallbackUri)
    }

    LaunchedEffect(layerMode) {
        if (!layerMode.showsZones && uiState.selectedZoneId != null) {
            viewModel.dismissZoneDetail()
        }
        if (selectedInsight?.let { layerMode.showsInsight(it.type) } == false) {
            selectedInsight = null
        }
    }

    LaunchedEffect(pendingShortcut, uiState.hotplaceInsight, uiState.eventInsight) {
        when (pendingShortcut?.kind) {
            Season2ShortcutKind.Hotplace -> uiState.hotplaceInsight.places.firstOrNull()?.let { place ->
                selectedInsight = place.toInsightSelection()
                pendingShortcut = null
            }
            Season2ShortcutKind.Event -> uiState.eventInsight.events.firstOrNull()?.let { event ->
                selectedInsight = event.toInsightSelection()
                pendingShortcut = null
            }
            null -> Unit
        }
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
                    zones = visibleZones,
                    hotplaces = visibleHotplaces,
                    events = visibleEvents,
                    selectedZoneId = visibleSelectedZoneId,
                    onZoneSelected = { zoneId ->
                        selectedInsight = null
                        viewModel.selectZone(zoneId)
                    },
                    onHotplaceSelected = { hotplaceId ->
                        uiState.hotplaceInsight.places.firstOrNull { it.id == hotplaceId }?.let { place ->
                            selectedInsight = place.toInsightSelection()
                            viewModel.dismissZoneDetail()
                        }
                    },
                    onEventSelected = { eventId ->
                        uiState.eventInsight.events.firstOrNull { it.id == eventId }?.let { event ->
                            selectedInsight = event.toInsightSelection()
                            viewModel.dismissZoneDetail()
                        }
                    },
                    onVisibleBoundsChanged = { bounds ->
                        visibleBounds = bounds
                        viewModel.refreshMapData(bounds)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                TopMapChrome(
                    isLoading = uiState.isLoading,
                    onMenu = { scope.launch { drawerState.open() } },
                    onRefresh = {
                        viewModel.refreshMapData(visibleBounds, force = true)
                    },
                )

                Season2QuickActionStrip(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 76.dp, start = 16.dp, end = 16.dp),
                    onShortcutSelected = { shortcut ->
                        layerMode = shortcut.targetLayerMode
                        viewModel.dismissZoneDetail()
                        selectedInsight = null
                        pendingShortcut = shortcut
                        viewModel.runSeason2Shortcut(shortcut)
                    },
                )

                Season2LayerControl(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 120.dp, start = 16.dp, end = 16.dp),
                    activeMode = layerMode,
                    onModeSelected = { nextMode ->
                        layerMode = nextMode
                        if (!nextMode.showsZones) {
                            viewModel.dismissZoneDetail()
                        }
                        if (selectedInsight?.let { nextMode.showsInsight(it.type) } == false) {
                            selectedInsight = null
                        }
                    },
                )

                Season2InsightPanel(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 172.dp, start = 16.dp, end = 16.dp),
                    hotplaces = visibleHotplaces,
                    events = visibleEvents,
                    hotplaceFreshness = uiState.hotplaceInsight.dataFreshness,
                    eventFreshness = uiState.eventInsight.dataFreshness,
                    insightStatus = uiState.insightStatus,
                    isLoading = uiState.isInsightLoading,
                )

                BottomMapChrome(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    selectedZone = visibleSelectedZone,
                    reviewCount = uiState.selectedZoneReviews.size,
                    statusMessage = sanitizeStatusMessage(uiState.authMessage, uiState.actionMessage),
                    onCloseZone = viewModel::dismissZoneDetail,
                    onReviews = { activeSheet = HomeSheet.Reviews },
                    onReport = { activeSheet = if (uiState.isSignedIn) HomeSheet.Report else HomeSheet.Account },
                )

                selectedInsight?.let { insight ->
                    Season2InsightDetailCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 148.dp),
                        insight = insight,
                        onClose = { selectedInsight = null },
                        onRoute = {
                            uriHandler.openUri(
                                "geo:${insight.latitude},${insight.longitude}?q=${insight.latitude},${insight.longitude}(${Uri.encode(insight.title)})",
                            )
                        },
                    )
                }

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
                    onLogin = { provider -> uriHandler.openUri(viewModel.authorizationUrl(provider)) },
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
                    userEmail = uiState.currentUser?.email,
                    isActionLoading = uiState.isActionLoading,
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
                    errorMessage = uiState.reviewErrorMessage,
                    isSignedIn = uiState.isSignedIn,
                    onRetry = { uiState.selectedZoneId?.let(viewModel::selectZone) },
                    onCreateReview = viewModel::createReview,
                )
                HomeSheet.Settings -> SettingsSheet()
                null -> Unit
            }
        }
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
) {
    All("전체", showsZones = true, showsHotplaces = true, showsEvents = true),
    Zones("흡연", showsZones = true, showsHotplaces = false, showsEvents = false),
    Hotplaces("핫플", showsZones = false, showsHotplaces = true, showsEvents = false),
    Events("행사", showsZones = false, showsHotplaces = false, showsEvents = true);

    fun showsInsight(type: InsightSelectionType): Boolean =
        when (type) {
            InsightSelectionType.Hotplace -> showsHotplaces
            InsightSelectionType.Event -> showsEvents
        }
}

enum class Season2ShortcutKind {
    Hotplace,
    Event,
}

enum class Season2Shortcut(
    val title: String,
    val keyword: String,
    val kind: Season2ShortcutKind,
) {
    LotteWorldCrowd("롯데월드 혼잡도", "롯데월드", Season2ShortcutKind.Hotplace),
    HotNow("지금 핫한 곳", "hot-now", Season2ShortcutKind.Hotplace),
    SeongsuPopup("성수 팝업", "성수", Season2ShortcutKind.Event);

    val targetLayerMode: Season2LayerMode
        get() = when (kind) {
            Season2ShortcutKind.Hotplace -> Season2LayerMode.Hotplaces
            Season2ShortcutKind.Event -> Season2LayerMode.Events
        }
}

enum class InsightSelectionType {
    Hotplace,
    Event,
}

private data class InsightSelection(
    val type: InsightSelectionType,
    val title: String,
    val label: String,
    val detail: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val source: String,
)

@Composable
private fun Season2QuickActionStrip(
    modifier: Modifier = Modifier,
    onShortcutSelected: (Season2Shortcut) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Season2Shortcut.entries.forEach { shortcut ->
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clickable { onShortcutSelected(shortcut) },
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (shortcut.kind == Season2ShortcutKind.Event) "▣" else "▲",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (shortcut.kind == Season2ShortcutKind.Event) Color(0xFF2E6D92) else Color(0xFFD4551B),
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = shortcut.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Season2LayerControl(
    modifier: Modifier = Modifier,
    activeMode: Season2LayerMode,
    onModeSelected: (Season2LayerMode) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Season2LayerMode.entries.forEach { mode ->
                val selected = mode == activeMode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clickable { onModeSelected(mode) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMapChrome(
    isLoading: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(58.dp),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onMenu),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.74f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("☰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("⌕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isLoading) "흡연구역 불러오는 중" else "장소, 주소 검색",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier.clickable(onClick = onRefresh),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    if (isLoading) "로딩" else "검색",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                )
            }
        }
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
private fun Season2InsightPanel(
    modifier: Modifier = Modifier,
    hotplaces: List<HotplaceDto>,
    events: List<TrendEventDto>,
    hotplaceFreshness: String?,
    eventFreshness: String?,
    insightStatus: InsightStatusPayload?,
    isLoading: Boolean,
) {
    val visibleHotplaces = hotplaces.take(5)
    val visibleEvents = events.take(5)
    if (visibleHotplaces.isEmpty() && visibleEvents.isEmpty() && !isLoading) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = formatInsightStatus(insightStatus),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InsightSectionHeader(
                title = "지금 핫한 곳",
                status = formatHotplacePanelStatus(hotplaces, hotplaceFreshness, isLoading),
            )
            if (visibleHotplaces.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleHotplaces.forEach { place ->
                        InsightChip(
                            title = place.name,
                            label = place.crowdLabel,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        )
                    }
                }
            }

            if (visibleEvents.isNotEmpty()) {
                InsightSectionHeader(
                    title = "팝업·행사·축제",
                    status = formatEventPanelStatus(eventFreshness),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleEvents.forEach { event ->
                        InsightChip(
                            title = event.title,
                            label = event.eventLabel,
                            color = Color(0xFFF7F7F2),
                        )
                    }
                }
            }
        }
    }
}

private fun formatInsightStatus(status: InsightStatusPayload?): String {
    status ?: return "데이터 상태 확인 중"
    val hotplaceStatus = formatTelecomStatus(status)
        ?: formatProviderMode(status.hotplaceMode, "핫플")
    val eventStatus = formatEventStatus(status)
    return "$hotplaceStatus · $eventStatus"
}

private fun formatTelecomStatus(status: InsightStatusPayload): String? {
    return when {
        status.telecomCrowdKeyConfigured && !status.telecomCrowdUrlTemplateConfigured -> "통신사 URL 필요"
        status.telecomCrowd?.configured == true -> "통신사 연결"
        status.telecomCrowdKeyConfigured -> "통신사 설정 확인"
        else -> null
    }
}

private fun formatEventStatus(status: InsightStatusPayload): String {
    val popupStatus = status.popupTrends
    return when {
        status.eventMode == "CRAWLED_OR_PARTIAL" -> "크롤링 팝업 트렌드"
        status.ktoTourApi?.qualityStatus == "OK" -> "TourAPI 연결"
        status.seoulCultureApi?.qualityStatus == "OK" -> "서울문화 API 연결"
        status.ktoTourApiKeyConfigured -> "TourAPI 설정 확인"
        status.seoulCultureApiKeyConfigured -> "서울문화 API 확인"
        popupStatus?.fileExists == true && popupStatus.recordCount > 0 -> "크롤링 팝업 트렌드"
        else -> formatProviderMode(status.eventMode, "행사")
    }
}

private fun formatProviderMode(mode: String?, label: String): String {
    return when (mode) {
        "LIVE_OR_PARTIAL" -> "$label 실시간"
        "CRAWLED_OR_PARTIAL" -> "$label 크롤링"
        "STATIC_FALLBACK" -> "$label 후보"
        else -> "$label 확인 중"
    }
}

private fun formatHotplacePanelStatus(
    hotplaces: List<HotplaceDto>,
    hotplaceFreshness: String?,
    isLoading: Boolean,
): String {
    return when {
        isLoading -> "갱신 중"
        hotplaces.any { it.source == "TELECOM_CROWD" } -> "통신사"
        hotplaceFreshness == "LIVE_OR_PARTIAL" -> "실시간"
        else -> "후보"
    }
}

private fun formatEventPanelStatus(eventFreshness: String?): String {
    return when (eventFreshness) {
        "LIVE_OR_PARTIAL" -> "API"
        "CRAWLED_OR_PARTIAL" -> "크롤링"
        else -> "후보"
    }
}

@Composable
private fun Season2InsightDetailCard(
    modifier: Modifier = Modifier,
    insight: InsightSelection,
    onClose: () -> Unit,
    onRoute: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    insight.source,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = onRoute, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) {
                    Text("길찾기")
                }
            }
        }
    }
}

private fun HotplaceDto.toInsightSelection(): InsightSelection {
    val details = listOfNotNull(
        crowdMessage
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == "실시간 혼잡도 키가 없거나 해당 장소 응답을 받을 수 없어 후보 장소만 표시합니다." },
        updatedAt?.takeIf { it.isNotBlank() }?.let { "갱신 $it" },
        sourcePlaceCode?.takeIf { it.isNotBlank() }?.let { "장소 $it" },
    )
    return InsightSelection(
        type = InsightSelectionType.Hotplace,
        title = name,
        label = crowdLabel,
        detail = details.joinToString(" · ").ifBlank { null },
        address = address,
        latitude = latitude,
        longitude = longitude,
        source = when (source) {
            "TELECOM_CROWD" -> "통신사 장소 혼잡도"
            "SEOUL_CITYDATA" -> "서울 실시간 도시데이터"
            else -> "핫플 후보"
        },
    )
}

private fun TrendEventDto.toInsightSelection(): InsightSelection = InsightSelection(
    type = InsightSelectionType.Event,
    title = title,
    label = eventLabel,
    detail = sourceContentId?.takeIf { it.isNotBlank() }?.let { "출처 ID $it" },
    address = address,
    latitude = latitude,
    longitude = longitude,
    source = when (source) {
        "KTO_TOUR_API" -> "한국관광공사 TourAPI"
        "SEOUL_CULTURE_API" -> "서울 문화행사 API"
        "CRAWLED_POPUP_TREND" -> "크롤링 팝업 트렌드"
        else -> "이벤트 후보"
    },
)

@Composable
private fun InsightSectionHeader(
    title: String,
    status: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InsightChip(
    title: String,
    label: String,
    color: Color,
) {
    Surface(
        modifier = Modifier.width(146.dp),
        shape = RoundedCornerShape(16.dp),
        color = color,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BottomMapChrome(
    modifier: Modifier = Modifier,
    selectedZone: ZoneDto?,
    reviewCount: Int,
    statusMessage: String?,
    onCloseZone: () -> Unit,
    onReviews: () -> Unit,
    onReport: () -> Unit,
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
                onClose = onCloseZone,
                onReviews = onReviews,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.24f),
                shadowElevation = 12.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⌖", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }

            Button(
                onClick = onReport,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("+  제보하기", fontWeight = FontWeight.Black)
            }
        }
    }
}


@Composable
private fun StatusToast(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 10.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
) {
    Surface(
        modifier = modifier,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("리뷰 ${reviewCount}개", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onReviews, shape = RoundedCornerShape(14.dp)) { Text("리뷰 보기") }
            }
        }
    }
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
private fun SettingsSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTitle("설정")
        Text("너굴맵", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
            text = { Text("계정과 로그인 토큰이 삭제됩니다. 일부 제보/리뷰 기록은 서비스 운영 정책에 따라 보존될 수 있습니다.") },
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
    userEmail: String?,
    isActionLoading: Boolean,
    onCreateZone: (ZoneCreatePayload) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }

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
                            user = userName ?: userEmail ?: "Android",
                        ),
                    )
                }
            },
            enabled = !isActionLoading,
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
    firstStatusMessage(authMessage, actionMessage)
        ?.toUserFacingStatus("지도를 표시하지 못했습니다.")

@Composable
private fun ReviewSheet(
    selectedZone: ZoneDto?,
    reviews: List<ZoneReviewDto>,
    isLoading: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    isSignedIn: Boolean,
    onRetry: () -> Unit,
    onCreateReview: (String) -> Unit,
) {
    var reviewText by remember(selectedZone?.id) { mutableStateOf("") }
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
            else -> reviews.forEach { review -> ReviewRow(review) }
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
        Button(
            onClick = {
                onCreateReview(normalizedReview)
                reviewText = ""
            },
            enabled = isSignedIn && selectedZone != null && normalizedReview.isNotBlank() && !isSubmitting,
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
}

@Composable
private fun ReviewRow(review: ZoneReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF7F7F2))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(review.displayAuthor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(review.content, style = MaterialTheme.typography.bodyMedium)
        if (!review.createdAt.isNullOrBlank()) Text(review.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val REVIEW_MAX_LENGTH = 500
