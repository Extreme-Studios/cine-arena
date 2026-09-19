@file:OptIn(UnstableApi::class)

package com.extremestudios.cinearena.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.extremestudios.cinearena.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class OrientationMode {
    Auto, Landscape, Portrait
}

private const val QUALIFIED_VIEW_WATCH_MS = 10 * 60 * 1000L

@Composable
fun VideoPlayerScreen(
    title: String,
    rawUrl: String,
    countViewAfter30m: Boolean = false,
    onQualifiedView: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val resolvedUrl = remember(rawUrl) { resolveDriveUrl(rawUrl) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isVrEnabled by remember { mutableStateOf(false) }
    var vrStereoMode by remember { mutableStateOf(VrStereoMode.Mono) }
    var vrEyeSwap by remember { mutableStateOf(false) }
    var vrDebugGrid by remember { mutableStateOf(false) }
    var vrFrameTimestampNs by remember { mutableStateOf(0L) }
    var orientationMode by remember { mutableStateOf(OrientationMode.Landscape) }
    var isVideoLandscape by remember { mutableStateOf(true) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
    var hasVideoSize by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsPulse by remember { mutableStateOf(0) }
    var hasCountedView by remember(resolvedUrl) { mutableStateOf(false) }

    val exoPlayer = remember(resolvedUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(resolvedUrl)))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        hasVideoSize = true
                        isVideoLandscape = videoSize.width >= videoSize.height
                        videoAspectRatio = (
                            videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        ).coerceAtLeast(0.1f)
                        orientationMode = if (isVideoLandscape) {
                            OrientationMode.Landscape
                        } else {
                            OrientationMode.Portrait
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerError = "Video gagal diputar. Coba cek link film."
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(activity, isVrEnabled, orientationMode, isVideoLandscape) {
        if (activity == null) return@DisposableEffect onDispose {}
        val previousOrientation = activity.requestedOrientation
        val targetOrientation = when (orientationMode) {
            OrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.Auto -> if (!hasVideoSize || isVideoLandscape || isVrEnabled) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        runCatching {
            activity.requestedOrientation = targetOrientation
        }
        onDispose {
            runCatching {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    LaunchedEffect(controlsVisible, controlsPulse) {
        if (controlsVisible) {
            delay(1000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(isVrEnabled, vrFrameTimestampNs) {
        if (isVrEnabled && vrFrameTimestampNs > 0L) {
            val leftTimestampNs = vrFrameTimestampNs
            val rightTimestampNs = vrFrameTimestampNs
            val driftNs = kotlin.math.abs(leftTimestampNs - rightTimestampNs)
            if (driftNs > 1_000_000L) {
                Log.w("CineArenaVR", "VR eye timestamp drift > 1ms: ${driftNs / 1_000_000f} ms")
            }
        }
    }

    LaunchedEffect(countViewAfter30m, resolvedUrl) {
        if (!countViewAfter30m) return@LaunchedEffect
        var watchedMs = 0L
        var previousTick = System.currentTimeMillis()
        while (isActive && !hasCountedView) {
            delay(500L)
            val now = System.currentTimeMillis()
            val elapsed = (now - previousTick).coerceAtLeast(0L)
            previousTick = now
            if (exoPlayer.isPlaying) {
                watchedMs += elapsed
                if (watchedMs >= QUALIFIED_VIEW_WATCH_MS) {
                    hasCountedView = true
                    onQualifiedView()
                }
            }
        }
    }

    fun nudgeControls() {
        controlsVisible = true
        controlsPulse += 1
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { nudgeControls() })
            }
    ) {
        if (isVrEnabled) {
            VrFrameSyncPlayer(
                player = exoPlayer,
                stereoMode = vrStereoMode,
                eyeSwap = vrEyeSwap,
                contentAspectRatio = videoAspectRatio,
                onFrameTimestamp = { vrFrameTimestampNs = it },
                modifier = Modifier.fillMaxSize()
            )
            VrPlaybackControls(
                player = exoPlayer,
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
            if (vrDebugGrid) {
                VrDebugOverlay(
                    frameTimestampNs = vrFrameTimestampNs,
                    stereoMode = vrStereoMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            SingleVideoPlayer(
                player = exoPlayer,
                onPlayerUiVisible = { visible ->
                    if (visible) nudgeControls()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlIconButton(
                        label = if (isVrEnabled) "VR ON" else "VR OFF",
                        onClick = {
                            nudgeControls()
                            isVrEnabled = !isVrEnabled
                        }
                    ) {
                        Icon(
                            painterResource(android.R.drawable.ic_menu_view),
                            contentDescription = if (isVrEnabled) "VR On" else "VR Off",
                            tint = Color.White
                        )
                    }
                    if (isVrEnabled) {
                        ControlTextButton(
                            label = when (vrStereoMode) {
                                VrStereoMode.Mono -> "Mono"
                                VrStereoMode.SideBySide -> "SBS"
                                VrStereoMode.TopBottom -> "T/B"
                            },
                            onClick = {
                                nudgeControls()
                                vrStereoMode = when (vrStereoMode) {
                                    VrStereoMode.Mono -> VrStereoMode.SideBySide
                                    VrStereoMode.SideBySide -> VrStereoMode.TopBottom
                                    VrStereoMode.TopBottom -> VrStereoMode.Mono
                                }
                            }
                        )
                        ControlTextButton(
                            label = "Swap",
                            onClick = {
                                nudgeControls()
                                vrEyeSwap = !vrEyeSwap
                            }
                        )
                        ControlTextButton(
                            label = "Grid",
                            onClick = {
                                nudgeControls()
                                vrDebugGrid = !vrDebugGrid
                            }
                        )
                        ControlTextButton(
                            label = "Reset",
                            onClick = {
                                nudgeControls()
                                vrStereoMode = VrStereoMode.Mono
                                vrEyeSwap = false
                                vrDebugGrid = false
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlIconButton(
                        label = "Portrait",
                        onClick = {
                            nudgeControls()
                            orientationMode = OrientationMode.Portrait
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phone_portrait),
                            contentDescription = "Portrait",
                            tint = Color.White
                        )
                    }
                    ControlIconButton(
                        label = "Landscape",
                        onClick = {
                            nudgeControls()
                            orientationMode = OrientationMode.Landscape
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phone_landscape),
                            contentDescription = "Landscape",
                            tint = Color.White
                        )
                    }
                }
            }            
        }

        playerError?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(14.dp)
            ) {
                Text(message, color = Color.White)
            }
        }
    }
}

@Composable
private fun ControlTextButton(
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
        ) {
            Text(label.take(3), color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ControlIconButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
        ) {
            content()
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SingleVideoPlayer(
    player: ExoPlayer,
    onPlayerUiVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onPlayerUiVisible(visibility == View.VISIBLE)
                    }
                )
                this.player = player
            }
        },
        update = { playerView ->
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            playerView.player = player
            playerView.controllerShowTimeoutMs = 1000
        },
        modifier = modifier
    )
}

@Composable
private fun VrFrameSyncPlayer(
    player: ExoPlayer,
    stereoMode: VrStereoMode,
    eyeSwap: Boolean,
    contentAspectRatio: Float,
    onFrameTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { viewContext ->
            VrFrameSyncView(viewContext).apply {
                this.player = player
                this.stereoMode = stereoMode
                this.eyeSwap = eyeSwap
                this.contentAspectRatio = contentAspectRatio
                this.onFrameTimestampChanged = onFrameTimestamp
            }
        },
        update = { vrView ->
            vrView.player = player
            vrView.stereoMode = stereoMode
            vrView.eyeSwap = eyeSwap
            vrView.contentAspectRatio = contentAspectRatio
            vrView.onFrameTimestampChanged = onFrameTimestamp
        },
        modifier = modifier
    )
}

@Composable
private fun VrDebugOverlay(
    frameTimestampNs: Long,
    stereoMode: VrStereoMode,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.28f)
            val centerColor = Color.Red.copy(alpha = 0.5f)
            val halfWidth = size.width / 2f
            drawLine(centerColor, start = androidx.compose.ui.geometry.Offset(halfWidth, 0f), end = androidx.compose.ui.geometry.Offset(halfWidth, size.height), strokeWidth = 2f)
            repeat(2) { eye ->
                val left = eye * halfWidth
                drawRect(
                    color = gridColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(halfWidth, size.height),
                    style = Stroke(width = 1f)
                )
                for (i in 1..3) {
                    val x = left + (halfWidth * i / 4f)
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                }
                for (i in 1..3) {
                    val y = size.height * i / 4f
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(left, y), end = androidx.compose.ui.geometry.Offset(left + halfWidth, y), strokeWidth = 1f)
                }
            }
        }
        Text(
            text = "L ${frameTimestampNs / 1_000_000} ms   R ${frameTimestampNs / 1_000_000} ms   ${stereoMode.name}",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VrPlaybackControls(
    player: ExoPlayer,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { viewContext ->
            PlayerControlView(viewContext).apply {
                this.player = player
                showTimeoutMs = 0
                setTimeBarMinUpdateInterval(200)
                isAnimationEnabled = false
                show()
            }
        },
        update = { controls ->
            controls.player = player
            if (visible) {
                controls.visibility = View.VISIBLE
                controls.alpha = 1f
                controls.show()
            } else {
                controls.hide()
                controls.alpha = 0f
                controls.visibility = View.INVISIBLE
            }
        },
        modifier = modifier
    )
}

private fun resolveDriveUrl(url: String): String {
    val trimmed = url.trim()
    val idFromFilePattern = Regex("""/d/([a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.getOrNull(1)
    val idFromOpenPattern = Regex("""[?&]id=([a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.getOrNull(1)
    val fileId = idFromFilePattern ?: idFromOpenPattern
    if (fileId.isNullOrBlank()) return trimmed
    return "https://drive.google.com/uc?export=download&id=$fileId"
}
