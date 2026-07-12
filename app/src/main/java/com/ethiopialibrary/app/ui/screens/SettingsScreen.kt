package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.BuildConfig
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.exportAndShareBackup
import com.ethiopialibrary.app.dates.CalendarMode
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.SectionHeader
import com.ethiopialibrary.app.ui.SettingsViewModel
import com.ethiopialibrary.app.ui.safeLaunch
import com.ethiopialibrary.app.update.PackageInstallerUpdateInstaller
import com.ethiopialibrary.app.update.UpdateWorker
import com.ethiopialibrary.app.update.updateAvailable
import kotlinx.coroutines.launch

/**
 * Settings hold the destructive actions (restore from cloud, language,
 * loan period), so the whole screen sits behind the staff PIN once one
 * has been set.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, repo: LibraryRepository, onBack: () -> Unit) {
    var pinRefresh by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }
    val hasPin by produceState<Boolean?>(null, pinRefresh) { value = repo.hasStaffPin() }

    when {
        hasPin == null -> Unit // brief load; avoids flashing locked/unlocked UI
        hasPin == true && !unlocked -> PinGate(
            repo = repo,
            onBack = onBack,
            onUnlocked = { unlocked = true },
        )
        else -> SettingsContent(
            vm = vm,
            repo = repo,
            onBack = onBack,
            hasPin = hasPin == true,
            onPinChanged = { pinRefresh++ },
        )
    }
}

@Composable
private fun PinGate(repo: LibraryRepository, onBack: () -> Unit, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    PageColumn(scrollable = false) {
        AppTopBar(stringResource(R.string.nav_settings), onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.enter_pin), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(6)
                    wrong = false
                },
                modifier = Modifier.widthIn(max = 280.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                isError = wrong,
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wrong_pin),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
            BigButton(stringResource(R.string.ok), Modifier.widthIn(max = 280.dp)) {
                scope.launch {
                    if (repo.verifyStaffPin(pin)) onUnlocked() else wrong = true
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    vm: SettingsViewModel,
    repo: LibraryRepository,
    onBack: () -> Unit,
    hasPin: Boolean,
    onPinChanged: () -> Unit,
) {
    val loanDays by vm.loanPeriodDays.collectAsStateWithLifecycle()
    var daysText by remember(loanDays) { mutableStateOf(loanDays?.toString().orEmpty()) }
    val maxBooks by vm.maxBooks.collectAsStateWithLifecycle()
    var maxBooksText by remember(maxBooks) { mutableStateOf(maxBooks?.toString().orEmpty()) }
    val dueSoonDays by vm.dueSoonDays.collectAsStateWithLifecycle()
    var dueSoonText by remember(dueSoonDays) { mutableStateOf(dueSoonDays?.toString().orEmpty()) }
    val calendarMode by vm.calendarMode.collectAsStateWithLifecycle()
    var showSetPin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    PageColumn {
        AppTopBar(stringResource(R.string.nav_settings), onBack)

        // Cloud backup leads: it's the most important safety net for this app's
        // data, so it gets top billing ahead of every other setting.
        AppCard(modifier = Modifier.fillMaxWidth()) {
            CloudBackupSection(repo)
            Spacer(Modifier.height(12.dp))
            // Off-device insurance, independent of Firebase: a plain SQLite copy
            // shared to Drive/WhatsApp/USB. Works fully offline.
            BigOutlinedButton(stringResource(R.string.export_backup_file)) {
                scope.launch { exportAndShareBackup(context, repo) }
            }
        }
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            UpdateSection(repo)
        }
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.settings_language))
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
            Spacer(Modifier.height(20.dp))

            SectionHeader(stringResource(R.string.settings_calendar))
            Spacer(Modifier.height(12.dp))
            val calendarOptions = listOf(
                CalendarMode.DUAL to R.string.calendar_dual,
                CalendarMode.ETHIOPIAN to R.string.calendar_ethiopian,
                CalendarMode.GREGORIAN to R.string.calendar_gregorian,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                calendarOptions.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = calendarMode == mode,
                        onClick = { vm.setCalendarMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, calendarOptions.size),
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.settings_lending_rules))
            Spacer(Modifier.height(12.dp))
            NumericSettingRow(
                label = stringResource(R.string.settings_loan_period),
                value = daysText,
                onValueChange = { daysText = it.filter(Char::isDigit) },
                onSave = {
                    daysText.toIntOrNull()?.takeIf { it in 1..LibraryRepository.MAX_LOAN_PERIOD_DAYS }
                        ?.let(vm::setLoanPeriodDays)
                },
            )
            Spacer(Modifier.height(20.dp))
            NumericSettingRow(
                label = stringResource(R.string.settings_max_books),
                value = maxBooksText,
                onValueChange = { maxBooksText = it.filter(Char::isDigit) },
                onSave = {
                    maxBooksText.toIntOrNull()?.takeIf { it in 0..LibraryRepository.MAX_BOOKS_PER_MEMBER_CEILING }
                        ?.let(vm::setMaxBooks)
                },
            )
            Spacer(Modifier.height(20.dp))
            NumericSettingRow(
                label = stringResource(R.string.settings_due_soon),
                value = dueSoonText,
                onValueChange = { dueSoonText = it.filter(Char::isDigit) },
                onSave = {
                    dueSoonText.toIntOrNull()?.takeIf { it in 1..LibraryRepository.MAX_DUE_SOON_DAYS }
                        ?.let(vm::setDueSoonDays)
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.staff_pin))
            Spacer(Modifier.height(12.dp))
            BigOutlinedButton(
                stringResource(if (hasPin) R.string.change_pin else R.string.set_pin),
            ) { showSetPin = true }
        }
    }

    if (showSetPin) {
        SetPinDialog(
            onDismiss = { showSetPin = false },
            onSave = { pin ->
                showSetPin = false
                scope.safeLaunch {
                    repo.setStaffPin(pin)
                    Toast.makeText(context, R.string.pin_saved, Toast.LENGTH_SHORT).show()
                    onPinChanged()
                }
            },
        )
    }
}

/**
 * Self-update: shows the running version, and once UpdateWorker has
 * downloaded and verified a newer signed build, an install button. The
 * permission check/request happens right here rather than earlier, since
 * "install unknown apps" only needs granting the moment staff actually
 * tries to install - not before there's anything to install.
 */
@Composable
private fun UpdateSection(repo: LibraryRepository) {
    val context = LocalContext.current
    val readyInfo by repo.updateReadyInfo().collectAsStateWithLifecycle(null)
    val available = updateAvailable(readyInfo, BuildConfig.VERSION_CODE)

    SectionHeader(stringResource(R.string.app_update))
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    if (available && readyInfo != null) {
        Text(
            stringResource(R.string.update_available, readyInfo!!.versionName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        BigButton(stringResource(R.string.install_update)) {
            val installer = PackageInstallerUpdateInstaller(context)
            if (installer.canInstall()) {
                installer.install(readyInfo!!.apkPath)
            } else {
                installer.requestInstallPermission()
            }
        }
    } else {
        BigOutlinedButton(stringResource(R.string.check_for_update)) {
            UpdateWorker.checkNow(context)
            Toast.makeText(context, R.string.update_check_started, Toast.LENGTH_SHORT).show()
        }
    }
}

/** One "label, number field, save button" unit shared by the three lending-rule
 * settings; each call site keeps its own validation/save logic. */
@Composable
private fun NumericSettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    SectionHeader(label)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Spacer(Modifier.height(8.dp))
    BigButton(stringResource(R.string.save)) { onSave() }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_pin)) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.pin_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in 4..6,
                onClick = { onSave(pin) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
