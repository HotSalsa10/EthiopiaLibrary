package com.ethiopialibrary.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val loanDays by vm.loanPeriodDays.collectAsStateWithLifecycle()
    var daysText by remember(loanDays) { mutableStateOf(loanDays?.toString().orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        AppTopBar(stringResource(R.string.nav_settings), onBack)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigOutlinedButton(stringResource(R.string.lang_amharic), Modifier.weight(1f)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("am"))
            }
            BigOutlinedButton(stringResource(R.string.lang_arabic), Modifier.weight(1f)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            }
            BigOutlinedButton(stringResource(R.string.lang_english), Modifier.weight(1f)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.settings_loan_period), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = daysText,
            onValueChange = { daysText = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.save)) {
            daysText.toIntOrNull()?.takeIf { it > 0 }?.let(vm::setLoanPeriodDays)
        }
    }
}
