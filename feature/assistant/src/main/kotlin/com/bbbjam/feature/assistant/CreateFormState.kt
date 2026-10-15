package com.bbbjam.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.ui.jamform.JamFormField

/** What the open "Crear una jam nueva con esta lista" form holds: its turn, the typed texts, the edited fields. */
internal data class CreateFormInputs(val turnId: Long, val input: JamFormInput, val touchedMask: Int) {
    /** True once the admin typed in [field] since the form opened (a blank field is no error before that). */
    fun isTouched(field: JamFormField): Boolean = touchedMask and field.bit != 0
}

private val JamFormField.bit: Int
    get() = 1 shl ordinal

/**
 * The create form's state (`assistant-proposal-targets`, C3): the open turn id, the three texts and which
 * fields were edited, all saveable, so the form survives a rotation. It decides nothing: the handlers open
 * it, edit it and close it, and a submit re-checks the latest snapshot first.
 */
internal class CreateFormState(
    private val turn: MutableState<Long?>,
    private val date: MutableState<String>,
    private val time: MutableState<String>,
    private val venue: MutableState<String>,
    private val touched: MutableState<Int>,
) {
    /** The open form, or null when none is open. */
    val open: CreateFormInputs?
        get() = turn.value?.let {
            CreateFormInputs(it, JamFormInput(date.value, time.value, venue.value), touched.value)
        }

    fun open(turnId: Long, seed: JamFormInput) {
        date.value = seed.date
        time.value = seed.time
        venue.value = seed.venue
        touched.value = 0
        turn.value = turnId
    }

    fun edit(field: JamFormField, text: String) {
        when (field) {
            JamFormField.DATE -> date.value = text
            JamFormField.TIME -> time.value = text
            JamFormField.VENUE -> venue.value = text
        }
        touched.value = touched.value or field.bit
    }

    fun close() {
        turn.value = null
    }
}

@Composable
internal fun rememberCreateFormState(): CreateFormState {
    val turn = rememberSaveable { mutableStateOf<Long?>(null) }
    val date = rememberSaveable { mutableStateOf("") }
    val time = rememberSaveable { mutableStateOf("") }
    val venue = rememberSaveable { mutableStateOf("") }
    val touched = rememberSaveable { mutableStateOf(0) }
    return remember { CreateFormState(turn, date, time, venue, touched) }
}
