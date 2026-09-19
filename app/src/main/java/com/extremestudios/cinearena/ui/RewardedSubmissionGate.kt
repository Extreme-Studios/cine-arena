package com.extremestudios.cinearena.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun rememberRewardedSubmissionGate(
    context: Context,
    adUnitId: String,
    preferenceName: String,
    cooldownKey: String,
    cooldownMs: Long
): RewardedSubmissionGate {
    return remember(context, adUnitId, preferenceName, cooldownKey, cooldownMs) {
        RewardedSubmissionGate(
            isAdLoading = false,
            remainingCooldownMs = { 0L },
            ensureAdLoaded = {},
            show = { _, onRewardEarned, _, onLoadingChanged ->
                onLoadingChanged(false)
                onRewardEarned()
            }
        )
    }
}

data class RewardedSubmissionGate(
    val isAdLoading: Boolean,
    val remainingCooldownMs: () -> Long,
    val ensureAdLoaded: () -> Unit,
    val show: (
        activity: Activity?,
        onRewardEarned: () -> Unit,
        onMessage: (String) -> Unit,
        onLoadingChanged: (Boolean) -> Unit
    ) -> Unit
)

const val PORTAL_REWARD_PREFERENCE_NAME = "portal_submission_gate"
const val HUB_REGISTER_REWARD_COOLDOWN_KEY = "last_hub_register_reward_at_ms"
const val HUB_LOGIN_REWARD_COOLDOWN_KEY = "last_hub_login_reward_at_ms"
const val PORTAL_POSTER_REWARD_COOLDOWN_KEY = "last_portal_poster_reward_at_ms"
const val SONG_REGISTRATION_REWARD_COOLDOWN_KEY = "last_song_registration_reward_at_ms"
const val WATCH_MOVIE_REWARD_COOLDOWN_KEY = "last_watch_movie_reward_at_ms"
const val PORTAL_REWARD_COOLDOWN_MS = 5 * 60 * 1000L
const val ADMOB_APP_ID = "ca-app-pub-8451272966172172~2236096340"
const val BANNER_AD_UNIT_ID = "ca-app-pub-8451272966172172/8086884900"
const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-8451272966172172/5460721562"
const val REWARD_AD_UNIT_ID = "ca-app-pub-8451272966172172/1305854134"
