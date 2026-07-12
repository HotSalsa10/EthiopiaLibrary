package com.ethiopialibrary.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.ethiopialibrary.app.ui.LibraryNavHost
import com.ethiopialibrary.app.ui.UiErrorBus
import com.ethiopialibrary.app.ui.theme.EthiopiaLibraryTheme

class MainActivity : AppCompatActivity() {

    // Registered during construction (required by the Activity Result API).
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result unused */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Amharic is the app's default language until staff pick another
        // (autoStoreLocales persists the choice across restarts).
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("am"))
        }
        requestNotificationPermissionIfNeeded()
        val repository = (application as LibraryApp).repository
        setContent {
            EthiopiaLibraryTheme {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    UiErrorBus.errors.collect { messageRes ->
                        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
                    }
                }
                LibraryNavHost(repository)
            }
        }
    }

    // The daily overdue reminder needs POST_NOTIFICATIONS on Android 13+.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
