package ir.lizoo.courier.data

import android.content.Context

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("lizoo", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove("token")
                    remove("active_id")
                } else {
                    putString("token", value)
                }
            }.apply()
        }

    var activeActivityId: Long
        get() = prefs.getLong("active_id", 0L)
        set(value) {
            prefs.edit().putLong("active_id", value).apply()
        }
}
