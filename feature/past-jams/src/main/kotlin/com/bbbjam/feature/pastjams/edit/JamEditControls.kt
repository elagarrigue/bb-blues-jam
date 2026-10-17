package com.bbbjam.feature.pastjams.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamFormValues
import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.SetlistJamEditRepository
import com.bbbjam.core.ui.jamform.JamFormField
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The edit and delete dialogs' state and decisions (`admin-edit-delete-jam` C4/C5). The editing date, the
 * three texts, which fields were edited and the confirming date are saveable; one modal is open at a time.
 * `Guardar` and the delete confirmation close their dialog and call the repository undispatched behind an
 * in-flight guard, so a double tap writes once. If the composition dies mid-call the write still finishes
 * in `DataScope` and the entry's failure card comes from the repository.
 */
internal class JamEditControls(
    val editingDate: String?,
    val input: JamFormInput,
    private val touchedMask: Int,
    val deletingDate: String?,
    val onOpenEdit: (jamDate: LocalDate, seed: JamFormInput) -> Unit,
    val onEdit: (JamFormField, String) -> Unit,
    val onCancelEdit: () -> Unit,
    val onSave: (jamDate: LocalDate, values: JamFormValues) -> Unit,
    val onRequestDelete: (LocalDate) -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: (jamDate: LocalDate, songs: Int) -> Unit,
    val onRetry: (JamEdit) -> Unit,
    val retain: (Set<LocalDate>) -> Unit,
) {
    /** True once the admin typed in [field] since the dialog opened. */
    fun isTouched(field: JamFormField): Boolean = touchedMask and (1 shl field.ordinal) != 0
}

/** The edit dialog's saveable fields: which jam is being edited, the three texts and the edited-fields mask. */
internal class EditDialogState(
    private val editing: MutableState<String?>,
    private val date: MutableState<String>,
    private val time: MutableState<String>,
    private val venue: MutableState<String>,
    private val touched: MutableState<Int>,
) {
    val editingDate: String? get() = editing.value
    val input: JamFormInput get() = JamFormInput(date.value, time.value, venue.value)
    val touchedMask: Int get() = touched.value

    fun isOpenFor(jamDate: LocalDate): Boolean = editing.value == jamDate.toString()

    fun open(jamDate: LocalDate, seed: JamFormInput) {
        date.value = seed.date
        time.value = seed.time
        venue.value = seed.venue
        touched.value = 0
        editing.value = jamDate.toString()
    }

    fun type(field: JamFormField, text: String) {
        when (field) {
            JamFormField.DATE -> date.value = text
            JamFormField.TIME -> time.value = text
            JamFormField.VENUE -> venue.value = text
        }
        touched.value = touched.value or (1 shl field.ordinal)
    }

    fun close() {
        editing.value = null
    }
}

@Composable
private fun rememberEditDialogState(): EditDialogState {
    val editing = rememberSaveable { mutableStateOf<String?>(null) }
    val date = rememberSaveable { mutableStateOf("") }
    val time = rememberSaveable { mutableStateOf("") }
    val venue = rememberSaveable { mutableStateOf("") }
    val touched = rememberSaveable { mutableStateOf(0) }
    return EditDialogState(editing, date, time, venue, touched)
}

/** Sends [entry] again, whatever it was. */
private suspend fun SetlistJamEditRepository.resend(entry: JamEdit) {
    when (val change = entry.change) {
        is JamEdit.Change.Update -> updateJam(entry.jamDate, change.newDate, change.startTime, change.venue)
        is JamEdit.Change.Delete -> deleteJam(entry.jamDate, change.expectedSongs)
    }
}

@Composable
internal fun rememberJamEditControls(edits: SetlistJamEditRepository): JamEditControls {
    val dialog = rememberEditDialogState()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launchWrite: (suspend () -> Unit) -> Unit = { write ->
        inFlight = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                write()
            } finally {
                inFlight = false
            }
        }
    }
    return JamEditControls(
        editingDate = dialog.editingDate,
        input = dialog.input,
        touchedMask = dialog.touchedMask,
        deletingDate = deleting,
        onOpenEdit = { jamDate, seed ->
            if (!inFlight) {
                deleting = null
                dialog.open(jamDate, seed)
            }
        },
        onEdit = dialog::type,
        onCancelEdit = dialog::close,
        onSave = { jamDate, values ->
            if (dialog.isOpenFor(jamDate) && !inFlight) {
                dialog.close()
                launchWrite { edits.updateJam(jamDate, values.date, values.time, values.venue) }
            }
        },
        onRequestDelete = { jamDate ->
            if (!inFlight) {
                dialog.close()
                deleting = jamDate.toString()
            }
        },
        onCancelDelete = { deleting = null },
        onConfirmDelete = { jamDate, songs ->
            if (deleting == jamDate.toString() && !inFlight) {
                deleting = null
                launchWrite { edits.deleteJam(jamDate, songs) }
            }
        },
        onRetry = { entry ->
            if (!inFlight) {
                edits.dismissJamEdit(entry.id)
                launchWrite { edits.resend(entry) }
            }
        },
        retain = { dates ->
            val names = dates.map(LocalDate::toString)
            if (dialog.editingDate !in names) dialog.close()
            if (deleting !in names) deleting = null
        },
    )
}
