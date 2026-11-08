package ir.lizoo.courier.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountScreen(s: UiState, vm: AuthViewModel) {
    val u = s.user ?: return
    var sheba by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.uploadDoc(uri)
    }

    SectionPage("حساب من", null) {
        // header
        LzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Lz.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        u.fullName?.trim()?.take(1).orEmpty().ifEmpty { "👤" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(14.dp))
                androidx.compose.foundation.layout.Column {
                    Text(
                        u.fullName ?: "بدون نام",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (!u.username.isNullOrBlank()) {
                        Text("@${u.username}", color = Lz.AccentSoft)
                    }
                    Text(u.phone, color = Lz.Muted)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        LzCard {
            Text("اطلاعات پیک", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("شهر: ${u.city ?: "-"}")
            Text("پلاک موتور: ${u.vehiclePlate ?: "-"}")
            Text(
                "وضعیت: " + when {
                    !u.profileComplete -> "اطلاعات کامل نیست"
                    u.status == "approved" -> "تأیید شده"
                    u.status == "rejected" -> "رد شده"
                    else -> "در انتظار تأیید"
                }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { vm.editProfile() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (u.profileComplete) "ویرایش اطلاعات" else "تکمیل اطلاعات") }
        }

        Spacer(Modifier.height(14.dp))
        LzCard {
            Text("اطلاعات تسویه", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "شماره شبا و کارت باید به نام خودت باشد.",
                style = MaterialTheme.typography.bodySmall,
                color = Lz.Muted
            )
            Spacer(Modifier.height(10.dp))
            if (u.hasBank) {
                Text(
                    "✓ ثبت شده (کارت ••••${u.cardLast4 ?: ""} | شبا ••••${u.shebaLast4 ?: ""})",
                    color = Lz.Good
                )
                Spacer(Modifier.height(10.dp))
                Text("برای تغییر، اطلاعات جدید را وارد کن:", color = Lz.Muted)
                Spacer(Modifier.height(8.dp))
            }
            Field(sheba, { sheba = it.take(30) }, "شماره شبا (IR...)", ltr = true)
            Spacer(Modifier.height(10.dp))
            Field(
                card,
                { card = it.filter { c -> c.isDigit() || c == ' ' }.take(19) },
                "شماره کارت (۱۶ رقم)",
                KeyboardType.Number,
                ltr = true
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "ذخیره اطلاعات بانکی",
                s.busy,
                sheba.length >= 20 && card.filter { it.isDigit() }.length >= 16
            ) {
                vm.saveBank(sheba, card)
                sheba = ""
                card = ""
            }
        }

        Spacer(Modifier.height(14.dp))
        LzCard {
            Text("مدارک هویتی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (u.hasIdDoc) "✓ عکس کارت ملی یا شناسنامه آپلود شده"
                else "✗ هنوز آپلود نشده",
                color = if (u.hasIdDoc) Lz.Good else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "برای تسویه، عکس واضح کارت ملی یا شناسنامه خودت لازم است.",
                style = MaterialTheme.typography.bodySmall,
                color = Lz.Muted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { picker.launch("image/*") },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (u.hasIdDoc) "تغییر عکس مدرک" else "انتخاب عکس مدرک") }
        }

        s.info?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Lz.Good)
        }
        ErrorLine(s.error)

        Spacer(Modifier.height(18.dp))
        TextButton(onClick = { vm.logout() }) {
            Text("خروج از حساب", color = MaterialTheme.colorScheme.error)
        }
    }
}
