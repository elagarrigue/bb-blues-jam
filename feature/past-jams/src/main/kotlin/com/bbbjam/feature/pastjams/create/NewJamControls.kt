package com.bbbjam.feature.pastjams.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamFormSeed
import com.bbbjam.core.data.jams.JamFormValues
import com.bbbjam.core.data.jams.formatFormTime
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.ui.jamform.JamFormField
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** The last creation, kept in the composition (J9): going, done, or failed with the values to retry. */
internal sealed interface NewJamProgress {
    data object Creating : NewJamProgress

    data object Created : NewJamProgress

    /** [outcome] is `NotCreated` or `ScriptOutdated`, never `Created`. */
    data class Failed(val values: JamFormValues, val outcome: CreateJamOutcome) : NewJamProgress
}

/**
 * The form's state and decisions (`admin-jams-tab`, J7/J9). The open flag, the three texts and which
 * fields were edited are saveable; the dialog resets each time it opens. `Crear la jam` closes the dialog
 * and calls [JamCreator.createJam] undispatched behind an in-flight guard, so a double tap creates once.
 * If the composition dies mid-call the write still finishes in `DataScope`; only the result line is lost.
 */
internal class NewJamControls(
    val isOpen: Boolean,
    val input: JamFormInput,
    private val touchedMask: Int,
    val progress: NewJamProgress?,
    val onOpen: (JamFormSeed) -> Unit,
    val onCancel: () -> Unit,
    val onEdit: (JamFormField, String) -> Unit,
    val onCreate: (JamFormValues) -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
) {
    /** True once the admin typed in [field] since the dialog opened (a blank field is no error before that). */
    fun isTouched(field: JamFormField): Boolean = touchedMask and field.bit != 0
}

private val JamFormField.bit: Int
    get() = 1 shl ordinal

/** The call and what it leaves behind: the done line, or the values and the outcome to retry. */
private suspend fun JamCreator.create(values: JamFormValues): NewJamProgress {
    val outcome = createJam(values.date, values.time, values.venue)
    return if (outcome is CreateJamOutcome.Created) NewJamProgress.Created else NewJamProgress.Failed(values, outcome)
}

@Composable
internal fun rememberNewJamControls(creator: JamCreator): NewJamControls {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<NewJamProgress?>(null) }
    var inFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val send: (JamFormValues) -> Unit = { values ->
        inFlight = true
        progress = NewJamProgress.Creating
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                progress = creator.create(values)
            } finally {
                inFlight = false
            }
        }
    }
    return NewJamControls(
        isOpen = isOpen,
        input = JamFormInput(date, time, venue),
        touchedMask = touched,
        progress = progress,
        onOpen = { seed ->
            if (!inFlight) {
                date = ""
                time = formatFormTime(seed.time)
                venue = seed.venue
                touched = 0
                progress = null
                isOpen = true
            }
        },
        onCancel = { isOpen = false },
        onEdit = { field, text ->
            when (field) {
                JamFormField.DATE -> date = text
                JamFormField.TIME -> time = text
                JamFormField.VENUE -> venue = text
            }
            touched = touched or field.bit
        },
        onCreate = { values ->
            if (isOpen && !inFlight) {
                isOpen = false
                send(values)
            }
        },
        onRetry = {
            val failed = progress as? NewJamProgress.Failed
            if (failed != null && failed.outcome !is CreateJamOutcome.ScriptOutdated && !inFlight) send(failed.values)
        },
        onDismiss = { if (!inFlight) progress = null },
    )
}
