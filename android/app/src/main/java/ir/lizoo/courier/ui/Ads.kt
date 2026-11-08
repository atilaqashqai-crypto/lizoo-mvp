package ir.lizoo.courier.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.lizoo.courier.data.AdOrderDto
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdsUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val prices: Map<String, Long> = emptyMap(),
    val whatsapp: String = "",
    val payment: String = "",
    val orders: List<AdOrderDto> = emptyList(),
    val created: AdOrderDto? = null,
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
            val p = repo.adPrices()
            val o = repo.myAds()
            _ui.update {
                var n = it.copy(loading = false)
                when (p) {
                    is ApiResult.Ok -> n = n.copy(
                        prices = p.data.prices,
                        whatsapp = p.data.supportWhatsapp.orEmpty(),
                        payment = p.data.paymentInfo.orEmpty(),
                        error = null
                    )
                    is ApiResult.Fail -> n = n.copy(error = errorText(p.error))
                }
                if (o is ApiResult.Ok) n = n.copy(orders = o.data.orders)
                n
            }
        }
    }

    fun submit(name: String, phone: String, boxes: Int, type: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.createAd(name, phone, boxes, type)) {
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

@Composable
fun AdsScreen(onBack: () -> Unit, vm: AdsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var boxes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("normal") }

    val created = ui.created
    if (created != null) {
        SectionPage("ثبت تبلیغ", { vm.dismissCreated() }) {
            LzCard {
                Text("سفارش ثبت شد ✓", fontWeight = FontWeight.Bold, color = Lz.Good)
                Spacer(Modifier.height(10.dp))
                Text("تبلیغ‌دهنده: ${created.advertiserName}")
                Text("تعداد باکس: ${created.boxesCount}")
                Text("محل: ${locationLabel(created.locationType)}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "مبلغ قابل پرداخت: ${fmtMoney(created.totalPriceToman)} تومان",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            LzCard {
                Text("مرحله بعد: پرداخت", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("مبلغ را به حساب لیزو واریز کن و عکس رسید را برای پشتیبانی بفرست تا سفارشت تأیید شود.")
                if (ui.payment.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(ui.payment, color = Lz.AccentSoft)
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "اطلاعات حساب لیزو به‌زودی اینجا اعلام می‌شود.",
                        color = Lz.Muted
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (ui.whatsapp.isNotBlank()) {
                    PrimaryButton("ارسال رسید در واتساپ", false) { openWhatsApp(ctx, ui.whatsapp) }
                } else {
                    Text("راه ارتباطی پشتیبانی به‌زودی اضافه می‌شود.", color = Lz.Muted)
                }
            }
        }
        return
    }

    SectionPage("ثبت تبلیغ", onBack) {
        LzCard {
            Field(name, { name = it.take(120) }, "شخص / شرکت تبلیغ‌دهنده")
            Spacer(Modifier.height(12.dp))
            Field(
                phone,
                { phone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                "شماره تماس",
                KeyboardType.Phone,
                ltr = true
            )
            Spacer(Modifier.height(12.dp))
            Field(
                boxes,
                { boxes = it.filter { c -> c.isDigit() }.take(4) },
                "تعداد باکس‌های تبلیغاتی",
                KeyboardType.Number,
                ltr = true
            )
        }
        Spacer(Modifier.height(14.dp))
        LzCard {
            Text("محل تبلیغاتی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf("busy", "normal", "low").forEach { k ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { type = k }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = type == k, onClick = { type = k })
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(locationLabel(k))
                        val price = ui.prices[k]
                        Text(
                            if (price != null && price > 0) "هر باکس: ${fmtMoney(price)} تومان"
                            else "قیمت هنوز تعیین نشده",
                            style = MaterialTheme.typography.bodySmall,
                            color = Lz.Muted
                        )
                    }
                }
            }
            val count = boxes.toIntOrNull() ?: 0
            val unit = ui.prices[type] ?: 0L
            if (count > 0 && unit > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "مبلغ کل: ${fmtMoney(unit * count)} تومان",
                    fontWeight = FontWeight.Bold,
                    color = Lz.AccentSoft
                )
            }
        }
        ErrorLine(ui.error)
        Spacer(Modifier.height(14.dp))
        val count = boxes.toIntOrNull() ?: 0
        val ok = name.trim().length >= 2 && phone.length >= 10 && count in 1..1000
        PrimaryButton("ثبت سفارش", ui.busy, ok) {
            vm.submit(name.trim(), phone, count, type)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "پرداخت بعد از ثبت سفارش و با واریز به حساب لیزو انجام می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = Lz.Muted
        )

        if (ui.orders.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("سفارش‌های من", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ui.orders.forEach { o ->
                LzCard {
                    Text("${o.advertiserName} | ${o.boxesCount} باکس | ${locationLabel(o.locationType)}")
                    Text("${fmtMoney(o.totalPriceToman)} تومان | ${adStatusLabel(o.status)}")
                    val d = fmtDate(o.createdAt)
                    if (d.isNotEmpty()) {
                        Text(d, style = MaterialTheme.typography.bodySmall, color = Lz.Muted)
                    }
                    if (o.status == "rejected" && !o.note.isNullOrBlank()) {
                        Text("دلیل: ${o.note}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
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
