package com.extremestudios.cinearena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import com.extremestudios.cinearena.navigation.CineArenaApp
import com.extremestudios.cinearena.ui.theme.CineArenaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)

        enableEdgeToEdge()
        setContent {
            CineArenaTheme {
                var splashFinished by remember { mutableStateOf(false) }
                var continueToApp by remember { mutableStateOf(false) }
                if (!splashFinished) {
                    SplashScreen(onFinish = { splashFinished = true })
                } else if (!continueToApp) {
                    LaunchedEffect(Unit) {
                        continueToApp = true
                    }
                } else {
                    CineArenaApp()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3500)
        onFinish()
    }
    val versionName = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val loadingTransition = rememberInfiniteTransition(label = "splash-loading")
    val loadingProgress = loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loading-progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val logoPainter = painterResource(id = R.drawable.splash)
        Image(
            painter = logoPainter,
            contentDescription = "Splash",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(loadingProgress.value)
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFE9C277))
                )
            }

            Text(
                text = "Powered By EXTREME STUDIOS",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = versionName,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
