package app.sawatt.workout

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.sawatt.AppLanguage
import app.sawatt.MainActivity
import app.sawatt.R
import app.sawatt.SawattApp
import app.sawatt.ui.formatDuration
import app.sawatt.ui.intervalName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (BLE link + 1 Hz workout clock) alive while a
 * workout runs with the screen locked. The workout logic itself lives in [WorkoutController].
 */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.localized(base))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel_workout), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, build(getString(R.string.workout_notification_running)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            // Missing Bluetooth permission on Android 14+: the workout still runs in the foreground UI.
            Log.w("WorkoutService", "Cannot start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        val controller = (application as SawattApp).container.workout
        scope.launch {
            controller.state
                .map { s ->
                    val c = s.live?.current ?: return@map getString(R.string.workout_notification_running)
                    val time = c.remainingSec?.let { getString(R.string.workout_notification_left, formatDuration(it)) }
                        ?: formatDuration(c.elapsedSec)
                    getString(R.string.workout_notification_text, intervalName(this@WorkoutService, c.name, c.kind), time, c.stats.avgPowerW.toInt())
                }
                .distinctUntilChanged()
                .collect { manager.notify(NOTIFICATION_ID, build(it)) }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            // A connectedDevice foreground service needs a Bluetooth permission on Android 14+;
            // starting it without one would crash. The workout then simply runs in the foreground UI.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (e: Exception) {
                Log.w("WorkoutService", "Cannot start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
