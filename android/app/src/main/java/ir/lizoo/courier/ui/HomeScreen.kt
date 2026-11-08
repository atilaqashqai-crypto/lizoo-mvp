package ir.lizoo.courier.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.lizoo.courier.data.SettlementDto
import ir.lizoo.courier.data.UserDto
import ir.lizoo.courier.data.WeekDto
import kotlinx.coroutines.delay

private fun gpsOn(ctx: Context): Boolean =
    (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER)

@Composable
fun HomeScreen(
    user: UserDto,
    onBack: () -> Unit,
    onGoAccount: () -> Unit,
    vm: HomeViewModel = viewModel(),
    ev: EarningsViewModel = viewModel()
) {
    val ui by vm.ui.collectAsState()
    val t by vm.track.collectAsState()
    val eu by ev.ui.collectAsState()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    var gpsDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }

    LaunchedEffect(t.running) {
        while (t.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // refresh balances whenever tracking is not running (opening the screen, after an activity ends)
    LaunchedEffect(t.running) {
        if (!t.running) ev.refresh()
    }

    // pre-fill the settlement amount with the whole available balance
    LaunchedEffect(eu.summary?.availableToman) {
        val a = eu.summary?.availableToman
        if (a != null && a > 0 && amountText.isEmpty()) amountText = a.toString()
    }

    fun startChecked() {
        if (!gpsOn(ctx)) gpsDialog = true else vm.start()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) startChecked()
        else vm.permissionDenied()
    }

    fun onStartClick() {
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            startChecked()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(perms.toTypedArray())
        }
    }

    if (gpsDialog) {
        AlertDialog(
            onDismissRequest = { gpsDialog = false },
            title = { Text("GPS خاموش است") },
            text = {
                Text("برای ثبت درآمد باید GPS گوشی روشن باشد. به تنظیمات برو، «موقعیت مکانی» را روشن کن و برگرد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    gpsDialog = false
                    ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text("باز کردن تنظیمات") }
            },
            dismissButton = {
                TextButton(onClick = { gpsDialog = false }) { Text("انصراف") }
            }
        )
    }

    val summary = eu.summary
    val amount = amountText.toLongOrNull()
    if (confirm && summary != null && amount != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("درخواست تسویه") },
            text = { Text("مبلغ ${fmtMoney(amount)} تومان برای تسویه ثبت شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    ev.request(amount)
                }) { Text("ثبت درخواست") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("انصراف") }
            }
        )
    }

    SectionPage("رانندگان", onBack) {
        if (ui.loading) {
            CircularProgressIndicator()
            return@SectionPage
        }

        LzCard {
            if (t.running) {
                val elapsed = t.durationSeconds + ((now - t.updatedAtMs) / 1000).coerceAtLeast(0)
                Text("فعالیت در حال انجام است", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(fmtDuration(elapsed), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("درآمد تا الان: ${fmtMoney(t.earningsToman)} تومان")
                Spacer(Modifier.height(16.dp))
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
                    Spacer(Modifier.height(12.dp))
                }
                PrimaryButton("شروع فعالیت", ui.busy) { onStartClick() }
                Spacer(Modifier.height(8.dp))
                Text(
                    "با زدن دکمه، اگر GPS خاموش باشد از تو می‌خواهیم روشنش کنی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Lz.Muted
                )
            }
            ErrorLine(ui.error)
            t.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        if (!t.running) {
            Spacer(Modifier.height(16.dp))
            HowItWorksCard()
            Spacer(Modifier.height(16.dp))
            WeekCard(eu.week)
            Spacer(Modifier.height(16.dp))
            SettlementCard(
                eu = eu,
                user = user,
                amountText = amountText,
                onAmount = { amountText = it.filter { c -> c.isDigit() }.take(12) },
                onRequest = { confirm = true },
                onGoAccount = onGoAccount
            )
            Spacer(Modifier.height(16.dp))
            LzCard {
                Text("🚛  کامیون", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("به زودی...", color = Lz.Muted)
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    LzCard {
        Text("درآمد چطور حساب می‌شود؟", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "درآمد تو بر اساس مدت زمان فعالیت و منطقه‌ای که در آن حرکت می‌کنی حساب می‌شود. " +
                "لیزو برای هر ساعت فعالیت یک نرخ پایه تعیین می‌کند و در مناطق پرتردد ضریب بیشتری دارد. " +
                "اگر موتور مدتی ثابت بماند، فعالیت خودکار قطع می‌شود. " +
                "برای ثبت درست درآمد، GPS را روشن نگه دار.",
            style = MaterialTheme.typography.bodyMedium,
            color = Lz.Muted
        )
    }
}

@Composable
private fun WeekCard(week: WeekDto?) {
    LzCard {
        Text("درآمد ۷ روز اخیر", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (week == null) {
            Text("در حال بارگذاری…", color = Lz.Muted)
            return@LzCard
        }
        val max = (week.days.maxOfOrNull { it.toman } ?: 0L).coerceAtLeast(1L)
        week.days.forEach { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fmtDay(d.day), modifier = Modifier.width(52.dp), color = Lz.Muted)
                Box(modifier = Modifier.weight(1f)) {
                    val frac = (d.toman.toFloat() / max.toFloat()).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (d.toman > 0) Lz.Accent else Lz.Line)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(fmtMoney(d.toman), modifier = Modifier.width(84.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("جمع: ${fmtMoney(week.totalToman)} تومان", fontWeight = FontWeight.Bold)
        Text("مدت فعالیت: ${fmtDuration(week.totalSeconds)}", color = Lz.Muted)
    }
}

@Composable
private fun SettlementCard(
    eu: EarningsUi,
    user: UserDto,
    amountText: String,
    onAmount: (String) -> Unit,
    onRequest: () -> Unit,
    onGoAccount: () -> Unit
) {
    val s = eu.summary
    LzCard {
        Text("درآمد و تسویه", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (s == null) {
            if (eu.loading) CircularProgressIndicator() else ErrorLine(eu.error)
            return@LzCard
        }
        Text("امروز: ${fmtMoney(s.todayToman)} تومان")
        Text("کل: ${fmtMoney(s.totalToman)} تومان")
        Spacer(Modifier.height(4.dp))
        Text("قابل برداشت: ${fmtMoney(s.availableToman)} تومان", fontWeight = FontWeight.Bold)
        if (s.inProcessToman > 0) Text("در حال تسویه: ${fmtMoney(s.inProcessToman)} تومان")
        if (s.paidToman > 0) Text("پرداخت‌شده: ${fmtMoney(s.paidToman)} تومان")

        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))

        val ready = user.hasBank && user.hasIdDoc
        if (!ready) {
            Text(
                "برای اولین تسویه باید اطلاعات زیر ثبت شود:",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(if (user.hasBank) "✓ شماره شبا و کارت" else "✗ شماره شبا و کارت (به نام خودت)")
            Text(if (user.hasIdDoc) "✓ عکس کارت ملی یا شناسنامه" else "✗ عکس کارت ملی یا شناسنامه")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onGoAccount, modifier = Modifier.fillMaxWidth()) {
                Text("ثبت اطلاعات در حساب کاربری")
            }
            Spacer(Modifier.height(14.dp))
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = onAmount,
            label = { Text("مبلغ تسویه (تومان)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "حداقل مبلغ تسویه ${fmtMoney(s.minSettlementToman)} تومان است.",
            style = MaterialTheme.typography.bodySmall,
            color = Lz.Muted
        )
        Spacer(Modifier.height(10.dp))

        val amount = amountText.toLongOrNull()
        val valid = amount != null && amount >= s.minSettlementToman && amount <= s.availableToman
        val canRequest = ready && valid && s.inProcessToman == 0L
        PrimaryButton("درخواست تسویه", eu.busy, canRequest) { onRequest() }
        Spacer(Modifier.height(4.dp))
        when {
            s.inProcessToman > 0 -> Text("یک درخواست تسویه در جریان داری.")
            amount != null && amount > s.availableToman ->
                Text("مبلغ از موجودی قابل برداشت بیشتر است.")
            amount != null && amount < s.minSettlementToman ->
                Text("مبلغ کمتر از حداقل تسویه است.")
            else -> {}
        }
        eu.info?.let { Text(it, color = Lz.Good) }
        ErrorLine(eu.error)

        if (eu.settlements.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("درخواست‌های تسویه", fontWeight = FontWeight.Bold)
            eu.settlements.forEach { SettlementRow(it) }
        }
    }
}

@Composable
private fun SettlementRow(st: SettlementDto) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text("${fmtMoney(st.amountToman)} تومان | ${settlementStatusLabel(st.status)}")
        val date = fmtDate(st.requestedAt)
        if (date.isNotEmpty()) Text(date, style = MaterialTheme.typography.bodySmall, color = Lz.Muted)
        if (st.status == "rejected" && !st.note.isNullOrBlank()) {
            Text("دلیل: ${st.note}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
    }
}
