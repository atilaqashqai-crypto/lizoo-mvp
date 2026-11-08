package ir.lizoo.courier.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

object Lz {
    val Bg = Color(0xFF0A1020)
    val Card = Color(0xFF111A2E)
    val CardHi = Color(0xFF16233D)
    val Accent = Color(0xFF3B82F6)
    val AccentSoft = Color(0xFF60A5FA)
    val Muted = Color(0xFF94A3B8)
    val Good = Color(0xFF22C55E)
    val Bad = Color(0xFFEF4444)
    val Line = Color(0xFF2B3A55)
}

@Composable
fun LizooTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Lz.Accent,
                onPrimary = Color.White,
                background = Lz.Bg,
                onBackground = Color.White,
                surface = Lz.Bg,
                onSurface = Color.White,
                surfaceVariant = Lz.Card,
                onSurfaceVariant = Lz.Muted,
                secondaryContainer = Lz.CardHi,
                onSecondaryContainer = Color.White,
                error = Color(0xFFFF6B6B),
                outline = Lz.Line
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    }
}
