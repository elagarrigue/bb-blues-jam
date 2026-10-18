package com.bbbjam.core.ui.text

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.R

/** "Guitarra": the full instrument name, shared by the strip, the lineup panel and the filter. */
fun Instrument.fullName(): UiText = UiText.res(
    when (this) {
        Instrument.GUITAR -> R.string.instrument_guitar
        Instrument.BASS -> R.string.instrument_bass
        Instrument.DRUMS -> R.string.instrument_drums
        Instrument.VOCALS -> R.string.instrument_vocals
        Instrument.HARMONICA -> R.string.instrument_harmonica
        Instrument.KEYBOARDS -> R.string.instrument_keyboards
    },
)

/** "guitarra": the name in lower case, for inside a sentence. */
fun Instrument.lowercaseName(): UiText = UiText.res(
    when (this) {
        Instrument.GUITAR -> R.string.instrument_guitar_lower
        Instrument.BASS -> R.string.instrument_bass_lower
        Instrument.DRUMS -> R.string.instrument_drums_lower
        Instrument.VOCALS -> R.string.instrument_vocals_lower
        Instrument.HARMONICA -> R.string.instrument_harmonica_lower
        Instrument.KEYBOARDS -> R.string.instrument_keyboards_lower
    },
)

internal fun Instrument.abbreviation(): UiText = UiText.res(
    when (this) {
        Instrument.GUITAR -> R.string.instrument_guitar_abbr
        Instrument.BASS -> R.string.instrument_bass_abbr
        Instrument.DRUMS -> R.string.instrument_drums_abbr
        Instrument.VOCALS -> R.string.instrument_vocals_abbr
        Instrument.HARMONICA -> R.string.instrument_harmonica_abbr
        Instrument.KEYBOARDS -> R.string.instrument_keyboards_abbr
    },
)

internal fun Instrument.abbreviationInCaps(): UiText = UiText.res(
    when (this) {
        Instrument.GUITAR -> R.string.instrument_guitar_abbr_caps
        Instrument.BASS -> R.string.instrument_bass_abbr_caps
        Instrument.DRUMS -> R.string.instrument_drums_abbr_caps
        Instrument.VOCALS -> R.string.instrument_vocals_abbr_caps
        Instrument.HARMONICA -> R.string.instrument_harmonica_abbr_caps
        Instrument.KEYBOARDS -> R.string.instrument_keyboards_abbr_caps
    },
)
