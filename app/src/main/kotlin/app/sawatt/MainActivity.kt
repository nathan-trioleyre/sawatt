package app.sawatt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.sawatt.ui.SawattNavHost
import app.sawatt.ui.theme.SawattTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SawattTheme {
                SawattNavHost(container)
            }
        }
    }
}
