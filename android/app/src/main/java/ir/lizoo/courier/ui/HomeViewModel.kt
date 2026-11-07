package ir.lizoo.courier.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.lizoo.courier.data.ActivityDto
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.Graph
import ir.lizoo.courier.tracking.TrackSnapshot
import ir.lizoo.courier.tracking.TrackingService
import ir.lizoo.courier.tracking.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null
)

fun homeError(code: String): String = when (code) {
    "not_approved" -> "حساب تو هنوز تأیید نشده است"
    "already_active" -> "یک فعالیت باز داری"
    else -> errorText(code)
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val repo = Graph.repo(ctx)

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()
    val track = TrackingState.flow

    init {
        sync()
    }

    private fun hasFine() = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun gpsOn() = (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun setError(msg: String) =
        _ui.update { it.copy(loading = false, busy = false, error = msg) }

    // On open: if the server has an open activity and tracking isn't running, resume it
    fun sync() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.activeActivity()) {
                is ApiResult.Ok -> {
                    val a = r.data.activity
                    if (a != null && !track.value.running && hasFine() && gpsOn()) resume(a)
                    _ui.update { it.copy(loading = false) }
                }
                is ApiResult.Fail -> setError(homeError(r.error))
            }
        }
    }

    private fun resume(a: ActivityDto) {
        TrackingState.flow.value = TrackSnapshot(
            running = true,
            activityId = a.id,
            durationSeconds = a.durationSeconds,
            earningsToman = a.earningsToman,
            updatedAtMs = System.currentTimeMillis()
        )
        launchService(a.id)
    }

    private fun launchService(id: Long) {
        try {
            TrackingService.start(ctx, id)
            _ui.update { it.copy(busy = false, error = null) }
        } catch (e: Exception) {
            TrackingState.flow.value = TrackSnapshot()
            setError("اجرای سرویس ردیابی ممکن نشد")
        }
    }

    fun start() {
        if (_ui.value.busy) return
        if (!hasFine()) return setError("مجوز موقعیت دقیق لازم است")
        if (!gpsOn()) return setError("GPS گوشی را روشن کن")

        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.startActivity()) {
                is ApiResult.Ok -> {
                    val a = r.data.activity
                    if (a == null) {
                        setError(errorText("server_error"))
                    } else {
                        TrackingState.flow.value = TrackSnapshot(
                            running = true,
                            activityId = a.id,
                            updatedAtMs = System.currentTimeMillis()
                        )
                        launchService(a.id)
                    }
                }
                is ApiResult.Fail -> {
                    if (r.error == "already_active") {
                        when (val c = repo.activeActivity()) {
                            is ApiResult.Ok -> {
                                val a = c.data.activity
                                if (a != null) resume(a) else setError(homeError("already_active"))
                            }
                            is ApiResult.Fail -> setError(homeError(c.error))
                        }
                    } else {
                        setError(homeError(r.error))
                    }
                }
            }
        }
    }

    fun stop() {
        if (track.value.running) TrackingService.stop(ctx)
    }

    fun permissionDenied() = setError("بدون مجوز موقعیت دقیق نمی‌شود فعالیت را شروع کرد")
}
