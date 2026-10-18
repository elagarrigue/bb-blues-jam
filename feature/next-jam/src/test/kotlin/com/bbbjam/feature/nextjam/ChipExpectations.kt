package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentChipUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName

/** The instrument strip chips a row expects, written out with the `:core:ui` resource ids (strip spec, C1). */

private fun abbreviation(instrument: Instrument) = UiText.res(
    when (instrument) {
        Instrument.GUITAR -> CoreR.string.instrument_guitar_abbr
        Instrument.BASS -> CoreR.string.instrument_bass_abbr
        Instrument.DRUMS -> CoreR.string.instrument_drums_abbr
        Instrument.VOCALS -> CoreR.string.instrument_vocals_abbr
        Instrument.HARMONICA -> CoreR.string.instrument_harmonica_abbr
        Instrument.KEYBOARDS -> CoreR.string.instrument_keyboards_abbr
    },
)

private fun abbreviationInCaps(instrument: Instrument) = UiText.res(
    when (instrument) {
        Instrument.GUITAR -> CoreR.string.instrument_guitar_abbr_caps
        Instrument.BASS -> CoreR.string.instrument_bass_abbr_caps
        Instrument.DRUMS -> CoreR.string.instrument_drums_abbr_caps
        Instrument.VOCALS -> CoreR.string.instrument_vocals_abbr_caps
        Instrument.HARMONICA -> CoreR.string.instrument_harmonica_abbr_caps
        Instrument.KEYBOARDS -> CoreR.string.instrument_keyboards_abbr_caps
    },
)

/** The strip chip of an open slot: "GTR: LIBRE", read "Guitarra: libre". */
internal fun openChip(instrument: Instrument) = InstrumentChipUiModel(
    UiText.res(CoreR.string.strip_open_label, abbreviationInCaps(instrument)),
    UiText.res(CoreR.string.strip_open_description, instrument.fullName()),
    InstrumentChipKind.OPEN_SLOT,
)

/** The strip chip of a filled slot: "Gtr: Tincho", read "Guitarra: Tincho". */
internal fun filledChip(instrument: Instrument, name: String) = InstrumentChipUiModel(
    UiText.res(CoreR.string.strip_filled_label, abbreviation(instrument), name),
    UiText.res(CoreR.string.strip_filled_description, instrument.fullName(), name),
    InstrumentChipKind.FILLED_SLOT,
)

/** The strip chip of an extra participant. */
internal fun extraChip(instrument: String, name: String) = InstrumentChipUiModel(
    UiText.res(CoreR.string.strip_extra_label, instrument, name),
    UiText.res(CoreR.string.strip_extra_description, instrument, name),
    InstrumentChipKind.EXTRA,
)
