package com.nugulmap.nativeapp.ui.map

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun MapScreen(
    oauthCallbackUri: Uri? = null,
    viewModel: MapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val selectedZone = uiState.zones.firstOrNull { it.id == uiState.selectedZoneId }

    LaunchedEffect(oauthCallbackUri) {
        viewModel.handleOAuthCallback(oauthCallbackUri)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MapHeader(
                    isSignedIn = uiState.isSignedIn,
                    isAuthLoading = uiState.isAuthLoading,
                    authMessage = uiState.authMessage,
                    actionMessage = uiState.actionMessage,
                    onRefresh = viewModel::refreshZones,
                    onLogin = { provider -> uriHandler.openUri(viewModel.authorizationUrl(provider)) },
                    onLogout = viewModel::logout,
                    onSyncAccount = viewModel::refreshProfileAndMyZones,
                )
            }
            item {
                KakaoZoneMap(
                    zones = uiState.zones,
                    selectedZoneId = uiState.selectedZoneId,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onZoneSelected = viewModel::selectZone,
                )
            }
            item {
                ZoneDetailPanel(
                    selectedZone = selectedZone,
                    reviewCount = uiState.selectedZoneReviews.size,
                    onClose = viewModel::dismissZoneDetail,
                )
            }
            item {
                AccountPanel(
                    isSignedIn = uiState.isSignedIn,
                    userName = uiState.currentUser?.displayName,
                    userEmail = uiState.currentUser?.email,
                    myZones = uiState.myZones,
                    isActionLoading = uiState.isActionLoading,
                    onCompleteProfile = viewModel::completeProfile,
                    onCreateZone = viewModel::createZone,
                    onSelectZone = viewModel::selectZone,
                    onRefreshProfileAndZones = viewModel::refreshProfileAndMyZones,
                )
            }
            item {
                ReviewPanel(
                    selectedZone = selectedZone,
                    reviews = uiState.selectedZoneReviews,
                    isLoading = uiState.isReviewLoading,
                    isSubmitting = uiState.isReviewSubmitting,
                    errorMessage = uiState.reviewErrorMessage,
                    isSignedIn = uiState.isSignedIn,
                    onRetry = { uiState.selectedZoneId?.let(viewModel::selectZone) },
                    onCreateReview = viewModel::createReview,
                )
            }
            items(uiState.zones, key = { it.id }) { zone ->
                ZoneCard(
                    zone = zone,
                    selected = zone.id == uiState.selectedZoneId,
                    onClick = { viewModel.selectZone(zone.id) },
                )
            }
        }
    }
}

