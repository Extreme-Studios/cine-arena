package com.extremestudios.cinearena.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.extremestudios.cinearena.data.local.PortalSessionStore
import com.extremestudios.cinearena.data.model.PortalPost
import com.extremestudios.cinearena.data.model.PortalUserSession
import com.extremestudios.cinearena.data.repository.PortalRepository
import com.extremestudios.cinearena.ui.PORTAL_POSTER_REWARD_COOLDOWN_KEY
import com.extremestudios.cinearena.ui.PORTAL_REWARD_COOLDOWN_MS
import com.extremestudios.cinearena.ui.PORTAL_REWARD_PREFERENCE_NAME
import com.extremestudios.cinearena.ui.REWARD_AD_UNIT_ID
import com.extremestudios.cinearena.ui.SelectedImagePayload
import com.extremestudios.cinearena.ui.readSelectedImage
import com.extremestudios.cinearena.ui.rememberRewardedSubmissionGate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

private const val REPORT_COOLDOWN_MS = 5 * 60 * 1000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalKitaScreen(
    posts: List<PortalPost>,
    categories: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onPostPreview: (PortalPost) -> Unit,
    onOpenPosterSubmission: () -> Unit
) {
    val context = LocalContext.current
    val sessionStore = remember { PortalSessionStore(context) }
    val repository = remember { PortalRepository() }
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    val pendingHubWhatsapp by sessionStore.pendingWhatsappFlow.collectAsState(initial = null)
    val lastHubWhatsapp by sessionStore.lastHubWhatsappFlow.collectAsState(initial = null)
    val canPostPoster = session?.status.equals("ACTIVE", ignoreCase = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var reportPost by remember { mutableStateOf<PortalPost?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var boardNotice by remember { mutableStateOf<UiNotice?>(null) }
    val reportCooldownUntilByPostId = remember { mutableStateMapOf<String, Long>() }

    var selectedCategory by remember { mutableStateOf("All") }
    val filteredPosts = remember(posts, selectedCategory) {
        posts.filter { selectedCategory == "All" || it.category == selectedCategory }
    }

    androidx.compose.runtime.LaunchedEffect(session?.userId, session?.status, canPostPoster) {
        Log.d("PortalKita", "hubSession status=${session?.status ?: "null"}")
        Log.d("PortalKita", "canPostPoster=$canPostPoster")
        Log.d("PortalKita", "Pasang Poster visible=$canPostPoster")
    }

    androidx.compose.runtime.LaunchedEffect(session?.whatsappNormalized, pendingHubWhatsapp, lastHubWhatsapp) {
        if (canPostPoster) return@LaunchedEffect
        val watchWhatsapp = session?.whatsappNormalized ?: pendingHubWhatsapp ?: lastHubWhatsapp
        if (watchWhatsapp.isNullOrBlank()) return@LaunchedEffect
        val result = repository.checkHubStatus(watchWhatsapp)
        result.onSuccess { account ->
            when {
                account.status.equals("ACTIVE", ignoreCase = true) -> {
                    sessionStore.saveSession(account)
                    sessionStore.clearPendingWhatsapp()
                    Log.d("PortalKita", "Recovered ACTIVE hub session from status check")
                }
                account.status.equals("PENDING", ignoreCase = true) -> {
                    sessionStore.clearSession()
                    sessionStore.savePendingWhatsapp(account.whatsappNormalized)
                }
                account.status.equals("BLOCKED", ignoreCase = true) -> {
                    sessionStore.clearSession()
                    sessionStore.clearPendingWhatsapp()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "POSTER BOARD",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Poster Board",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ruang poster kompetisi, screening, dan karya film pendek pelajar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canPostPoster) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onOpenPosterSubmission) {
                        Text("+ Pasang Poster")
                    }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                boardNotice?.let { notice ->
                    Spacer(Modifier.height(12.dp))
                    NoticeCard(
                        message = notice.message,
                        type = notice.type,
                        onDismiss = { boardNotice = null }
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Text(
                    text = "Memuat poster...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (errorMessage != null && filteredPosts.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRefresh) {
                        Text("Coba Lagi")
                    }
                }
            }
        } else if (filteredPosts.isEmpty()) {
            item {
                Text(
                    text = "Belum ada poster di kategori ini.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredPosts, key = { it.id }) { post ->
                PortalPostCard(
                    post = post,
                    onPrimaryClick = {
                        if (post.targetUrl.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(post.targetUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            onPostPreview(post)
                        }
                    },
                    onPreviewClick = { onPostPreview(post) },
                    onReportClick = {
                        val now = System.currentTimeMillis()
                        val cooldownUntil = reportCooldownUntilByPostId[post.id] ?: 0L
                        if (cooldownUntil > now) {
                            val remainingMs = cooldownUntil - now
                            val remainingMinutes = (remainingMs + 59_999L) / 60_000L
                            boardNotice = UiNotice(
                                "Poster ini baru dilaporkan. Coba lagi ${remainingMinutes} menit lagi.",
                                UiNoticeType.INFO
                            )
                            return@PortalPostCard
                        }
                        reportPost = post
                        reportReason = ""
                    }
                )
            }
        }
    }

    reportPost?.let { post ->
        AlertDialog(
            onDismissRequest = { reportPost = null },
            title = { Text("Laporkan Poster") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tulis alasan laporan untuk poster ini.")
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reportReason.trim().isBlank()) return@TextButton
                        scope.launch {
                            val result = repository.reportPoster(
                                post = post,
                                reason = reportReason.trim(),
                                reporterWhatsapp = session?.whatsappNormalized.orEmpty()
                            )
                            result.onSuccess {
                                reportCooldownUntilByPostId[post.id] = System.currentTimeMillis() + REPORT_COOLDOWN_MS
                                boardNotice = UiNotice("Laporan terkirim. Tim akan meninjau konten ini.", UiNoticeType.SUCCESS)
                                reportPost = null
                            }.onFailure { error ->
                                boardNotice = UiNotice(error.message ?: "Laporan gagal.", UiNoticeType.ERROR)
                            }
                        }
                    }
                ) { Text("Kirim") }
            },
            dismissButton = {
                TextButton(onClick = { reportPost = null }) { Text("Batal") }
            }
        )
    }

    SnackbarHost(hostState = snackbarHostState)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalPosterSubmissionScreen(
    modifier: Modifier = Modifier,
    repository: PortalRepository = remember { PortalRepository() },
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionStore = remember { PortalSessionStore(context) }
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    var resolvedSession by remember { mutableStateOf<PortalUserSession?>(null) }
    androidx.compose.runtime.LaunchedEffect(session?.userId, session?.status) {
        if (session?.status.equals("ACTIVE", ignoreCase = true)) {
            resolvedSession = session
        } else if (session == null) {
            // keep the last active session latched while submit flow is open
        } else {
            resolvedSession = null
        }
    }
    val canPostPoster = resolvedSession?.status.equals("ACTIVE", ignoreCase = true)
    val categories = remember { repository.getSubmissionCategories() }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedPosterImage by remember { mutableStateOf<SelectedImagePayload?>(null) }
    var agreementChecked by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitNotice by remember { mutableStateOf<UiNotice?>(null) }
    val posterRewardGate = rememberRewardedSubmissionGate(
        context = context,
        adUnitId = REWARD_AD_UNIT_ID,
        preferenceName = PORTAL_REWARD_PREFERENCE_NAME,
        cooldownKey = PORTAL_POSTER_REWARD_COOLDOWN_KEY,
        cooldownMs = PORTAL_REWARD_COOLDOWN_MS
    )
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            readSelectedImage(context, uri)
                .onSuccess {
                    selectedPosterImage = it
                    fieldErrors.remove("posterImage")
                }
                .onFailure { error ->
                    submitNotice = UiNotice(error.message ?: "Gagal memuat poster.", UiNoticeType.ERROR)
                }
        }
    }

    fun validate(): Boolean {
        fieldErrors.clear()
        if (selectedCategory.isBlank()) fieldErrors["category"] = "Kategori poster wajib dipilih"
        if (selectedPosterImage == null) fieldErrors["posterImage"] = "Poster wajib dipilih"
        if (!agreementChecked) fieldErrors["agreement"] = "Persetujuan wajib dicentang"
        return fieldErrors.isEmpty()
    }

    fun clearForm() {
        selectedCategory = ""
        selectedPosterImage = null
        agreementChecked = false
        fieldErrors.clear()
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("Kembali")
                }
            }
            item {
                Text(
                    text = "Pasang Poster",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            submitNotice?.let { notice ->
                item {
                    NoticeCard(
                        message = notice.message,
                        type = notice.type,
                        onDismiss = { submitNotice = null }
                    )
                }
            }
            if (!canPostPoster || resolvedSession == null) {
                item {
                    Text(
                        text = "Login Cine Arena dulu untuk memasang poster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    LoggedInPortalHeader(
                        session = resolvedSession ?: return@item
                    )
                }
                item {
                    Text(
                        text = "Kreator: ${resolvedSession?.bandName.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text(
                        text = "WhatsApp: ${resolvedSession?.whatsappNormalized.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pilih kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = category
                                        fieldErrors.remove("category")
                                    },
                                    label = { Text(category) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        fieldErrors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Poster",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pilih poster JPG atau PNG langsung dari galeri HP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(if (selectedPosterImage == null) "Pilih Poster" else "Ganti Poster")
                        }
                        selectedPosterImage?.let { poster ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Image(
                                    bitmap = poster.previewBitmap.asImageBitmap(),
                                    contentDescription = "Preview poster",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Text(
                                    text = poster.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        fieldErrors["posterImage"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Poster Board menerima poster film pendek pelajar, screening sekolah, dan kegiatan kompetisi Cine Arena. Konten di luar tema akan ditolak admin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        )
                    }
                }
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = agreementChecked,
                                onCheckedChange = {
                                    agreementChecked = it
                                    fieldErrors.remove("agreement")
                                }
                            )
                            Text(
                                "Saya menyatakan poster ini berkaitan dengan film pendek pelajar atau kompetisi Cine Arena dan saya memiliki hak/izin untuk mengirimkannya ke Poster Board.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        fieldErrors["agreement"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (!validate()) {
                                submitNotice = UiNotice("Form poster belum lengkap.", UiNoticeType.WARNING)
                                return@Button
                            }

                            posterRewardGate.show(
                                context as? android.app.Activity,
                                {
                                    isSubmitting = true
                                    scope.launch {
                                        val activeSession = resolvedSession
                                        if (activeSession == null || !activeSession.status.equals("ACTIVE", ignoreCase = true)) {
                                            isSubmitting = false
                                            submitNotice = UiNotice("Login Cine Arena dulu untuk memasang poster.", UiNoticeType.WARNING)
                                            return@launch
                                        }
                                        val result = repository.submitPoster(
                                            session = activeSession,
                                            category = selectedCategory,
                                            posterImageBase64 = requireNotNull(selectedPosterImage).base64,
                                            posterImageFileName = requireNotNull(selectedPosterImage).fileName,
                                            posterImageMimeType = requireNotNull(selectedPosterImage).mimeType
                                        )
                                        isSubmitting = false
                                        result.onSuccess {
                                            submitNotice = UiNotice("Poster berhasil dipasang di Poster Board.", UiNoticeType.SUCCESS)
                                            clearForm()
                                            onSubmitted()
                                        }.onFailure { error ->
                                            submitNotice = UiNotice(error.message ?: "Gagal mengirim poster.", UiNoticeType.ERROR)
                                        }
                                    }
                                },
                                { message ->
                                    submitNotice = UiNotice(message, UiNoticeType.WARNING)
                                },
                                { isSubmitting = it }
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSubmitting) {
                            Text("Mengirim...")
                        } else {
                            Text("Kirim Poster", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedInPortalHeader(
    session: PortalUserSession
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Login sebagai: ${session.bandName}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "WhatsApp: ${session.whatsappNormalized}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PortalPostDetailScreen(
    post: PortalPost,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreenPreview by remember { mutableStateOf(false) }
    if (isFullscreenPreview) {
        FullscreenPosterViewer(
            imageUrl = post.posterUrl,
            title = post.title,
            onExit = { isFullscreenPreview = false }
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Kembali")
            }
        }
        item {
            RemotePoster(
                imageUrl = post.posterUrl,
                title = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clickable { isFullscreenPreview = true }
            )
        }
        item {
            val displayTitle = post.displayTitle()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!post.submitterName.equals(displayTitle, ignoreCase = true)) {
                    Text(
                        text = post.submitterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = post.displayDate(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(
                    onClick = {},
                    label = { Text(post.category) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (post.shortCaption.isNotBlank()) {
                    Text(
                        text = post.shortCaption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (post.eventDate.isNotBlank()) {
                    Text(
                        text = post.eventDate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (post.targetUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(post.targetUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) {
                        Text("Buka Link")
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalPostCard(
    post: PortalPost,
    onPrimaryClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onReportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimaryClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RemotePoster(
                imageUrl = post.posterUrl,
                title = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = post.submitterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(post.category) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = null
                    )
                    if (post.eventDate.isNotBlank()) {
                        Text(
                            text = post.eventDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (post.shortCaption.isNotBlank()) {
                    Text(
                        text = post.shortCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPreviewClick) {
                        Text("Preview")
                    }
                    TextButton(onClick = onReportClick) {
                        Text("Laporkan")
                    }
                    if (post.targetUrl.isNotBlank()) {
                        TextButton(onClick = onPrimaryClick) {
                            Text("Buka Link")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePoster(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedImageUrl = normalizeDriveImageUrl(imageUrl)
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedImageUrl)
            .crossfade(true)
            .build(),
        contentDescription = "$title poster",
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Poster gagal dimuat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun FullscreenPosterViewer(
    imageUrl: String,
    title: String,
    onExit: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        if (nextScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(scale) {
                detectTapGestures(
                    onTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            onExit()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.4f
                        }
                    }
                )
            }
            .transformable(transformableState),
        contentAlignment = Alignment.Center
    ) {
        RemotePoster(
            imageUrl = imageUrl,
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

private fun PortalPost.displayDate(): String {
    val rawDate = eventDate.ifBlank { createdAt }.trim()
    if (rawDate.isBlank()) return "Tanggal posting belum tersedia"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val parsed = OffsetDateTime.parse(rawDate)
            "Diposting ${parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID")))}"
        } else {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            val date = inputFormat.parse(rawDate)
            if (date != null) "Diposting ${outputFormat.format(date)}" else "Diposting $rawDate"
        }
    }.getOrElse {
        "Diposting $rawDate"
    }
}

private fun PortalPost.displayTitle(): String {
    val raw = title.trim()
    val categoryPrefix = "${category.trim()} - "
    return if (category.isNotBlank() && raw.startsWith(categoryPrefix, ignoreCase = true)) {
        raw.removePrefix(categoryPrefix).trim().ifBlank { raw }
    } else {
        raw
    }
}

private fun normalizeDriveImageUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    if (url.isBlank()) return url

    val fileIdByPath = Regex("/file/d/([^/]+)").find(url)?.groupValues?.get(1)
    if (!fileIdByPath.isNullOrBlank()) {
        return "https://drive.google.com/uc?export=view&id=$fileIdByPath"
    }

    val fileIdByQuery = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1)
    if (!fileIdByQuery.isNullOrBlank()) {
        return "https://drive.google.com/uc?export=view&id=$fileIdByQuery"
    }

    return url
}

private enum class UiNoticeType { SUCCESS, WARNING, ERROR, INFO }

private data class UiNotice(
    val message: String,
    val type: UiNoticeType
)

@Composable
private fun NoticeCard(
    message: String,
    type: UiNoticeType,
    onDismiss: () -> Unit
) {
    val (bg, fg, border) = when (type) {
        UiNoticeType.SUCCESS -> Triple(Color(0x1A2E7D32), Color(0xFFB9F6CA), Color(0x6632C45A))
        UiNoticeType.WARNING -> Triple(Color(0x1A7A5A00), Color(0xFFFFE082), Color(0x66FFC107))
        UiNoticeType.ERROR -> Triple(Color(0x1A8E2430), Color(0xFFFFCDD2), Color(0x66EF5350))
        UiNoticeType.INFO -> Triple(Color(0x1A0277BD), Color(0xFF81D4FA), Color(0x66039BE5))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(14.dp)),
        color = bg,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = fg)
            }
        }
    }
}
