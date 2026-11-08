package ir.lizoo.courier.data

import com.google.gson.Gson
import ir.lizoo.courier.BuildConfig
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Fail(val error: String, val retryAfter: Int? = null) : ApiResult<Nothing>()
}

class Repository(private val tokens: TokenStore) {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            tokens.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: LizooApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LizooApi::class.java)

    private val gson = Gson()

    fun hasToken(): Boolean = tokens.token != null

    fun logout() {
        tokens.token = null
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val r = block()
            val body = r.body()
            if (r.isSuccessful && body != null) {
                ApiResult.Ok(body)
            } else {
                val err = try {
                    gson.fromJson(r.errorBody()?.string(), ErrorBody::class.java)
                } catch (e: Exception) {
                    null
                }
                val code = when {
                    r.code() == 401 -> "unauthorized"
                    err?.error != null -> err.error
                    r.code() == 429 -> "rate_limited"
                    else -> "server_error"
                }
                ApiResult.Fail(code, err?.retryAfter)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.Fail("network")
        } catch (e: Exception) {
            ApiResult.Fail("server_error")
        }
    }

    suspend fun requestOtp(phone: String): ApiResult<OtpResponse> =
        call { api.requestOtp(RequestOtpBody(phone)) }

    suspend fun verifyOtp(phone: String, code: String): ApiResult<AuthResponse> {
        val r = call { api.verifyOtp(VerifyBody(phone, code)) }
        if (r is ApiResult.Ok) tokens.token = r.data.token
        return r
    }

    suspend fun me(): ApiResult<UserResponse> = call { api.me() }

    suspend fun updateProfile(
        name: String,
        city: String,
        plate: String,
        username: String?
    ): ApiResult<UserResponse> =
        call { api.updateProfile(ProfileBody(name, city, plate, username)) }

    suspend fun saveBank(sheba: String, card: String): ApiResult<UserResponse> =
        call { api.saveBank(BankBody(sheba, card)) }

    suspend fun uploadDoc(base64: String): ApiResult<UserResponse> =
        call { api.uploadDoc(DocBody(base64)) }

    suspend fun week(): ApiResult<WeekDto> = call { api.week() }

    suspend fun adPrices(): ApiResult<AdPricesDto> = call { api.adPrices() }

    suspend fun createAd(
        name: String,
        phone: String,
        boxes: Int,
        type: String
    ): ApiResult<AdOrderResponse> =
        call { api.createAd(AdOrderBody(name, phone, boxes, type)) }

    suspend fun myAds(): ApiResult<AdOrdersResponse> = call { api.myAds() }

    suspend fun startActivity(): ApiResult<ActivityResponse> =
        call { api.startActivity() }

    suspend fun activeActivity(): ApiResult<ActivityResponse> =
        call { api.activeActivity() }

    suspend fun sendPoints(id: Long, points: List<PointDto>): ApiResult<PointsResponse> =
        call { api.sendPoints(id, PointsBody(points)) }

    suspend fun stopActivity(id: Long): ApiResult<ActivityResponse> =
        call { api.stopActivity(id) }

    suspend fun summary(): ApiResult<SummaryDto> =
        call { api.summary() }

    suspend fun mySettlements(): ApiResult<SettlementsResponse> =
        call { api.mySettlements() }

    suspend fun requestSettlement(amount: Long?): ApiResult<SettlementResponse> =
        call { api.requestSettlement(SettlementRequestBody(amount)) }
}