@Composable
private fun MapHeader(
    isSignedIn: Boolean,
    isAuthLoading: Boolean,
    authMessage: String?,
    actionMessage: String?,
    onRefresh: () -> Unit,
    onLogin: (OAuthProvider) -> Unit,
    onLogout: () -> Unit,
    onSyncAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("너굴맵", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Android 네이티브 지도·상세·리뷰 MVP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onRefresh) { Text("새로고침") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isSignedIn) {
                OutlinedButton(onClick = onSyncAccount, enabled = !isAuthLoading) { Text("내 정보") }
                OutlinedButton(onClick = onLogout, enabled = !isAuthLoading) { Text("로그아웃") }
            } else {
                OAuthProvider.entries.forEach { provider ->
                    OutlinedButton(onClick = { onLogin(provider) }, enabled = !isAuthLoading) {
                        Text("${provider.displayName} 로그인")
                    }
                }
            }
        }
        if (!authMessage.isNullOrBlank()) {
            Text(authMessage, style = MaterialTheme.typography.bodySmall, color = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!actionMessage.isNullOrBlank()) {
            Text(actionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ZoneDetailPanel(
    selectedZone: ZoneDto?,
    reviewCount: Int,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("구역 상세", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (selectedZone != null) {
                    OutlinedButton(onClick = onClose) { Text("닫기") }
                }
            }
            if (selectedZone == null) {
                Text("지도 마커 또는 목록에서 구역을 선택해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(selectedZone.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selectedZone.summary.isNotBlank()) {
                Text(selectedZone.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("주소: ${selectedZone.address ?: "주소 정보 없음"}", style = MaterialTheme.typography.bodyMedium)
            Text("좌표: ${selectedZone.latitude}, ${selectedZone.longitude}", style = MaterialTheme.typography.bodySmall)
            if (!selectedZone.description.isNullOrBlank()) {
                Text(selectedZone.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (!selectedZone.size.isNullOrBlank()) {
                Text("규모: ${selectedZone.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("리뷰 ${reviewCount}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountPanel(
    isSignedIn: Boolean,
    userName: String?,
    userEmail: String?,
    myZones: List<ZoneDto>,
    isActionLoading: Boolean,
    onCompleteProfile: (String) -> Unit,
    onCreateZone: (ZoneCreatePayload) -> Unit,
    onSelectZone: (Int) -> Unit,
    onRefreshProfileAndZones: () -> Unit,
) {
    var nickname by remember(userName) { mutableStateOf(userName.orEmpty()) }
    var address by remember { mutableStateOf("서울특별시 중구 세종대로 110") }
    var description by remember { mutableStateOf("Android 앱에서 등록한 흡연구역") }
    var latitude by remember { mutableStateOf("37.5665") }
    var longitude by remember { mutableStateOf("126.9780") }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("프로필 / 내 구역", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(if (isSignedIn) userName ?: "프로필 설정 필요" else "로그인 후 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!userEmail.isNullOrBlank()) Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onRefreshProfileAndZones, enabled = isSignedIn && !isActionLoading) { Text("동기화") }
            }
            OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("닉네임") }, enabled = isSignedIn && !isActionLoading, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onCompleteProfile(nickname) }, enabled = isSignedIn && !isActionLoading && nickname.trim().length in 2..20) {
                Text(if (isActionLoading) "처리 중" else "프로필 저장")
            }

            HorizontalDivider()
            Text("내가 등록한 구역 ${myZones.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            if (!isSignedIn) {
                Text("로그인하면 /api/zones/my 결과가 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (myZones.isEmpty()) {
                Text("아직 등록한 구역이 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                myZones.take(3).forEach { zone -> MyZoneRow(zone = zone, onClick = { onSelectZone(zone.id) }) }
                if (myZones.size > 3) Text("외 ${myZones.size - 3}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Text("구역 등록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("구역 주소") }, enabled = isSignedIn && !isActionLoading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("설명") }, enabled = isSignedIn && !isActionLoading, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("위도") }, enabled = isSignedIn && !isActionLoading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("경도") }, enabled = isSignedIn && !isActionLoading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
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
                enabled = isSignedIn && !isActionLoading,
            ) { Text(if (isActionLoading) "처리 중" else "구역 등록") }
        }
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

@Composable
private fun ReviewPanel(
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("리뷰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("${reviews.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text(selectedZone?.title ?: "구역을 선택하면 리뷰를 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                selectedZone == null -> Text("지도 마커 또는 목록에서 구역을 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                isLoading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text("리뷰 불러오는 중", style = MaterialTheme.typography.bodySmall)
                }
                !errorMessage.isNullOrBlank() -> {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                }
                reviews.isEmpty() -> Text("아직 등록된 리뷰가 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> reviews.take(3).forEach { review -> ReviewRow(review) }
            }
            OutlinedTextField(value = reviewText, onValueChange = { reviewText = it }, label = { Text("리뷰 내용") }, enabled = isSignedIn && selectedZone != null && !isSubmitting, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onCreateReview(normalizedReview)
                    reviewText = ""
                },
                enabled = isSignedIn && selectedZone != null && normalizedReview.isNotBlank() && !isSubmitting,
            ) { Text(if (isSubmitting) "등록 중" else "리뷰 등록") }
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

@Composable
private fun ZoneCard(zone: ZoneDto, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE7F0E8) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(zone.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (zone.summary.isNotBlank()) Text(zone.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("위도 ${zone.latitude}, 경도 ${zone.longitude}", style = MaterialTheme.typography.bodySmall)
            if (!zone.description.isNullOrBlank()) Text(zone.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
