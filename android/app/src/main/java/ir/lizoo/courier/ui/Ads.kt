package ir.lizoo.courier.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.lizoo.courier.R
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.BoxTypeDto
import ir.lizoo.courier.data.Graph
import ir.lizoo.courier.data.MotorOrderDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Amber = Color(0xFFF59E0B)

data class AdsUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val boxTypes: List<BoxTypeDto> = emptyList(),
    val whatsapp: String = "",
    val payment: String = "",
    val orders: List<MotorOrderDto> = emptyList(),
    val fetchedAtMs: Long = 0L,
    val created: MotorOrderDto? = null,
    val error: String? = null
)

class AdsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Graph.repo(app.applicationContext)
    private val _ui = MutableStateFlow(AdsUi())
    val ui: StateFlow<AdsUi> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val t = repo.boxTypes()
            val o = repo.myMotorOrders()
            _ui.update {
                var n = it.copy(loading = false)
                when (t) {
                    is ApiResult.Ok -> n = n.copy(
                        boxTypes = t.data.boxTypes,
                        whatsapp = t.data.supportWhatsapp.orEmpty(),
                        payment = t.data.paymentInfo.orEmpty(),
                        error = null
                    )
                    is ApiResult.Fail -> n = n.copy(error = errorText(t.error))
                }
                if (o is ApiResult.Ok) {
                    n = n.copy(orders = o.data.orders, fetchedAtMs = System.currentTimeMillis())
                }
                n
            }
        }
    }

    fun submit(name: String, phone: String, code: String, motors: Int, hours: Int) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.createMotorOrder(name, phone, code, motors, hours)) {
                is ApiResult.Ok -> {
                    _ui.update {
                        it.copy(
                            busy = false,
                            created = r.data.order,
                            whatsapp = r.data.supportWhatsapp.orEmpty().ifEmpty { it.whatsapp },
                            payment = r.data.paymentInfo.orEmpty().ifEmpty { it.payment }
                        )
                    }
                    load()
                }
                is ApiResult.Fail ->
                    _ui.update { it.copy(busy = false, error = errorText(r.error)) }
            }
        }
    }

    fun dismissCreated() = _ui.update { it.copy(created = null) }
}

internal fun openWhatsApp(ctx: Context, number: String) {
    val d = number.filter { it.isDigit() }
    if (d.isEmpty()) return
    val full = if (d.startsWith("0")) "98" + d.drop(1) else d
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full")))
    } catch (e: Exception) {
        // no app can open the link
    }
}

private enum class AdsPage { Home, Select, Overview, Created }

@Composable
fun AdsScreen(onBack: () -> Unit, defaultPhone: String, vm: AdsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    var page by rememberSaveable { mutableStateOf(AdsPage.Home) }

    BackHandler(enabled = page != AdsPage.Home) {
        page = if (page == AdsPage.Created) AdsPage.Overview else AdsPage.Home
    }
    LaunchedEffect(page) {
        if (page == AdsPage.Overview || page == AdsPage.Select) vm.load()
    }
    LaunchedEffect(ui.created) {
        if (ui.created != null && page == AdsPage.Select) page = AdsPage.Created
    }

    when (page) {
        AdsPage.Home -> AdsHome(
            onBack = onBack,
            onSelect = { page = AdsPage.Select },
            onOverview = { page = AdsPage.Overview }
        )
        AdsPage.Select -> SelectPage(ui, defaultPhone, vm) { page = AdsPage.Home }
        AdsPage.Overview -> OverviewPage(ui, onBack = { page = AdsPage.Home }) {
            page = AdsPage.Select
        }
        AdsPage.Created ->
            if (ui.created == null) {
                OverviewPage(ui, onBack = { page = AdsPage.Home }) { page = AdsPage.Select }
            } else {
                CreatedPage(ui, onOverview = {
                    vm.dismissCreated()
                    page = AdsPage.Overview
                })
            }
    }
}

// ---------------- screen 1: vehicle types ----------------

