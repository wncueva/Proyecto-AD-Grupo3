package com.example.billetera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.billetera.ui.BilleteraScreen
import com.example.billetera.ui.theme.BilleteraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BilleteraTheme {
                BilleteraScreen()
            }
        }
    }
}
