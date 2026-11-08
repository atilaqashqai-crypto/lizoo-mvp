package ir.lizoo.courier.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.Graph
import ir.lizoo.courier.data.UserDto
import ir.lizoo.courier.data.UserResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Stage { Loading, Phone, Otp, Main, Profile }

data class UiState(
    val stage: Stage = Stage.Loading,
    val phone: String = "",
    val code: String = "",
    val fullName: String = "",
    val city: String = "",
    val plate: String = "",
    val username: String = "",
    val info: String? = null,
    val user: UserDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val resendIn: Int = 0
)

private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"

private fun String.toLatinDigits(): String = map { c ->
    val p = PERSIAN.indexOf(c)
    val a = ARABIC.indexOf(c)
    when {
        p >= 0 -> '0' + p
        a >= 0 -> '0' + a
        else -> c
    }
}.joinToString("")

fun errorText(code: String): String = when (code) {
    "network" -> "اتصال اینترنت برقرار نیست"
    "invalid_phone" -> "شماره موبایل معتبر نیست"
    "too_soon" -> "کمی صبر کن و دوباره تلاش کن"
    "rate_limited" -> "تعداد تلاش‌ها زیاد بود، چند دقیقه بعد امتحان کن"
    "otp_expired" -> "کد منقضی شده، کد جدید بگیر"
    "wrong_code" -> "کد اشتباه است"
    "too_many_attempts" -> "تلاش‌ها زیاد بود، کد جدید بگیر"
    "invalid_input" -> "اطلاعات واردشده معتبر نیست"
    "username_taken" -> "این نام کاربری قبلاً گرفته شده"
    "invalid_sheba" -> "شماره شبا معتبر نیست"
    "invalid_card" -> "شماره کارت معتبر نیست"
    "invalid_image" -> "عکس معتبر نیست، یک عکس دیگر انتخاب کن"
    "price_not_set" -> "قیمت این بخش هنوز تعیین نشده"
    "id_document_required" -> "اول عکس کارت ملی یا شناسنامه را در حساب کاربری آپلود کن"
    "bank_info_required" -> "اول شماره شبا و کارت را در حساب کاربری ثبت کن"
    else -> "خطای سرور، دوباره تلاش کن"
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Graph.repo(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var timer: Job? = null

    init {
        bootstrap()
    }

    fun bootstrap() {
        if (!repo.hasToken()) {
            _state.update { it.copy(stage = Stage.Phone, busy = false, error = null) }
            return
        }
        _state.update { it.copy(stage = Stage.Loading, busy = true, error = null) }
        viewModelScope.launch { handleMe(repo.me()) }
    }

    fun refresh() {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch { handleMe(repo.me()) }
    }

    private fun handleMe(r: ApiResult<UserResponse>) {
        when (r) {
            is ApiResult.Ok -> routeUser(r.data.user)
            is ApiResult.Fail -> {
                if (r.error == "unauthorized") {
                    repo.logout()
                    _state.value = UiState(stage = Stage.Phone)
                } else {
                    _state.update { it.copy(busy = false, error = errorText(r.error)) }
                }
            }
        }
    }

    private fun routeUser(u: UserDto) {
        timer?.cancel()
        _state.update {
            it.copy(
                stage = Stage.Main,
                user = u,
                username = u.username.orEmpty(),
                fullName = u.fullName.orEmpty(),
                city = u.city.orEmpty(),
                plate = u.vehiclePlate.orEmpty(),
                busy = false,
                error = null
            )
        }
    }

    private fun setError(msg: String) = _state.update { it.copy(error = msg, busy = false) }

    fun onPhone(v: String) = _state.update {
        it.copy(phone = v.toLatinDigits().filter { c -> c.isDigit() || c == '+' }.take(14))
    }

    fun onCode(v: String) = _state.update {
        it.copy(code = v.toLatinDigits().filter { c -> c.isDigit() }.take(5))
    }

    fun onUsername(v: String) = _state.update {
        it.copy(username = v.filter { c -> c.isLetterOrDigit() && c.code < 128 || c == '_' || c == '.' }.take(30))
    }

    fun onName(v: String) = _state.update { it.copy(fullName = v.take(120)) }
    fun onCity(v: String) = _state.update { it.copy(city = v.take(80)) }
    fun onPlate(v: String) = _state.update { it.copy(plate = v.take(30)) }

    fun sendOtp() {
        val phone = _state.value.phone.trim()
        if (phone.length < 10) {
            setError(errorText("invalid_phone"))
            return
        }
        requestOtp(phone)
    }

    fun resendOtp() {
        if (_state.value.resendIn > 0) return
        requestOtp(_state.value.phone.trim())
    }

    private fun requestOtp(phone: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestOtp(phone)) {
                is ApiResult.Ok -> goOtp(60)
                is ApiResult.Fail -> {
                    if (r.error == "too_soon" && r.retryAfter != null) {
                        goOtp(r.retryAfter)
                    } else {
                        setError(errorText(r.error))
                    }
                }
            }
        }
    }

    private fun goOtp(seconds: Int) {
        _state.update { it.copy(stage = Stage.Otp, code = "", busy = false, error = null) }
        timer?.cancel()
        timer = viewModelScope.launch {
            for (i in seconds downTo 0) {
                _state.update { it.copy(resendIn = i) }
                delay(1000)
            }
        }
    }

    fun verify() {
        val s = _state.value
        if (s.code.length != 5) {
            setError("کد پنج‌رقمی را وارد کن")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.verifyOtp(s.phone.trim(), s.code)) {
                is ApiResult.Ok -> routeUser(r.data.user)
                is ApiResult.Fail -> setError(errorText(r.error))
            }
        }
    }

    fun saveProfile() {
        val s = _state.value
        if (s.fullName.trim().length < 3 || s.city.trim().length < 2 || s.plate.trim().length < 3) {
            setError("همه فیلدها را کامل وارد کن")
            return
        }
        val uname = s.username.trim()
        if (uname.isNotEmpty() && uname.length < 3) {
            setError("نام کاربری حداقل ۳ حرف انگلیسی یا عدد باشد")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.updateProfile(
                s.fullName.trim(), s.city.trim(), s.plate.trim(), uname.ifEmpty { null }
            )) {
                is ApiResult.Ok -> routeUser(r.data.user)
                is ApiResult.Fail -> {
                    if (r.error == "unauthorized") handleMe(r) else setError(errorText(r.error))
                }
            }
        }
    }

    fun editProfile() = _state.update { it.copy(stage = Stage.Profile, error = null, info = null) }

    fun cancelEdit() = _state.update { it.copy(stage = Stage.Main, error = null) }

    private fun setUser(u: UserDto, info: String) = _state.update {
        it.copy(user = u, busy = false, error = null, info = info)
    }

    fun saveBank(sheba: String, card: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            when (val r = repo.saveBank(sheba.trim(), card.trim())) {
                is ApiResult.Ok -> setUser(r.data.user, "اطلاعات بانکی ذخیره شد")
                is ApiResult.Fail -> setError(errorText(r.error))
            }
        }
    }

    fun uploadDoc(uri: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val b64 = withContext(Dispatchers.IO) {
                try {
                    compressImage(uri)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                } catch (e: Exception) {
                    null
                }
            }
            if (b64 == null) {
                setError(errorText("invalid_image"))
                return@launch
            }
            when (val r = repo.uploadDoc(b64)) {
                is ApiResult.Ok -> setUser(r.data.user, "مدرک آپلود شد")
                is ApiResult.Fail -> setError(errorText(r.error))
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        return out.toByteArray()
    }

    fun backToPhone() {
        timer?.cancel()
        _state.update { it.copy(stage = Stage.Phone, code = "", error = null, busy = false) }
    }

    fun logout() {
        timer?.cancel()
        repo.logout()
        _state.value = UiState(stage = Stage.Phone)
    }
}
