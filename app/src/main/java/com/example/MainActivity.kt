package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DiscoveryApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DiscoveryViewModel

class MainActivity : ComponentActivity() {

    private val requestCodeNotifications = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            val viewModel: DiscoveryViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                DiscoveryApp()
            }
        }
    }

    // Запрос разрешения на уведомления (только для Android 13+)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    requestCodeNotifications
                )
            }
        }
    }
}
