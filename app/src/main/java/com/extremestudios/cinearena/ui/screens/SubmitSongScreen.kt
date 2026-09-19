package com.extremestudios.cinearena.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.SongRegistrationPayload
import com.extremestudios.cinearena.data.repository.SongRegistrationRepository
import com.extremestudios.cinearena.ui.PORTAL_REWARD_COOLDOWN_MS
import com.extremestudios.cinearena.ui.PORTAL_REWARD_PREFERENCE_NAME
import com.extremestudios.cinearena.ui.REWARD_AD_UNIT_ID
import com.extremestudios.cinearena.ui.SONG_REGISTRATION_REWARD_COOLDOWN_KEY
import com.extremestudios.cinearena.ui.SelectedImagePayload
import com.extremestudios.cinearena.ui.readSelectedImage
import com.extremestudios.cinearena.ui.rememberRewardedSubmissionGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FILM_REGISTRATION_COOLDOWN_MS = 5 * 60 * 1000L
private val FILM_CATEGORY_OPTIONS = listOf("Horror", "Action", "Drama", "Fantasy", "Sci Fi", "Education", "Culture")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubmitSongScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    repository: SongRegistrationRepository = remember { SongRegistrationRepository() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cooldownPrefs = remember {
        context.getSharedPreferences(PORTAL_REWARD_PREFERENCE_NAME, Context.MODE_PRIVATE)
    }
    val activity = context as? Activity
    var songTitle by remember { mutableStateOf("") }
    var release by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var director by remember { mutableStateOf("") }
    var castName by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var trailerUrl by remember { mutableStateOf("") }
    var movieUrl by remember { mutableStateOf("") }
    var agreementChecked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var cooldownTickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedCoverImage by remember { mutableStateOf<SelectedImagePayload?>(null) }

    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val songRegistrationRewardGate = rememberRewardedSubmissionGate(
        context = context,
        adUnitId = REWARD_AD_UNIT_ID,
        preferenceName = PORTAL_REWARD_PREFERENCE_NAME,
        cooldownKey = SONG_REGISTRATION_REWARD_COOLDOWN_KEY,
        cooldownMs = PORTAL_REWARD_COOLDOWN_MS
    )
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            readSelectedImage(context, uri)
                .onSuccess {
                    selectedCoverImage = it
                    fieldErrors.remove("coverImage")
                }
                .onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "Gagal memuat cover artwork.")
                }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            cooldownTickMs = System.currentTimeMillis()
        }
    }

    fun validate(): Boolean {
        fieldErrors.clear()
        if (songTitle.isBlank()) fieldErrors["songTitle"] = "Judul Film wajib diisi"
        if (release.isBlank()) fieldErrors["release"] = "Release wajib diisi"
        if (category.isBlank()) fieldErrors["category"] = "Kategori wajib diisi"
        if (movieUrl.isBlank()) fieldErrors["movieUrl"] = "Link Google Drive Movie wajib diisi"
        if (trailerUrl.isBlank()) fieldErrors["trailer"] = "Link Google Drive Trailer wajib diisi"
        if (!agreementChecked) fieldErrors["agreement"] = "Pernyataan izin wajib dicentang"
        return fieldErrors.isEmpty()
    }

    fun clearForm() {
        songTitle = ""
        release = ""
        category = ""
        duration = ""
        director = ""
        castName = ""
        synopsis = ""
        trailerUrl = ""
        movieUrl = ""
        agreementChecked = false
        selectedCoverImage = null
        fieldErrors.clear()
    }

    fun formatCooldownLabel(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                item {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Registrasi Film",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_revert),
                                    contentDescription = "Kembali",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
            if (onBack == null) {
                item {
                    Text(
                        text = "Registrasi Film",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item { FormField(songTitle, { songTitle = it }, "1. Judul Film", fieldErrors["songTitle"]) }
            item { FormField(release, { release = it }, "2. Release", fieldErrors["release"]) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "3. Kategori",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FILM_CATEGORY_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = {
                                    category = option
                                    fieldErrors.remove("category")
                                },
                                label = { Text(option) },
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
                    fieldErrors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            item { FormField(duration, { duration = it }, "4. Durasi", null) }
            item { FormField(director, { director = it }, "5. Director (Opsional)", null) }
            item { FormField(castName, { castName = it }, "6. Nama Pemain (Opsional)", null) }
            item { FormField(synopsis, { synopsis = it }, "7. Synopsis", null, minLines = 3) }
            item { FormField(movieUrl, { movieUrl = it }, "8. Link Google Drive Movie", fieldErrors["movieUrl"]) }
            item { FormField(trailerUrl, { trailerUrl = it }, "9. Link Google Drive Trailer", fieldErrors["trailer"]) }
            item {
                Text(
                    text = "Upload trailer dari HP dinonaktifkan untuk mencegah error memori. Gunakan link Google Drive Trailer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "10. Pilih Cover Artwork",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ambil cover JPG atau PNG dari galeri HP. Cover tetap opsional.",
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
                        Text(if (selectedCoverImage == null) "Pilih Cover Artwork" else "Ganti Cover Artwork")
                    }
                    selectedCoverImage?.let { cover ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = cover.previewBitmap.asImageBitmap(),
                                contentDescription = "Preview cover artwork",
                                modifier = Modifier
                                    .size(84.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = cover.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = cover.mimeType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kebijakan Registrasi Film",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("1. Registrasi ini hanya untuk proses kurasi dan review internal tim Cine Arena.")
                        Text("2. Film tidak otomatis dipublikasikan ke katalog utama.")
                        Text("3. Keputusan tampil di aplikasi sepenuhnya berdasarkan review admin.")
                        Text("4. Pengirim bertanggung jawab atas keaslian karya atau izin penggunaan yang sah.")
                        Text("5. Link Google Drive video/trailer yang dikirim hanya dipakai untuk proses registrasi dan review admin.")
                        Text("6. Data kontak dipakai untuk komunikasi terkait status review.")
                    }
                    Text(
                        text = "Scroll kebijakan di atas lalu centang persetujuan di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = agreementChecked, onCheckedChange = { agreementChecked = it })
                        Text(
                            "Saya menyatakan bahwa film ini adalah karya sendiri / memiliki izin resmi, dan mengizinkan Cine Arena menampilkan film ini di aplikasi.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    fieldErrors["agreement"]?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                val lastSubmitAt = cooldownPrefs.getLong(SONG_REGISTRATION_REWARD_COOLDOWN_KEY, 0L)
                val submitCooldownMs = (lastSubmitAt + FILM_REGISTRATION_COOLDOWN_MS - cooldownTickMs).coerceAtLeast(0L)
                val submitEnabled = !isLoading && submitCooldownMs <= 0L
                Button(
                    onClick = {
                        if (submitCooldownMs > 0L) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Cooldown aktif. Coba kirim registrasi lagi ${formatCooldownLabel(submitCooldownMs)}."
                                )
                            }
                            return@Button
                        }

                        if (!validate()) {
                            scope.launch { snackbarHostState.showSnackbar("Form belum lengkap. Cek kembali data wajib.") }
                            return@Button
                        }

                        songRegistrationRewardGate.show(
                            activity,
                            {
                                isLoading = true
                                scope.launch {
                                    val payload = SongRegistrationPayload(
                                        title = songTitle.trim(),
                                        release = release.trim(),
                                        category = category.trim(),
                                        duration = duration.trim(),
                                        director = director.trim(),
                                        castName = castName.trim(),
                                        synopsis = synopsis.trim(),
                                        copyrightAgreement = agreementChecked,
                                        trailerUrl = trailerUrl.trim().ifBlank { null },
                                        movieUrl = movieUrl.trim().ifBlank { null },
                                        coverImageBase64 = selectedCoverImage?.base64,
                                        coverImageFileName = selectedCoverImage?.fileName,
                                        coverImageMimeType = selectedCoverImage?.mimeType
                                    )

                                    val result = repository.submitRegistration(payload)
                                    isLoading = false

                                    result.onSuccess {
                                        cooldownPrefs.edit()
                                            .putLong(SONG_REGISTRATION_REWARD_COOLDOWN_KEY, System.currentTimeMillis())
                                            .apply()
                                        snackbarHostState.showSnackbar(
                                            "Registrasi film berhasil dikirim. Tim Cine Arena akan melakukan review terlebih dahulu."
                                        )
                                        clearForm()
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar(
                                            "Gagal mengirim registrasi: ${error.message ?: "Terjadi kesalahan, coba lagi."}"
                                        )
                                    }
                                }
                            },
                            { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            },
                            { isLoading = it }
                        )
                    },
                    enabled = submitEnabled,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            if (submitCooldownMs > 0L) "Kirim Registrasi (${formatCooldownLabel(submitCooldownMs)})" else "Kirim Registrasi",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    minLines: Int = 1
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        isError = error != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            focusedLabelColor = MaterialTheme.colorScheme.primary
        ),
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(
            imeAction = if (minLines == 1) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus(force = true) }
        ),
        supportingText = {
            if (error != null) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
