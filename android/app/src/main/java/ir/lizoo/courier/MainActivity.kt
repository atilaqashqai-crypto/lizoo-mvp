package ir.lizoo.courier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ir.lizoo.courier.ui.AppRoot
import ir.lizoo.courier.ui.LizooTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LizooTheme { AppRoot() }
        }
    }
}
