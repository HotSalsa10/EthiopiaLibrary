package com.ethiopialibrary.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ethiopialibrary.app.ui.LibraryNavHost
import com.ethiopialibrary.app.ui.theme.EthiopiaLibraryTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Amharic is the app's default language until staff pick another
        // (autoStoreLocales persists the choice across restarts).
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("am"))
        }
        val repository = (application as LibraryApp).repository
        setContent {
            EthiopiaLibraryTheme {
                LibraryNavHost(repository)
            }
        }
    }
}
