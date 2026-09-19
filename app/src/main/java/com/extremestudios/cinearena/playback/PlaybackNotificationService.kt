package com.extremestudios.cinearena.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.extremestudios.cinearena.R

class PlaybackNotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                currentArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                isPlaying = true
                startForeground(NOTIFICATION_ID, buildNotification())
            }

            ACTION_TOGGLE -> {
                if (currentTitle.isBlank()) {
                    currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                    currentArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                }
                isPlaying = !isPlaying
                notifyState()
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                currentTitle = ""
                currentArtist = ""
                isPlaying = false
            }
        }
        return START_NOT_STICKY
    }

    private fun notifyState() {
        if (currentTitle.isBlank()) return
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            return
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private val isForeground: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }
        } else {
            true
        }

    private fun buildNotification(): Notification {
        val toggleIntent = Intent(this, PlaybackNotificationService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val stopIntent = Intent(this, PlaybackNotificationService::class.java).apply {
            action = ACTION_STOP
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 101, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 102, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            103,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle.ifBlank { "Cine Arena" })
            .setContentText(currentArtist.ifBlank { "Playback dummy" })
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                togglePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "X", stopPendingIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dummy Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_START = "com.extremestudios.cinearena.playback.START"
        const val ACTION_TOGGLE = "com.extremestudios.cinearena.playback.TOGGLE"
        const val ACTION_STOP = "com.extremestudios.cinearena.playback.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        private const val CHANNEL_ID = "playback_dummy_channel"
        private const val NOTIFICATION_ID = 1001

        private var currentTitle: String = ""
        private var currentArtist: String = ""
        private var isPlaying: Boolean = false

        fun start(context: Context, title: String, artist: String) {
            val intent = Intent(context, PlaybackNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            context.startService(intent)
        }

        fun toggle(context: Context, title: String, artist: String) {
            val intent = Intent(context, PlaybackNotificationService::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            context.startService(intent)
        }
    }
}
