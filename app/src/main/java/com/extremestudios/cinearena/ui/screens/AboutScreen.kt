package com.extremestudios.cinearena.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.extremestudios.cinearena.data.local.PortalSessionStore
import com.extremestudios.cinearena.data.model.PortalUserSession
import com.extremestudios.cinearena.data.repository.PortalRepository
import com.extremestudios.cinearena.ui.HUB_LOGIN_REWARD_COOLDOWN_KEY
import com.extremestudios.cinearena.ui.HUB_REGISTER_REWARD_COOLDOWN_KEY
import com.extremestudios.cinearena.ui.PORTAL_REWARD_COOLDOWN_MS
import com.extremestudios.cinearena.ui.PORTAL_REWARD_PREFERENCE_NAME
import com.extremestudios.cinearena.ui.REWARD_AD_UNIT_ID
import com.extremestudios.cinearena.ui.rememberRewardedSubmissionGate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    openLoginOnStart: Boolean = false,
    onLoginSuccess: () -> Unit = {},
    onOpenRegistration: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val portalSessionStore = remember { PortalSessionStore(context) }
    val portalRepository = remember { PortalRepository() }
    val portalSession by portalSessionStore.sessionFlow.collectAsState(initial = null)
    val pendingHubWhatsapp by portalSessionStore.pendingWhatsappFlow.collectAsState(initial = null)
    val lastHubWhatsapp by portalSessionStore.lastHubWhatsappFlow.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cooldownPrefs = remember {
        context.getSharedPreferences(PORTAL_REWARD_PREFERENCE_NAME, Context.MODE_PRIVATE)
    }
    val authErrors = remember { mutableStateMapOf<String, String>() }
    var cooldownTickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var bandName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var loginWhatsapp by remember { mutableStateOf("") }
    var registerMessage by remember { mutableStateOf<String?>(null) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    var isSubmittingPortalAuth by remember { mutableStateOf(false) }
    var showHubLoginDialog by remember { mutableStateOf(false) }
    var showHubRegisterDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showVideoUploadDialog by remember { mutableStateOf(false) }
    var videoTitle by remember { mutableStateOf("") }
    var videoGenre by remember { mutableStateOf("") }
    var videoSchool by remember { mutableStateOf("") }
    var videoWhatsapp by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var videoUploadMessage by remember { mutableStateOf<String?>(null) }
    var isSubmittingVideoUpload by remember { mutableStateOf(false) }
    var isVideoGenreMenuExpanded by remember { mutableStateOf(false) }
    val videoGenres = remember { listOf("Horror", "Action", "Drama", "Fantasy", "Sci Fi", "Education", "Culture") }
    val isHubActive = portalSession?.status.equals("ACTIVE", ignoreCase = true)
    val hubRegisterRewardGate = rememberRewardedSubmissionGate(
        context = context,
        adUnitId = REWARD_AD_UNIT_ID,
        preferenceName = PORTAL_REWARD_PREFERENCE_NAME,
        cooldownKey = HUB_REGISTER_REWARD_COOLDOWN_KEY,
        cooldownMs = PORTAL_REWARD_COOLDOWN_MS
    )
    val hubLoginRewardGate = rememberRewardedSubmissionGate(
        context = context,
        adUnitId = REWARD_AD_UNIT_ID,
        preferenceName = PORTAL_REWARD_PREFERENCE_NAME,
        cooldownKey = HUB_LOGIN_REWARD_COOLDOWN_KEY,
        cooldownMs = PORTAL_REWARD_COOLDOWN_MS
    )

    LaunchedEffect(Unit) {
        Log.d("AboutScreen", "About screen rendered")
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            cooldownTickMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(openLoginOnStart, isHubActive) {
        if (openLoginOnStart && isHubActive) {
            onLoginSuccess()
        }
    }

    suspend fun syncHubStatus(whatsappRaw: String) {
        if (whatsappRaw.isBlank()) return
        val result = portalRepository.checkHubStatus(whatsappRaw)
        result.onSuccess { account ->
            when {
                account.status.equals("ACTIVE", ignoreCase = true) -> {
                    portalSessionStore.saveSession(account)
                    portalSessionStore.clearPendingWhatsapp()
                    pendingMessage = null
                    loginMessage = null
                }
                account.status.equals("PENDING", ignoreCase = true) -> {
                    portalSessionStore.clearSession()
                    portalSessionStore.savePendingWhatsapp(account.whatsappNormalized)
                    pendingMessage = "Pendaftaran sedang menunggu approval admin."
                }
                account.status.equals("BLOCKED", ignoreCase = true) -> {
                    portalSessionStore.clearSession()
                    portalSessionStore.clearPendingWhatsapp()
                    pendingMessage = "Akun Anda diblokir admin."
                }
            }
        }.onFailure { error ->
            if (portalSession == null) {
                pendingMessage = error.message
            }
        }
    }

    LaunchedEffect(portalSession?.whatsappNormalized, pendingHubWhatsapp, lastHubWhatsapp) {
        when {
            portalSession != null -> syncHubStatus(portalSession?.whatsappNormalized.orEmpty())
            !pendingHubWhatsapp.isNullOrBlank() -> syncHubStatus(pendingHubWhatsapp.orEmpty())
            !lastHubWhatsapp.isNullOrBlank() -> syncHubStatus(lastHubWhatsapp.orEmpty())
        }
    }

    DisposableEffect(lifecycleOwner, portalSession?.whatsappNormalized, pendingHubWhatsapp, lastHubWhatsapp) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val watchWhatsapp = portalSession?.whatsappNormalized ?: pendingHubWhatsapp ?: lastHubWhatsapp
                if (!watchWhatsapp.isNullOrBlank()) {
                    scope.launch { syncHubStatus(watchWhatsapp) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun validatePortalAuth(): Boolean {
        authErrors.clear()
        registerMessage = null
        pendingMessage = null
        if (bandName.isBlank()) authErrors["bandName"] = "Nama kreator wajib diisi"
        if (schoolName.isBlank()) authErrors["schoolName"] = "Asal Sekolah wajib diisi"
        if (whatsapp.isBlank()) authErrors["whatsapp"] = "Nomor WhatsApp wajib diisi"
        if (authErrors.isNotEmpty()) return false
        return portalRepository.normalizeWhatsapp(whatsapp).fold(
            onSuccess = { true },
            onFailure = {
                authErrors["whatsapp"] = it.message ?: "Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif."
                false
            }
        )
    }

    fun validateHubLogin(): Boolean {
        authErrors.clear()
        loginMessage = null
        if (loginWhatsapp.isBlank()) {
            authErrors["loginWhatsapp"] = "Nomor WhatsApp wajib diisi"
            return false
        }
        return portalRepository.normalizeWhatsapp(loginWhatsapp).fold(
            onSuccess = { true },
            onFailure = {
                authErrors["loginWhatsapp"] = it.message ?: "Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif."
                false
            }
        )
    }

    fun validateVideoUpload(): Boolean {
        authErrors.clear()
        videoUploadMessage = null
        if (videoTitle.isBlank()) authErrors["videoTitle"] = "Judul video wajib diisi"
        if (videoGenre.isBlank()) authErrors["videoGenre"] = "Genre wajib dipilih"
        if (videoSchool.isBlank()) authErrors["videoSchool"] = "Asal Sekolah wajib diisi"
        if (videoWhatsapp.isBlank()) authErrors["videoWhatsapp"] = "Nomor WhatsApp wajib diisi"
        if (videoUrl.isBlank()) authErrors["videoUrl"] = "Link video wajib diisi"
        if (authErrors.isNotEmpty()) return false
        return portalRepository.normalizeWhatsapp(videoWhatsapp).fold(
            onSuccess = { true },
            onFailure = {
                authErrors["videoWhatsapp"] = it.message ?: "Nomor WhatsApp tidak valid."
                false
            }
        )
    }

    fun remainingCooldownMs(cooldownKey: String): Long {
        val last = cooldownPrefs.getLong(cooldownKey, 0L)
        return (last + PORTAL_REWARD_COOLDOWN_MS - cooldownTickMs).coerceAtLeast(0L)
    }
    fun formatCooldownLabel(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    if (showHubLoginDialog && !isHubActive) {
        val loginCooldownMs = remainingCooldownMs(HUB_LOGIN_REWARD_COOLDOWN_KEY)
        val loginEnabled = !isSubmittingPortalAuth && loginCooldownMs <= 0L
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showHubLoginDialog = false
                    authErrors.clear()
                    loginMessage = null
                }
            },
            title = {
                Text(
                    text = "Login Cine Arena",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutAuthField(
                        value = loginWhatsapp,
                        onValueChange = { loginWhatsapp = it },
                        label = "Nomor WhatsApp",
                        error = authErrors["loginWhatsapp"]
                    )
                    loginMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (loginCooldownMs > 0L) {
                            loginMessage = "Cooldown login aktif. Coba lagi ${formatCooldownLabel(loginCooldownMs)}."
                            return@Button
                        }
                        if (!validateHubLogin()) return@Button
                        hubLoginRewardGate.show(
                            activity,
                            {
                                isSubmittingPortalAuth = true
                                scope.launch {
                                    val result = portalRepository.loginHubBand(loginWhatsapp.trim())
                                    isSubmittingPortalAuth = false
                                    result.onSuccess { account ->
                                        portalSessionStore.saveSession(account)
                                        portalSessionStore.clearPendingWhatsapp()
                                        loginWhatsapp = ""
                                        authErrors.clear()
                                        loginMessage = null
                                        pendingMessage = null
                                        showHubLoginDialog = false
                                        onLoginSuccess()
                                    }.onFailure { error ->
                                        loginMessage = error.message ?: "Gagal login Cine Arena."
                                    }
                                }
                            },
                            { loginMessage = it },
                            { isSubmittingPortalAuth = it }
                        )
                    },
                    enabled = loginEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        if (isSubmittingPortalAuth) "Memproses..."
                        else if (loginCooldownMs > 0L) "Login (${formatCooldownLabel(loginCooldownMs)})"
                        else "Login",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHubLoginDialog = false
                        authErrors.clear()
                        loginMessage = null
                    },
                    enabled = !isSubmittingPortalAuth
                ) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showHubRegisterDialog && !isHubActive) {
        val registerCooldownMs = remainingCooldownMs(HUB_REGISTER_REWARD_COOLDOWN_KEY)
        val registerEnabled = !isSubmittingPortalAuth && registerCooldownMs <= 0L
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showHubRegisterDialog = false
                    authErrors.clear()
                    registerMessage = null
                }
            },
            title = {
                Text(
                    text = "Daftar Kreator",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutAuthField(
                        value = bandName,
                        onValueChange = { bandName = it },
                        label = "Nama Kreator",
                        error = authErrors["bandName"]
                    )
                    AboutAuthField(
                        value = schoolName,
                        onValueChange = { schoolName = it },
                        label = "Asal Sekolah",
                        error = authErrors["schoolName"]
                    )
                    AboutAuthField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = "Nomor WhatsApp",
                        error = authErrors["whatsapp"]
                    )
                    registerMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (registerCooldownMs > 0L) {
                            registerMessage = "Cooldown daftar member aktif. Coba lagi ${formatCooldownLabel(registerCooldownMs)}."
                            return@Button
                        }
                        if (!validatePortalAuth()) return@Button
                        hubRegisterRewardGate.show(
                            activity,
                            {
                                isSubmittingPortalAuth = true
                                scope.launch {
                                    val result = portalRepository.registerHubBand(
                                        namaKreator = bandName.trim(),
                                        asalSekolah = schoolName.trim(),
                                        whatsappRaw = whatsapp.trim()
                                    )
                                    isSubmittingPortalAuth = false
                                    result.onSuccess { normalizedWhatsapp ->
                                        bandName = ""
                                        schoolName = ""
                                        whatsapp = ""
                                        authErrors.clear()
                                        registerMessage = null
                                        portalSessionStore.savePendingWhatsapp(normalizedWhatsapp)
                                        pendingMessage = "Pendaftaran terkirim. Tunggu approval admin."
                                        loginMessage = null
                                        showHubRegisterDialog = false
                                    }.onFailure { error ->
                                        registerMessage = error.message ?: "Gagal mendaftarkan kreator."
                                    }
                                }
                            },
                            { registerMessage = it },
                            { isSubmittingPortalAuth = it }
                        )
                    },
                    enabled = registerEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        if (isSubmittingPortalAuth) "Memproses..."
                        else if (registerCooldownMs > 0L) "Daftar / Masuk (${formatCooldownLabel(registerCooldownMs)})"
                        else "Daftar / Masuk",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHubRegisterDialog = false
                        authErrors.clear()
                        registerMessage = null
                    },
                    enabled = !isSubmittingPortalAuth
                ) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showVideoUploadDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingVideoUpload) {
                    showVideoUploadDialog = false
                    authErrors.clear()
                    videoUploadMessage = null
                }
            },
            title = {
                Text(
                    text = "Upload Video",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutAuthField(videoTitle, { videoTitle = it }, "Judul Video", authErrors["videoTitle"])
                    Box {
                        Button(
                            onClick = { isVideoGenreMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(videoGenre.ifBlank { "Pilih Genre" }) }
                        DropdownMenu(
                            expanded = isVideoGenreMenuExpanded,
                            onDismissRequest = { isVideoGenreMenuExpanded = false }
                        ) {
                            videoGenres.forEach { genre ->
                                DropdownMenuItem(
                                    text = { Text(genre) },
                                    onClick = {
                                        videoGenre = genre
                                        authErrors.remove("videoGenre")
                                        isVideoGenreMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    authErrors["videoGenre"]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.error)
                    }
                    AboutAuthField(videoSchool, { videoSchool = it }, "Asal Sekolah", authErrors["videoSchool"])
                    AboutAuthField(videoWhatsapp, { videoWhatsapp = it }, "Nomor WhatsApp", authErrors["videoWhatsapp"])
                    AboutAuthField(videoUrl, { videoUrl = it }, "Link Video (Drive, Dropbox, YouTube, dll.)", authErrors["videoUrl"])
                    videoUploadMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!validateVideoUpload()) return@Button
                        isSubmittingVideoUpload = true
                        scope.launch {
                            portalRepository.submitVideoUpload(
                                judulVideo = videoTitle.trim(),
                                genre = videoGenre,
                                asalSekolah = videoSchool.trim(),
                                whatsappRaw = videoWhatsapp.trim(),
                                videoUrl = videoUrl.trim()
                            ).onSuccess {
                                videoTitle = ""
                                videoGenre = ""
                                videoSchool = ""
                                videoWhatsapp = ""
                                videoUrl = ""
                                authErrors.clear()
                                videoUploadMessage = null
                                showVideoUploadDialog = false
                            }.onFailure { error ->
                                videoUploadMessage = error.message ?: "Upload video gagal dikirim."
                            }
                            isSubmittingVideoUpload = false
                        }
                    },
                    enabled = !isSubmittingVideoUpload,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isSubmittingVideoUpload) "Mengirim..." else "Kirim Video")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoUploadDialog = false }, enabled = !isSubmittingVideoUpload) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showLogoutConfirmDialog && portalSession != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPortalAuth) {
                    showLogoutConfirmDialog = false
                }
            },
            title = {
                Text(
                    text = "Keluar Cine Arena",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "Yakin ingin keluar dari Cine Arena di perangkat ini?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        portalSessionStore.clearSession()
                        portalSessionStore.clearLastHubWhatsapp()
                        showLogoutConfirmDialog = false
                    },
                    enabled = !isSubmittingPortalAuth,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Keluar",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                    },
                    enabled = !isSubmittingPortalAuth
                ) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "About Us",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Cine Arena adalah ruang kompetisi film pendek khusus pelajar. Kami memberi panggung bagi siswa dan komunitas sekolah untuk menampilkan cerita, gagasan, dan kreativitas visual mereka.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        item {
            AboutSectionTitle("Submission Guidelines")
            Spacer(Modifier.height(6.dp))
            GuidelineCard("Karya Orisinal", "Pastikan film yang disubmit adalah milik sendiri atau memiliki izin resmi dari kreator/komunitas terkait.")
            Spacer(Modifier.height(10.dp))
            GuidelineCard("Hak Kekayaan Intelektual", "Tidak melanggar hak cipta pihak manapun, termasuk musik, footage, poster, atau materi arsip.")
            Spacer(Modifier.height(10.dp))
            GuidelineCard("Lisensi Distribusi", "Memberi izin kepada Cine Arena untuk menampilkan karya di dalam platform.")
        }
        item {
            InfoBlock("PENGUMUMAN: Cine Arena sedang membuka ruang kompetisi film pendek pelajar. Katalog, poster, login kreator, dan kurasi admin tetap mengikuti sistem Google Sheet yang aktif.")
        }
        item {
            AboutSectionTitle("Upload Video")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Kirim judul, genre, asal sekolah, nomor WhatsApp, dan link video dari Google Drive, Dropbox, YouTube, atau layanan lain. Data masuk ke Google Sheet untuk diapprove.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    authErrors.clear()
                    videoUploadMessage = null
                    showVideoUploadDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Upload Video", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun AboutAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = error != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            focusedLabelColor = MaterialTheme.colorScheme.primary
        ),
        supportingText = {
            if (error != null) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun PendingHubCard(
    whatsapp: String,
    message: String,
    isChecking: Boolean,
    onCheckStatus: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Nomor: $whatsapp",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCheckStatus,
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isChecking) "Mengecek..." else "Cek Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun LoggedInHubCard(
    session: PortalUserSession,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Terdaftar sebagai: ${session.bandName}",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Asal Sekolah: ${session.city}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "WhatsApp: ${session.whatsappNormalized}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sekarang menu Registrasi Film dan kirim poster sudah aktif untuk akun Anda.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Keluar Cine Arena",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun GuidelineCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoBlock(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = Color.White
        )
    }
}
