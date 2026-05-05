package com.robert.qalarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.edit

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volumeStep = 0
    private var volumeObserver: android.database.ContentObserver? = null
    private var volumeObserverRegistered = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "PAUSE") {
            mediaPlayer?.pause()
            return START_STICKY
        }
        if (intent?.action == "RESUME") {
            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                if (mediaPlayerReady) {
                    try {
                        mediaPlayer?.start()
                    } catch (e: Exception) {
                        // Player in bad state — rebuild from last known path
                        val path = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                            .getString("last_ringtone_path", null)
                        startMediaPlayer(path)
                    }
                }
                // If not ready yet, setOnPreparedListener will call start() automatically
            }
            return START_STICKY
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QRAlarm::ServiceWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val startVolume = (maxVolume * 0.5f).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, startVolume, 0)

        // Volume enforcement observer — registered after the ramp so it doesn't
        // fight the ramp's own setStreamVolume calls.
        volumeObserver = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
                if (current < max) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                }
            }
        }

        // Gentle volume ramp 50% → 100% over 30 seconds, then lock volume at max.
        val volumeRunnable = object : Runnable {
            override fun run() {
                if (volumeStep <= 30) {
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    val newVolume = startVolume + ((max - startVolume) * volumeStep / 30)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, newVolume, 0)
                    volumeStep++
                    handler.postDelayed(this, 1000)
                } else {
                    contentResolver.registerContentObserver(
                        android.provider.Settings.System.CONTENT_URI,
                        true,
                        volumeObserver!!
                    )
                    volumeObserverRegistered = true
                }
            }
        }
        handler.postDelayed(volumeRunnable, 1000)

        val channelId = "alarm_channel"
        val channel = NotificationChannel(
            channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("qr_code", intent?.getStringExtra("qr_code"))
            putExtra("ringtone_path", intent?.getStringExtra("ringtone_path"))
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("QRAlarm")
            .setContentText("Time to wake up!")
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        startForeground(1, notification)

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val ringtonePath = intent?.getStringExtra("ringtone_path")?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("last_ringtone_path", null)

        if (!ringtonePath.isNullOrEmpty()) {
            prefs.edit { putString("last_ringtone_path", ringtonePath) }
        }

        startMediaPlayer(ringtonePath)

        return START_STICKY
    }

    // Builds and starts the media player asynchronously to avoid blocking the main thread.
    // Falls back to the system default alarm ringtone on any error.
    private fun startMediaPlayer(path: String?) {
        mediaPlayerReady = false
        mediaPlayer?.release()
        mediaPlayer = null

        if (!path.isNullOrEmpty()) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    setOnPreparedListener { mp ->
                        mediaPlayerReady = true
                        try { mp.start() } catch (_: Exception) { }
                    }
                    setOnErrorListener { _, _, _ ->
                        startMediaPlayer(null)
                        true
                    }
                    prepareAsync()
                }
                return
            } catch (_: Exception) { }
        }

        // Fall back to system default alarm tone
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { mp ->
                    mediaPlayerReady = true
                    try { mp.start() } catch (_: Exception) { }
                }
                prepareAsync()
            }
        } catch (_: Exception) { }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, AlarmService::class.java)
        // FLAG_UPDATE_CURRENT so the PendingIntent remains reusable across multiple task-removed events.
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (volumeObserverRegistered) {
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeObserverRegistered = false
        }
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
        } catch (_: Exception) { }
        mediaPlayer?.release()
        mediaPlayer = null
        mediaPlayerReady = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .edit { remove("last_ringtone_path") }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
