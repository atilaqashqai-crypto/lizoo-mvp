package ir.lizoo.courier.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.lizoo.courier.MainActivity
import ir.lizoo.courier.data.ActivityDto
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.Graph
import ir.lizoo.courier.data.PointDto
import ir.lizoo.courier.ui.errorText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TrackingService : Service() {

    companion object {
        const val ACTION_START = "ir.lizoo.courier.START"
        const val ACTION_STOP = "ir.lizoo.courier.STOP"
        const val EXTRA_ID = "activity_id"

        private const val CHANNEL_TRACK = "tracking"
        private const val CHANNEL_ALERT = "alerts"
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        private const val LOCATION_INTERVAL_MS = 10_000L
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val MAX_BUFFER = 300
        private const val NET_MSG = "اتصال اینترنت برقرار نیست؛ ردیابی ادامه دارد"

        fun start(ctx: Context, activityId: Long) {
            val i = Intent(ctx, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, activityId)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val buffer = ArrayList<PointDto>()
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private var activityId = 0L
    private var lastFlush = 0L
    private var flushing = false
    private var stopping = false
    private var listening = false

    private val tokens by lazy { Graph.tokens(this) }
    private val repo by lazy { Graph.repo(this) }
    private val locationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) = setMessage(null)
        override fun onProviderDisabled(provider: String) = setMessage("GPS خاموش است")

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (activityId > 0) stopTracking() else stopSelf()
            return START_STICKY
        }

        if (!startAsForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val fromIntent = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        val target = if (fromIntent > 0) fromIntent else tokens.activeActivityId
        if (target <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (target == activityId) return START_STICKY

        activityId = target
        tokens.activeActivityId = target
        stopping = false
        flushing = false
        lastFlush = 0L
        buffer.clear()

        val cur = TrackingState.flow.value
        TrackingState.flow.value =
            if (cur.running && cur.activityId == target) {
                cur.copy(stopping = false, finishedReason = null, message = null)
            } else {
                TrackSnapshot(
                    running = true,
                    activityId = target,
                    updatedAtMs = System.currentTimeMillis()
                )
            }

        if (!startLocation()) {
            finish(null, "error", "مجوز موقعیت یا GPS در دسترس نیست")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // ---------- Location ----------

    @SuppressLint("MissingPermission")
    private fun startLocation(): Boolean {
        if (listening) return true
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper()
            )
            listening = true
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopLocation() {
        if (listening) {
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
            }
            listening = false
        }
    }

    private fun onLocation(loc: Location) {
        if (stopping || activityId <= 0) return
        if (loc.hasAccuracy() && loc.accuracy > 100f) return

        buffer.add(
            PointDto(
                lat = loc.latitude,
                lng = loc.longitude,
                accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                recordedAt = isoFormat.format(Date(loc.time))
            )
        )
        if (buffer.size > MAX_BUFFER) buffer.removeAt(0)

        if (SystemClock.elapsedRealtime() - lastFlush >= FLUSH_INTERVAL_MS) flush()
    }

    // ---------- Sync with server ----------

    private fun removeSent(n: Int) {
        buffer.subList(0, minOf(n, buffer.size)).clear()
    }

    private fun flush() {
        if (flushing || stopping || buffer.isEmpty() || activityId <= 0) return
        flushing = true
        lastFlush = SystemClock.elapsedRealtime()
        val batch = buffer.take(50)
        val id = activityId

        scope.launch {
            val r = repo.sendPoints(id, batch)
            flushing = false
            if (stopping || id != activityId) return@launch

            when (r) {
                is ApiResult.Ok -> {
                    removeSent(batch.size)
                    handleActivity(r.data.activity)
                    if (r.data.autoStopped) finish(r.data.activity, "auto_idle")
                }
                is ApiResult.Fail -> when (r.error) {
                    "not_active", "not_found" -> finish(null, "ended")
                    "unauthorized" -> finish(null, "error", "نشست منقضی شده؛ دوباره وارد شو")
                    "network" -> setMessage(NET_MSG)
                    else -> {}
                }
            }
        }
    }

    private fun stopTracking() {
        if (stopping || activityId <= 0) return
        stopping = true
        TrackingState.flow.update { it.copy(stopping = true) }
        val id = activityId

        scope.launch {
            var guard = 0
            while (buffer.isNotEmpty() && guard++ < 6) {
                val batch = buffer.take(50)
                val r = repo.sendPoints(id, batch)
                if (r is ApiResult.Ok) {
                    removeSent(batch.size)
                    if (r.data.autoStopped) {
                        finish(r.data.activity, "auto_idle")
                        return@launch
                    }
                } else if (r is ApiResult.Fail &&
                    (r.error == "not_active" || r.error == "not_found")
                ) {
                    finish(null, "ended")
                    return@launch
                } else {
                    break
                }
            }

            when (val r = repo.stopActivity(id)) {
                is ApiResult.Ok -> finish(r.data.activity, "manual")
                is ApiResult.Fail -> when (r.error) {
                    "not_found" -> finish(null, "ended")
                    "unauthorized" -> finish(null, "error", "نشست منقضی شده؛ دوباره وارد شو")
                    else -> {
                        stopping = false
                        TrackingState.flow.update {
                            it.copy(
                                stopping = false,
                                message = if (r.error == "network") {
                                    "اتصال برقرار نیست؛ دوباره «پایان فعالیت» را بزن"
                                } else {
                                    errorText(r.error)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleActivity(a: ActivityDto?) {
        if (a == null) return
        TrackingState.flow.update {
            it.copy(
                durationSeconds = a.durationSeconds,
                earningsToman = a.earningsToman,
                updatedAtMs = System.currentTimeMillis(),
                message = if (it.message == NET_MSG) null else it.message
            )
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("درآمد تا الان: ${"%,d".format(a.earningsToman)} تومان"))
    }

    private fun setMessage(msg: String?) {
        TrackingState.flow.update { it.copy(message = msg) }
    }

    private fun finish(a: ActivityDto?, reason: String, message: String? = null) {
        stopLocation()
        tokens.activeActivityId = 0L
        val cur = TrackingState.flow.value
        TrackingState.flow.value = TrackSnapshot(
            running = false,
            activityId = activityId,
            durationSeconds = a?.durationSeconds ?: cur.durationSeconds,
            earningsToman = a?.earningsToman ?: cur.earningsToman,
            updatedAtMs = System.currentTimeMillis(),
            finishedReason = reason,
            message = message
        )
        if (reason == "auto_idle") {
            notifyAlert("فعالیت به‌دلیل ثابت‌ماندن موتور به‌صورت خودکار قطع شد")
        }
        activityId = 0L
        stopping = false
        flushing = false
        buffer.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopLocation()
        if (activityId > 0) {
            TrackingState.flow.update {
                it.copy(running = false, stopping = false, message = "ردیابی متوقف شد")
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Notifications ----------

    private fun startAsForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        false
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACK, "ثبت فعالیت", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "اعلان‌ها", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(text: String = "در حال ثبت فعالیت و موقعیت…"): Notification {
        ensureChannels()
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_TRACK)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("لیزو")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "پایان فعالیت", stopIntent)
            .build()
    }

    private fun notifyAlert(text: String) {
        ensureChannels()
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("لیزو")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_ID, n)
    }
}
