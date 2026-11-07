package ir.lizoo.courier.tracking

import kotlinx.coroutines.flow.MutableStateFlow

data class TrackSnapshot(
    val running: Boolean = false,
    val stopping: Boolean = false,
    val activityId: Long = 0L,
    val durationSeconds: Long = 0L,
    val earningsToman: Long = 0L,
    val updatedAtMs: Long = 0L,
    val finishedReason: String? = null, // manual | auto_idle | ended | error
    val message: String? = null
)

object TrackingState {
    val flow = MutableStateFlow(TrackSnapshot())
}
