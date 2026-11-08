package ir.lizoo.courier.ui

import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone

internal fun fmtDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

internal fun fmtMoney(n: Long): String =
    NumberFormat.getInstance(Locale("fa", "IR")).format(n)

// Server sends UTC ISO time; show it as a Jalali date
internal fun fmtDate(iso: String?): String {
    if (iso == null || iso.length < 19) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.substring(0, 19)) ?: return iso.substring(0, 10)
        val out = android.icu.text.SimpleDateFormat(
            "yyyy/MM/dd",
            android.icu.util.ULocale("fa_IR@calendar=persian")
        )
        out.format(date)
    } catch (e: Exception) {
        iso.substring(0, 10)
    }
}

// "2026-11-08" (already Tehran day) -> Jalali "MM/dd"
internal fun fmtDay(day: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(day) ?: return day
        val out = android.icu.text.SimpleDateFormat(
            "MM/dd",
            android.icu.util.ULocale("fa_IR@calendar=persian")
        )
        out.format(date)
    } catch (e: Exception) {
        day
    }
}

internal fun settlementStatusLabel(status: String): String = when (status) {
    "pending" -> "در انتظار بررسی"
    "approved" -> "تأیید شد، منتظر واریز"
    "paid" -> "پرداخت شد"
    "rejected" -> "رد شد"
    else -> status
}

internal fun adStatusLabel(status: String): String = when (status) {
    "pending" -> "در انتظار تأیید پرداخت"
    "confirmed" -> "تأیید شد"
    "rejected" -> "رد شد"
    else -> status
}

internal fun locationLabel(type: String): String = when (type) {
    "busy" -> "منطقه پرتردد"
    "normal" -> "منطقه معمولی"
    "low" -> "منطقه کم‌تردد"
    else -> type
}
