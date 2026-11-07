package ir.lizoo.courier.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.lizoo.courier.data.SettlementDto
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone

private fun fmtDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun fmtMoney(n: Long): String =
    NumberFormat.getInstance(Locale("fa", "IR")).format(n)

// Server sends UTC ISO time; show it as a Jalali date
private fun fmtDate(iso: String?): String {
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

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "در انتظار بررسی"
    "approved" -> "تأیید شد، منتظر واریز"
    "paid" -> "پرداخت شد"
    "rejected" -> "رد شد"
    else -> status
}

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    vm: HomeViewModel = viewModel(),
    ev: EarningsViewModel = viewModel()
) {
    val ui by vm.ui.collectAsState()
    val t by vm.track.collectAsState()
    val eu by ev.ui.collectAsState()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(t.running) {
        while (t.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // refresh balances whenever tracking is not running (opening the app, after an activity ends)
    LaunchedEffect(t.running) {
        if (!t.running) ev.refresh()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.start()
        else vm.permissionDenied()
    }

    fun onStartClick() {
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            vm.start()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(perms.toTypedArray())
        }
    }

    val summary = eu.summary
    if (confirm && summary != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("درخواست تسویه") },
            text = {
                Text("مبلغ ${fmtMoney(summary.availableToman)} تومان برای تسویه ثبت شود؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    ev.request()
                }) { Text("ثبت درخواست") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("انصراف") }
            }
        )
    }

    Page("لیزو") {
        if (ui.loading) {
            CircularProgressIndicator()
            return@Page
        }

        if (t.running) {
            val elapsed = t.durationSeconds + ((now - t.updatedAtMs) / 1000).coerceAtLeast(0)
            Text("فعالیت در حال انجام است", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(fmtDuration(elapsed), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text("درآمد تا الان: ${fmtMoney(t.earningsToman)} تومان")
            Spacer(Modifier.height(24.dp))
            PrimaryButton("پایان فعالیت", t.stopping) { vm.stop() }
            if (t.stopping) {
                Spacer(Modifier.height(8.dp))
                Text("در حال پایان دادن…")
            }
        } else {
            when (t.finishedReason) {
                "manual" -> Text("فعالیت پایان یافت.", fontWeight = FontWeight.Bold)
                "auto_idle" -> Text(
                    "فعالیت به‌دلیل ثابت‌ماندن موتور قطع شد.",
                    fontWeight = FontWeight.Bold
                )
                "ended" -> Text("این فعالیت در سرور بسته شده است.", fontWeight = FontWeight.Bold)
                else -> {}
            }
            if (t.finishedReason == "manual" || t.finishedReason == "auto_idle") {
                Spacer(Modifier.height(8.dp))
                Text("مدت: ${fmtDuration(t.durationSeconds)}")
                Text("درآمد: ${fmtMoney(t.earningsToman)} تومان")
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("شروع فعالیت", ui.busy) { onStartClick() }
            Spacer(Modifier.height(8.dp))
            Text("برای ثبت درست درآمد، GPS را روشن نگه دار.")
        }

        ErrorLine(ui.error)
        t.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (!t.running) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            EarningsSection(eu, onRequest = { confirm = true })
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLogout) { Text("خروج از حساب") }
        }
    }
}

@Composable
private fun EarningsSection(eu: EarningsUi, onRequest: () -> Unit) {
    val s = eu.summary
    if (s == null) {
        if (eu.loading) CircularProgressIndicator() else ErrorLine(eu.error)
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("درآمد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("امروز: ${fmtMoney(s.todayToman)} تومان")
        Text("کل: ${fmtMoney(s.totalToman)} تومان")
        Spacer(Modifier.height(4.dp))
        Text(
            "قابل برداشت: ${fmtMoney(s.availableToman)} تومان",
            fontWeight = FontWeight.Bold
        )
        if (s.inProcessToman > 0) {
            Text("در حال تسویه: ${fmtMoney(s.inProcessToman)} تومان")
        }
        if (s.paidToman > 0) {
            Text("پرداخت‌شده: ${fmtMoney(s.paidToman)} تومان")
        }

        Spacer(Modifier.height(12.dp))
        val canRequest = s.inProcessToman == 0L && s.availableToman >= s.minSettlementToman
        PrimaryButton("درخواست تسویه", eu.busy || !canRequest) { onRequest() }
        Spacer(Modifier.height(4.dp))
        when {
            s.inProcessToman > 0 -> Text("یک درخواست تسویه در جریان داری.")
            s.availableToman < s.minSettlementToman ->
                Text("حداقل مبلغ تسویه ${fmtMoney(s.minSettlementToman)} تومان است.")
            else -> {}
        }
        eu.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ErrorLine(eu.error)

        if (eu.settlements.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "درخواست‌های تسویه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            eu.settlements.forEach { SettlementRow(it) }
        }
    }
}

@Composable
private fun SettlementRow(st: SettlementDto) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text("${fmtMoney(st.amountToman)} تومان | ${statusLabel(st.status)}")
        val date = fmtDate(st.requestedAt)
        if (date.isNotEmpty()) Text(date, style = MaterialTheme.typography.bodySmall)
        if (st.status == "rejected" && !st.note.isNullOrBlank()) {
            Text("دلیل: ${st.note}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
    }
}
