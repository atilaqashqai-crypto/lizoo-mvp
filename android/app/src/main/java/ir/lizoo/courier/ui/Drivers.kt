package ir.lizoo.courier.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DriversSection(s: UiState, vm: AuthViewModel, onBack: () -> Unit, onGoAccount: () -> Unit) {
    val u = s.user
    when {
        u == null -> SectionPage("رانندگان", onBack) { CircularProgressIndicator() }

        !u.profileComplete -> SectionPage("رانندگان", onBack) {
            LzCard {
                Text("برای شروع، اطلاعات پیک را کامل کن.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("نام، شهر و پلاک موتور برای بررسی توسط مدیر لیزو ارسال می‌شود.")
                Spacer(Modifier.height(14.dp))
                PrimaryButton("تکمیل اطلاعات پیک", s.busy) { vm.editProfile() }
            }
        }

        u.status != "approved" -> SectionPage("رانندگان", onBack) {
            LzCard {
                if (u.status == "rejected") {
                    Text(
                        "درخواست تو رد شده است.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("اطلاعات را اصلاح کن و دوباره ارسال کن.")
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("ویرایش و ارسال مجدد", s.busy) { vm.editProfile() }
                } else {
                    Text("درخواست تو در انتظار تأیید مدیر لیزو است.", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("بعد از تأیید، وضعیت را دوباره بررسی کن.")
                }
                ErrorLine(s.error)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { vm.refresh() },
                    enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بررسی وضعیت") }
            }
        }

        else -> HomeScreen(user = u, onBack = onBack, onGoAccount = onGoAccount)
    }
}
