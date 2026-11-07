package ir.lizoo.courier.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class RequestOtpBody(val phone: String)

data class OtpResponse(
    val ok: Boolean,
    @SerializedName("expires_in") val expiresIn: Int
)

data class VerifyBody(val phone: String, val code: String)

data class UserDto(
    val id: Long,
    val phone: String,
    @SerializedName("full_name") val fullName: String?,
    val city: String?,
    @SerializedName("vehicle_plate") val vehiclePlate: String?,
    val role: String,
    val status: String,
    @SerializedName("profile_complete") val profileComplete: Boolean
)

data class AuthResponse(val token: String, val user: UserDto)

data class UserResponse(val user: UserDto)

data class ProfileBody(
    @SerializedName("full_name") val fullName: String,
    val city: String,
    @SerializedName("vehicle_plate") val vehiclePlate: String
)

data class ErrorBody(
    val error: String?,
    @SerializedName("retry_after") val retryAfter: Int?
)

data class ActivityDto(
    val id: Long,
    val status: String,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("end_reason") val endReason: String?,
    @SerializedName("duration_seconds") val durationSeconds: Long,
    @SerializedName("earnings_toman") val earningsToman: Long
)

data class ActivityResponse(val activity: ActivityDto?)

data class PointDto(
    val lat: Double,
    val lng: Double,
    val accuracy: Double?,
    @SerializedName("recorded_at") val recordedAt: String
)

data class PointsBody(val points: List<PointDto>)

data class PointsResponse(
    val activity: ActivityDto?,
    @SerializedName("auto_stopped") val autoStopped: Boolean
)

data class SummaryDto(
    @SerializedName("today_toman") val todayToman: Long,
    @SerializedName("total_toman") val totalToman: Long,
    @SerializedName("available_toman") val availableToman: Long,
    @SerializedName("in_process_toman") val inProcessToman: Long,
    @SerializedName("paid_toman") val paidToman: Long,
    @SerializedName("min_settlement_toman") val minSettlementToman: Long
)

data class SettlementDto(
    val id: Long,
    @SerializedName("amount_toman") val amountToman: Long,
    val status: String,
    val note: String?,
    @SerializedName("requested_at") val requestedAt: String?,
    @SerializedName("reviewed_at") val reviewedAt: String?
)

data class SettlementsResponse(val settlements: List<SettlementDto>)

data class SettlementResponse(val settlement: SettlementDto)

// amount = null -> whole available balance (Gson omits null fields)
class SettlementRequestBody(val amount: Long? = null)

interface LizooApi {
    @POST("auth/request-otp")
    suspend fun requestOtp(@Body body: RequestOtpBody): Response<OtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyBody): Response<AuthResponse>

    @GET("users/me")
    suspend fun me(): Response<UserResponse>

    @PUT("users/me/profile")
    suspend fun updateProfile(@Body body: ProfileBody): Response<UserResponse>

    @POST("activities/start")
    suspend fun startActivity(): Response<ActivityResponse>

    @GET("activities/active")
    suspend fun activeActivity(): Response<ActivityResponse>

    @POST("activities/{id}/points")
    suspend fun sendPoints(
        @Path("id") id: Long,
        @Body body: PointsBody
    ): Response<PointsResponse>

    @POST("activities/{id}/stop")
    suspend fun stopActivity(@Path("id") id: Long): Response<ActivityResponse>

    @GET("earnings/summary")
    suspend fun summary(): Response<SummaryDto>

    @GET("settlements/mine")
    suspend fun mySettlements(): Response<SettlementsResponse>

    @POST("settlements/request")
    suspend fun requestSettlement(
        @Body body: SettlementRequestBody
    ): Response<SettlementResponse>
}
