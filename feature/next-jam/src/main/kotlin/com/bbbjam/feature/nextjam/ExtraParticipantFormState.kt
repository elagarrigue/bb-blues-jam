package com.bbbjam.feature.nextjam

import androidx.compose.runtime.MutableState
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class ExtraParticipantFormState(
    private val formKey: MutableState<String?>,
    private val name: MutableState<String>,
    private val instrument: MutableState<String>,
    private val consumed: MutableState<Boolean>,
) {
    fun handle(
        date: LocalDate,
        songId: SongId,
        event: ExtraParticipantEditorUiModel.Event,
        setlist: SetlistRepository,
        scope: CoroutineScope,
    ) {
        val key = removalKey(date, songId)
        when (event) {
            ExtraParticipantEditorUiModel.Event.Open -> open(key)

            is ExtraParticipantEditorUiModel.Event.NameChanged -> if (formKey.value == key) name.value = event.value

            is ExtraParticipantEditorUiModel.Event.InstrumentChanged -> if (formKey.value ==
                key
            ) {
                instrument.value = event.value
            }

            ExtraParticipantEditorUiModel.Event.Submit -> submit(date, songId, key, setlist, scope)

            is ExtraParticipantEditorUiModel.Event.Remove -> scope.launch(start = CoroutineStart.UNDISPATCHED) {
                setlist.removeExtraParticipant(date, songId, event.ordinal, event.name, event.instrument)
            }
        }
    }

    private fun open(key: String) {
        formKey.value = key
        name.value = ""
        instrument.value = ""
        consumed.value = false
    }

    private fun submit(
        date: LocalDate,
        songId: SongId,
        key: String,
        setlist: SetlistRepository,
        scope: CoroutineScope,
    ) {
        if (formKey.value != key || consumed.value) return
        consumed.value = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            setlist.addExtraParticipant(date, songId, name.value, instrument.value)
            if (formKey.value == key) formKey.value = null
            consumed.value = false
        }
    }
}
