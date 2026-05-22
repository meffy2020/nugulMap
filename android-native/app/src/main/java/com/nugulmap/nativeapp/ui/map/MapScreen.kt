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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(oauthCallbackUri) {
        viewModel.handleOAuthCallback(oauthCallbackUri)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            KakaoZoneMap(
                zones = uiState.zones,
                selectedZoneId = uiState.selectedZoneId,
                onZoneSelected = viewModel::selectZone,
                modifier = Modifier.fillMaxSize(),
            )

            TopMapChrome(
                isLoading = uiState.isLoading,
                isSignedIn = uiState.isSignedIn,
                onRefresh = viewModel::refreshZones,
                onAccount = { activeSheet = HomeSheet.Account },
            )

            BottomMapChrome(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedZone = selectedZone,
                reviewCount = uiState.selectedZoneReviews.size,
                statusMessage = sanitizeStatusMessage(uiState.authMessage, uiState.actionMessage),
                onCloseZone = viewModel::dismissZoneDetail,
                onReviews = { activeSheet = HomeSheet.Reviews },
                onReport = { activeSheet = if (uiState.isSignedIn) HomeSheet.Report else HomeSheet.Account },
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
                null -> Unit
            }
        }
    }
}

private enum class HomeSheet {
    Account,
    Report,
    Reviews,
}

@Composable
private fun TopMapChrome(
    isLoading: Boolean,
    isSignedIn: Boolean,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clickable(onClick = onRefresh),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Text(
                        "검색",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(52.dp)
                .clickable(onClick = onAccount),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (isSignedIn) "내" else "👤", fontWeight = FontWeight.Black)
            }
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
    onCompleteProfile: (String) -> Unit,
    onSyncAccount: () -> Unit,
    onSelectZone: (Int) -> Unit,
) {
    var nickname by remember(userName) { mutableStateOf(userName.orEmpty()) }

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
            if (isSignedIn) userName ?: "프로필 설정 필요" else "로그인 후 리뷰와 제보 기능을 사용할 수 있어요.",
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
    var address by remember { mutableStateOf("서울특별시 중구 세종대로 110") }
    var description by remember { mutableStateOf("Android 앱에서 등록한 흡연구역") }
    var latitude by remember { mutableStateOf("37.5665") }
    var longitude by remember { mutableStateOf("126.9780") }
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
        Text("주소와 좌표를 확인해 새 구역을 등록합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("${zone.latitude}, ${zone.longitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sanitizeStatusMessage(authMessage: String?, actionMessage: String?): String? =
    firstStatusMessage(authMessage, actionMessage)
        ?.toUserFacingStatus("일시적으로 지도 데이터 로드에 실패했습니다. 잠시 후 다시 시도해 주세요.")

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
