package ir.lizoo.courier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.lizoo.courier.R

enum class Route { Home, Drivers, Ads, Ai, Support, Account }

@Composable
fun MainShell(s: UiState, vm: AuthViewModel, route: Route, onRoute: (Route) -> Unit) {
    BackHandler(enabled = route != Route.Home) { onRoute(Route.Home) }
    val tab = if (route == Route.Support || route == Route.Account) route else Route.Home

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Lz.Card) {
                NavigationBarItem(
                    selected = tab == Route.Home,
                    onClick = { onRoute(Route.Home) },
                    icon = { Text("🏠") },
                    label = { Text("خانه") }
                )
                NavigationBarItem(
                    selected = tab == Route.Support,
                    onClick = { onRoute(Route.Support) },
                    icon = { Text("🎧") },
                    label = { Text("پشتیبانی") }
                )
                NavigationBarItem(
                    selected = tab == Route.Account,
                    onClick = { onRoute(Route.Account) },
                    icon = { Text("👤") },
                    label = { Text("حساب من") }
                )
            }
        }
    ) { pad ->
        Box(modifier = Modifier.padding(pad).fillMaxSize()) {
            val back = { onRoute(Route.Home) }
            when (route) {
                Route.Home -> HomeTab(onOpen = onRoute)
                Route.Drivers -> DriversSection(s, vm, back) { onRoute(Route.Account) }
                Route.Ads -> AdsScreen(back)
                Route.Ai -> AiScreen(back)
                Route.Support -> SupportScreen()
                Route.Account -> AccountScreen(s, vm)
            }
        }
    }
}

@Composable
internal fun SectionPage(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("→ بازگشت") }
        } else {
            Spacer(Modifier.height(24.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun LzCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Lz.CardHi)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun HomeTab(onOpen: (Route) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Hero()
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            R.drawable.img_drivers, "رانندگان",
            "با موتور، ماشین و کامیون، تبلیغات را به مقصد برسانید."
        ) { onOpen(Route.Drivers) }
        Spacer(Modifier.height(14.dp))
        FeatureCard(
            R.drawable.img_ads, "ثبت تبلیغ",
            "سریع، آسان، هدفمند. تبلیغ خود را ثبت کنید."
        ) { onOpen(Route.Ads) }
        Spacer(Modifier.height(14.dp))
        FeatureCard(
            R.drawable.img_ai, "طراحی با هوش مصنوعی",
            "طراحی کارت ویزیت و بنر تبلیغاتی با هوش مصنوعی."
        ) { onOpen(Route.Ai) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Hero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B3E86), Color(0xFF0E1A33))))
            .padding(20.dp)
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.lizoo_logo),
                contentDescription = "Lizoo",
                modifier = Modifier.height(44.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart
            )
            Spacer(Modifier.height(10.dp))
            Text("تبلیغات هوشمند، فرصت‌های بیشتر", color = Lz.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Text("تبلیغات", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "همیشه در حرکت",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Lz.AccentSoft
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "از موتور تا ماشین و کامیون، بهترین مسیر دیده شدن.",
                color = Lz.Muted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun FeatureCard(image: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xF00A1020))
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFFCBD5E1))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x663B4A66)),
                contentAlignment = Alignment.Center
            ) { Text("←", fontSize = 18.sp) }
        }
    }
}
