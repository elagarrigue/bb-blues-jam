package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.bbbjam.core.data.musician.MusicianFilterPreference
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.filter.InstrumentFilterChange
import com.bbbjam.core.ui.filter.MusicianFilterChange
import com.bbbjam.core.ui.filter.updatedBy
import com.bbbjam.core.ui.presenter.EventHandler
import java.io.IOException
import kotlinx.coroutines.launch

/** Persistent selections, with one saveable mode deciding which selection may affect the list. */
internal data class NextJamFilterState(
    val instruments: Set<Instrument>,
    val onInstruments: (InstrumentFilterChange) -> Unit,
    val onMusician: (MusicianFilterChange) -> Unit,
    val mode: FilterModeUiModel,
)

internal fun initialFilterMode(musician: String?): JamFilterMode =
    if (musician == null) JamFilterMode.INSTRUMENT else JamFilterMode.MUSICIAN

@Composable
internal fun rememberNextJamFilters(
    musicianPreference: MusicianFilterPreference,
    musician: String?,
): NextJamFilterState {
    var filter by rememberSaveable(stateSaver = InstrumentFilterSaver) { mutableStateOf(emptySet<Instrument>()) }
    var selectedMode by rememberSaveable { mutableStateOf<JamFilterMode?>(null) }
    val onFilterChange: (InstrumentFilterChange) -> Unit = { change ->
        Snapshot.withMutableSnapshot {
            if (selectedMode != null || musician != null) selectedMode = JamFilterMode.INSTRUMENT
            filter = filter.updatedBy(change)
        }
    }
    val changeMusician = rememberMusicianChange(musicianPreference)
    val onMusicianChange: (MusicianFilterChange) -> Unit = { change ->
        selectedMode = JamFilterMode.MUSICIAN
        changeMusician(change)
    }
    return NextJamFilterState(
        filter,
        onFilterChange,
        onMusicianChange,
        FilterModeUiModel(selectedMode ?: initialFilterMode(musician), EventHandler { selectedMode = it.mode }),
    )
}

/**
 * Writes only the musician preference. Clearing this mode never clears the inactive instrument selection.
 * A write that fails leaves the selection as it was.
 */
@Composable
private fun rememberMusicianChange(preference: MusicianFilterPreference): (MusicianFilterChange) -> Unit {
    val scope = rememberCoroutineScope()
    return { change ->
        scope.launch {
            try {
                preference.select((change as? MusicianFilterChange.Select)?.name)
            } catch (_: IOException) {
                // The preference could not be written: the selection stays as it was.
            }
        }
    }
}

/** The selected instruments as their enum names: bundle-safe, so the filter survives rotation. */
internal val InstrumentFilterSaver: Saver<Set<Instrument>, Any> = listSaver(
    save = { selected -> selected.map { it.name } },
    restore = { names -> names.map { Instrument.valueOf(it) }.toSet() },
)