@Composable
private fun AdsHome(onBack: () -> Unit, onSelect: () -> Unit, onOverview: () -> Unit) {
    var soon by remember { mutableStateOf(false) }
    val comingSoon = { soon = true }

    SectionPage("📢  ثبت تبلیغ", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VehicleTile(R.drawable.key_truck, false, Modifier.weight(1f), comingSoon)
            VehicleTile(R.drawable.key_car, false, Modifier.weight(1f), comingSoon)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VehicleTile(R.drawable.key_motor, true, Modifier.weight(1f), onSelect)
            VehicleTile(R.drawable.key_pickup, false, Modifier.weight(1f), comingSoon)
        }
        if (soon) {
            Spacer(Modifier.height(10.dp))
            Text(
                "این بخش به‌زودی اضافه می‌شود. فعلاً تبلیغ روی موتور فعال است.",
                color = Lz.AccentSoft,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
        ActionCard(R.drawable.icon_new_order, "پنل ثبت سفارش", "ثبت سریع و آسان سفارش جدید", onSelect)
        Spacer(Modifier.height(12.dp))
        ActionCard(
            R.drawable.icon_active_orders,
            "سفارشات در حال انجام",
            "مشاهده وضعیت سفارش‌های شما",
            onOverview
        )
    }
}

@Composable
private fun VehicleTile(image: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.6f)
        )
        if (!enabled) {
            Text(
                "به‌زودی",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x990A1020))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ActionCard(image: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Lz.CardHi)
            .border(1.dp, Lz.Accent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Lz.Muted, fontSize = 12.sp)
        }
        Text("←", fontSize = 20.sp, color = Lz.AccentSoft)
    }
}

// ---------------- screen 2: box types + order ----------------

private fun boxImage(code: String): Int = when (code) {
    "transparent" -> R.drawable.box_transparent
    "moving" -> R.drawable.box_moving
    "tv" -> R.drawable.box_tv
    else -> R.drawable.box_fixed
}

