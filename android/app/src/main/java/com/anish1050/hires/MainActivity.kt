package com.anish1050.hires

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anish1050.hires.ui.HiResApp
import com.anish1050.hires.ui.theme.HiResTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HiResTheme { HiResApp(this) } }
    }
}
