package ir.lizoo.courier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(vm: AuthViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    when (s.stage) {
        Stage.Loading -> LoadingScreen(s, vm)
        Stage.Phone -> PhoneScreen(s, vm)
        Stage.Otp -> OtpScreen(s, vm)
        Stage.Profile -> ProfileScreen(s, vm)
        Stage.Status ->
            if (s.user?.status == "approved") HomeScreen(onLogout = vm::logout)
            else StatusScreen(s, vm)
    }
}

@Composable
internal fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    ltr: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = if (ltr) {
            LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left)
        } else {
            LocalTextStyle.current
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            Text(text)
        }
    }
}

@Composable
internal fun ErrorLine(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LoadingScreen(s: UiState, vm: AuthViewModel) {
    Page("لیزو") {
        if (s.error == null) {
            CircularProgressIndicator()
        } else {
            Text(s.error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("تلاش مجدد", s.busy) { vm.bootstrap() }
            TextButton(onClick = { vm.logout() }) { Text("خروج از حساب") }
        }
    }
}

@Composable
private fun PhoneScreen(s: UiState, vm: AuthViewModel) {
    Page("ورود به لیزو") {
        Text("شماره موبایل خود را وارد کن تا کد تأیید برایت پیامک شود.")
        Spacer(Modifier.height(16.dp))
        Field(s.phone, vm::onPhone, "شماره موبایل", KeyboardType.Phone, ltr = true)
        ErrorLine(s.error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("دریافت کد", s.busy) { vm.sendOtp() }
    }
}

@Composable
private fun OtpScreen(s: UiState, vm: AuthViewModel) {
    Page("کد تأیید") {
        Text("کد پنج‌رقمی ارسال‌شده به ${s.phone} را وارد کن.")
        Spacer(Modifier.height(16.dp))
        Field(s.code, vm::onCode, "کد تأیید", KeyboardType.Number, ltr = true)
        ErrorLine(s.error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("تأیید و ورود", s.busy) { vm.verify() }
        Spacer(Modifier.height(8.dp))
        if (s.resendIn > 0) {
            Text("ارسال دوباره تا ${s.resendIn} ثانیه دیگر")
        } else {
            TextButton(onClick = { vm.resendOtp() }, enabled = !s.busy) {
                Text("ارسال دوباره کد")
            }
        }
        TextButton(onClick = { vm.backToPhone() }) { Text("تغییر شماره") }
    }
}

@Composable
private fun ProfileScreen(s: UiState, vm: AuthViewModel) {
    Page("اطلاعات پیک") {
        Text("اطلاعات زیر برای بررسی توسط مدیر لیزو ارسال می‌شود.")
        Spacer(Modifier.height(16.dp))
        Field(s.fullName, vm::onName, "نام و نام خانوادگی")
        Spacer(Modifier.height(12.dp))
        Field(s.city, vm::onCity, "شهر")
        Spacer(Modifier.height(12.dp))
        Field(s.plate, vm::onPlate, "پلاک موتور")
        ErrorLine(s.error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("ثبت اطلاعات", s.busy) { vm.saveProfile() }
        TextButton(onClick = { vm.logout() }) { Text("خروج از حساب") }
    }
}

@Composable
private fun StatusScreen(s: UiState, vm: AuthViewModel) {
    val user = s.user
    Page(user?.fullName ?: "وضعیت حساب") {
        when (user?.status) {
            "rejected" -> {
                Text("درخواست تو رد شده است.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("اطلاعات را اصلاح کن و دوباره ارسال کن.")
                Spacer(Modifier.height(16.dp))
                PrimaryButton("ویرایش و ارسال مجدد", s.busy) { vm.editProfile() }
            }
            else -> {
                Text("درخواست تو در انتظار تأیید مدیر لیزو است.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("بعد از تأیید، وضعیت را دوباره بررسی کن.")
            }
        }
        ErrorLine(s.error)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { vm.refresh() },
            enabled = !s.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("بررسی وضعیت") }
        TextButton(onClick = { vm.logout() }) { Text("خروج از حساب") }
    }
}
