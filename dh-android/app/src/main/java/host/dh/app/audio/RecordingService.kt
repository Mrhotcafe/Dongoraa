package host.dh.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps microphone capture alive and compliant while
 * the app is backgrounded. Capture itself lives in [RecorderController]; this
 * service provides the required foreground notification + microphone type.
 */
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            mgr.createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat()
                RecorderController.beginCapture()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("DH Recorder")
            .setContentText("Recording audio…")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "host.dh.app.START"
        const val ACTION_STOP = "host.dh.app.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 4201
    }
}
