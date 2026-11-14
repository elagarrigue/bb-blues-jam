package com.bbbjam.core.ui.jamform

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.util.Locale

@Composable
internal fun JamFormPicker(model: JamFormUiModel, field: JamFormField?, onDismiss: () -> Unit) {
    if (field == null) return
    // The app's copy is Spanish even on an English device; Material's calendar and clock must match.
    val current = LocalConfiguration.current
    val configuration = remember(current) { Configuration(current).apply { setLocale(Locale.forLanguageTag("es-AR")) } }
    val context = LocalContext.current
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
        PickerContent(model, field, onDismiss)
    }
}

@Composable
private fun PickerContent(model: JamFormUiModel, field: JamFormField, onDismiss: () -> Unit) {
    when (field) {
        JamFormField.DATE -> JamDatePicker(model.date.value, onDismiss) {
            model.events(JamFormUiModel.Event.Edit(JamFormField.DATE, it))
            onDismiss()
        }

        JamFormField.TIME -> JamTimePicker(model.time.value, onDismiss) {
            model.events(JamFormUiModel.Event.Edit(JamFormField.TIME, it))
            onDismiss()
        }

        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JamDatePicker(value: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = JamPickerValues.dateMillis(value))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = JamPickerDefaults.dateColors(),
        confirmButton = {
            PickerConfirm(enabled = state.selectedDateMillis != null) {
                state.selectedDateMillis?.let { onConfirm(JamPickerValues.dateText(it)) }
            }
        },
        dismissButton = { PickerCancel(onDismiss) },
    ) {
        DatePicker(state = state, showModeToggle = false, colors = JamPickerDefaults.dateColors())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JamTimePicker(value: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initial = JamPickerValues.time(value)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BluesJamTheme.colors.surfaceRaised,
        title = { Text(stringResource(R.string.jam_form_time_label), color = BluesJamTheme.colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(state = state, colors = JamPickerDefaults.timeColors())
            }
        },
        confirmButton = { PickerConfirm { onConfirm(JamPickerValues.timeText(state.hour, state.minute)) } },
        dismissButton = { PickerCancel(onDismiss) },
    )
}

@Composable
private fun PickerConfirm(enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) {
        Text(
            stringResource(R.string.jam_form_picker_confirm),
            color = if (enabled) BluesJamTheme.colors.text else BluesJamTheme.colors.textMuted,
        )
    }
}

@Composable
private fun PickerCancel(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.jam_form_cancel), color = BluesJamTheme.colors.text)
    }
}
