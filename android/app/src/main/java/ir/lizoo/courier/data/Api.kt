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
    val username: String?,
    @SerializedName("has_bank") val hasBank: Boolean,
    @SerializedName("has_id_doc") val hasIdDoc: Boolean,
    @SerializedName("sheba_last4") val shebaLast4: String?,
    @SerializedName("card_last4") val cardLast4: String?,
    val role: String,
    val status: String,
    @SerializedName("profile_complete") val profileComplete: Boolean
)

data class AuthResponse(val token: String, val user: UserDto)

data class UserResponse(val user: UserDto)

data class ProfileBody(
    @SerializedName("full_name") val fullName: String,
    val city: String,
    @SerializedName("vehicle_plate") val vehiclePlate: String,
    val username: String? = null
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

data class BankBody(
    val sheba: String,
    @SerializedName("card_number") val cardNumber: String
)

data class DocBody(@SerializedName("image_base64") val imageBase64: String)

data class DayDto(val day: String, val toman: Long, val seconds: Long)

data class WeekDto(
    val days: List<DayDto>,
    @SerializedName("total_toman") val totalToman: Long,
    @SerializedName("total_seconds") val totalSeconds: Long
)

data class AdPricesDto(
    val prices: Map<String, Long>,
    @SerializedName("support_whatsapp") val supportWhatsapp: String?,
    @SerializedName("payment_info") val paymentInfo: String?
)

data class AdOrderBody(
    @SerializedName("advertiser_name") val advertiserName: String,
    @SerializedName("contact_phone") val contactPhone: String,
    @SerializedName("boxes_count") val boxesCount: Int,
    @SerializedName("location_type") val locationType: String
)

data class AdOrderDto(
    val id: Long,
    @SerializedName("advertiser_name") val advertiserName: String,
    @SerializedName("boxes_count") val boxesCount: Int,
    @SerializedName("location_type") val locationType: String,
    @SerializedName("total_price_toman") val totalPriceToman: Long,
    val status: String,
    val note: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class AdOrderResponse(
    val order: AdOrderDto,
    @SerializedName("support_whatsapp") val supportWhatsapp: String?,
    @SerializedName("payment_info") val paymentInfo: String?
)

data class AdOrdersResponse(val orders: List<AdOrderDto>)

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

    @PUT("users/me/bank")
    suspend fun saveBank(@Body body: BankBody): Response<UserResponse>

    @POST("users/me/id-document")
    suspend fun uploadDoc(@Body body: DocBody): Response<UserResponse>

    @GET("earnings/week")
    suspend fun week(): Response<WeekDto>

    @GET("ads/prices")
    suspend fun adPrices(): Response<AdPricesDto>

    @POST("ads/orders")
    suspend fun createAd(@Body body: AdOrderBody): Response<AdOrderResponse>

    @GET("ads/orders/mine")
    suspend fun myAds(): Response<AdOrdersResponse>
}