@Composable
private fun SelectPage(ui: AdsUi, defaultPhone: String, vm: AdsViewModel, onBack: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    var motors by remember { mutableStateOf(1) }
    var hours by remember { mutableStateOf(2) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(defaultPhone) }
    val sel = ui.boxTypes.firstOrNull { it.code == code }

    SectionPage("📢  ثبت تبلیغ", onBack) {
        LzCard {
            Text("نوع موتور مورد نظر را انتخاب کنید", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "یکی از گزینه‌های زیر را انتخاب کنید تا جزئیات آن را ببینید.",
                color = Lz.Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(14.dp))

        if (ui.loading) {
            CircularProgressIndicator()
        } else if (ui.boxTypes.isEmpty()) {
            Text(ui.error ?: "فعلاً نوع باکسی تعریف نشده است.", color = Lz.Muted)
        }

        ui.boxTypes.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { b ->
                    BoxTypeCard(b, b.code == code, Modifier.weight(1f)) {
                        if (b.available > 0) {
                            code = b.code
                            hours = b.hoursDefault.coerceIn(1, 24)
                            motors = 1
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        if (sel != null) {
            LzCard {
                Text(sel.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Stepper("تعداد موتور", motors, 1, sel.available.coerceAtLeast(1)) { motors = it }
                Stepper("ساعت تبلیغ", hours, 1, 24) { hours = it }
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it.take(120) }, "شخص / شرکت تبلیغ‌دهنده")
                Spacer(Modifier.height(10.dp))
                Field(
                    phone,
                    { phone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                    "شماره تماس",
                    KeyboardType.Phone,
                    ltr = true
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                val total = sel.pricePerHourToman * hours * motors
                Text(
                    "مبلغ کل: ${fmtMoney(total)} تومان",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Lz.AccentSoft
                )
                Text(
                    "${fmtMoney(motors.toLong())} موتور × ${fmtMoney(hours.toLong())} ساعت × " +
                        "${fmtMoney(sel.pricePerHourToman)} تومان",
                    color = Lz.Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                ErrorLine(ui.error)
                Spacer(Modifier.height(12.dp))
                val ok = name.trim().length >= 2 && phone.length >= 10
                PrimaryButton("ثبت سفارش", ui.busy, ok) {
                    vm.submit(name.trim(), phone, sel.code, motors, hours)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "سفارش بعد از ثبت برای تأیید به مدیر لیزو ارسال می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Lz.Muted
                )
            }
        }
    }
}

@Composable
private fun BoxTypeCard(b: BoxTypeDto, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val soldOut = b.available <= 0
    Column(
        modifier = modifier
            .clip(shape)
            .background(Lz.CardHi)
            .border(if (selected) 2.dp else 1.dp, if (selected) Lz.Accent else Lz.Line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(boxImage(b.code)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (soldOut) 0.5f else 1f)
            )
            if (selected) {
                Text(
                    "✓",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Lz.Accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("⏱ ${fmtMoney(b.hoursDefault.toLong())} ساعت", fontSize = 12.sp, color = Lz.Muted)
        Text(
            if (soldOut) "ناموجود" else "🏍 موجود: ${fmtMoney(b.available.toLong())}",
            fontSize = 12.sp,
            color = if (soldOut) MaterialTheme.colorScheme.error else Lz.Muted
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "هر ساعت ${fmtMoney(b.pricePerHourToman)} تومان",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Lz.AccentSoft
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min
        ) { Text("−") }
        Text(
            fmtMoney(value.toLong()),
            modifier = Modifier.padding(horizontal = 14.dp),
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max
        ) { Text("+") }
    }
}

// ---------------- created ----------------

@Composable
private fun CreatedPage(ui: AdsUi, onOverview: () -> Unit) {
    val ctx = LocalContext.current
    val c = ui.created ?: return
    SectionPage("ثبت تبلیغ", null) {
        LzCard {
            Text("سفارش ثبت شد ✓", fontWeight = FontWeight.Bold, color = Lz.Good)
            Spacer(Modifier.height(10.dp))
            Text("تبلیغ‌دهنده: ${c.advertiserName}")
            Text("نوع: ${c.boxName ?: c.boxCode}")
            Text("${fmtMoney(c.motorsCount.toLong())} موتور | ${fmtMoney(c.hours.toLong())} ساعت")
            Spacer(Modifier.height(6.dp))
            Text(
                "مبلغ قابل پرداخت: ${fmtMoney(c.totalPriceToman)} تومان",
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        LzCard {
            Text("مرحله بعد: پرداخت", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("مبلغ را به حساب لیزو واریز کن و عکس رسید را برای پشتیبانی بفرست تا سفارشت تأیید شود.")
            Spacer(Modifier.height(10.dp))
            if (ui.payment.isNotBlank()) {
                Text(ui.payment, color = Lz.AccentSoft)
            } else {
                Text("اطلاعات حساب لیزو به‌زودی اینجا اعلام می‌شود.", color = Lz.Muted)
            }
            Spacer(Modifier.height(14.dp))
            if (ui.whatsapp.isNotBlank()) {
                PrimaryButton("ارسال رسید در واتساپ", false) { openWhatsApp(ctx, ui.whatsapp) }
            } else {
                Text("راه ارتباطی پشتیبانی به‌زودی اضافه می‌شود.", color = Lz.Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onOverview, modifier = Modifier.fillMaxWidth()) {
            Text("مشاهده سفارش‌ها")
        }
    }
}

// ---------------- screen 3: active orders ----------------

@Composable
private fun OverviewPage(ui: AdsUi, onBack: () -> Unit, onNew: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    fun remaining(o: MotorOrderDto): Long =
        (o.remainingSeconds - (now - ui.fetchedAtMs) / 1000).coerceAtLeast(0)

    fun shown(o: MotorOrderDto): String =
        if (o.status == "active" && remaining(o) <= 0) "done" else o.status

    val active = ui.orders.filter { shown(it) == "active" }

    SectionPage("سفارش‌ها", onBack) {
        if (ui.loading) CircularProgressIndicator()

        LzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("مجموع تبلیغات فعال", color = Lz.Muted, fontSize = 12.sp)
                    Text(
                        "${fmtMoney(active.size.toLong())} مورد",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Lz.AccentSoft
                    )
                }
                Text("📢", fontSize = 32.sp)
            }
            val byType = active.groupBy { it.boxName ?: it.boxCode }
                .mapValues { e -> e.value.sumOf { it.motorsCount } }
            if (byType.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                byType.forEach { (n, count) ->
                    Text("🏍 ${fmtMoney(count.toLong())} موتور | $n", fontSize = 13.sp)
                }
            }
        }

        active.forEach { o ->
            Spacer(Modifier.height(16.dp))
            Text(
                "موتورهای ${o.boxName ?: o.boxCode}",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            val shownMotors = minOf(o.motorsCount, 20)
            for (i in 1..shownMotors) {
                LzCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("موتور ${fmtMoney(i.toLong())}", fontWeight = FontWeight.Bold)
                            Text(o.boxName ?: o.boxCode, color = Lz.Muted, fontSize = 12.sp)
                            Text("● فعال", color = Lz.Good, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("زمان باقی‌مانده", color = Lz.Muted, fontSize = 11.sp)
                            Text(
                                fmtRemaining(remaining(o)),
                                color = Lz.AccentSoft,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (o.motorsCount > shownMotors) {
                Text(
                    "و ${fmtMoney((o.motorsCount - shownMotors).toLong())} موتور دیگر",
                    color = Lz.Muted
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("همه سفارش‌ها", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (ui.orders.isEmpty() && !ui.loading) {
            Text("هنوز سفارشی ثبت نکرده‌ای.", color = Lz.Muted)
        }
        ui.orders.forEach { o ->
            val st = shown(o)
            LzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.advertiserName,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        adStatusLabel(st),
                        fontWeight = FontWeight.Bold,
                        color = when (st) {
                            "active" -> Lz.Good
                            "pending" -> Amber
                            "rejected" -> MaterialTheme.colorScheme.error
                            else -> Lz.Muted
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${o.boxName ?: o.boxCode} | ${fmtMoney(o.motorsCount.toLong())} موتور | " +
                        "${fmtMoney(o.hours.toLong())} ساعت",
                    fontSize = 13.sp
                )
                Text("${fmtMoney(o.totalPriceToman)} تومان", color = Lz.AccentSoft)
                val d = fmtDate(o.createdAt)
                if (d.isNotEmpty()) Text(d, color = Lz.Muted, fontSize = 11.sp)
                if (st == "rejected" && !o.note.isNullOrBlank()) {
                    Text("دلیل: ${o.note}", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("＋ سفارش جدید", false) { onNew() }
    }
}

@Composable
fun AiScreen(onBack: () -> Unit) {
    SectionPage("طراحی با هوش مصنوعی", onBack) {
        LzCard {
            Text("طراحی کارت ویزیت و بنر تبلیغاتی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "به‌زودی می‌توانی با توضیح دادن کسب‌وکارت، کارت ویزیت و بنر تبلیغاتی را با هوش مصنوعی بسازی.",
                color = Lz.Muted
            )
            Spacer(Modifier.height(12.dp))
            Text("به زودی...", color = Lz.AccentSoft, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SupportScreen(vm: AdsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    SectionPage("پشتیبانی", null) {
        LzCard {
            Text("با ما در ارتباط باش", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (ui.whatsapp.isBlank()) {
                Text("راه ارتباطی پشتیبانی به‌زودی اضافه می‌شود.", color = Lz.Muted)
            } else {
                Text("برای سؤال، مشکل یا ارسال رسید پرداخت از واتساپ پیام بده.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { openWhatsApp(ctx, ui.whatsapp) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("باز کردن واتساپ") }
            }
        }
    }
}
