package com.bbbjam.feature.nextjam.assign

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.admin.AdminControlsDefaults

/** Presentation rules of the fixed-slot assignment flow, apart from the composable so a JVM test can check them. */
internal object AssignMusicianDefaults {
    /** "Guitarra 2", "Bajo": the instrument's approved name, numbered only for guitars. */
    fun slotName(instrument: Instrument, ordinal: Int): UiText = if (instrument == Instrument.GUITAR) {
        UiText.res(R.string.assign_musician_slot_numbered, instrument.fullName(), ordinal)
    } else {
        instrument.fullName()
    }

    /**
     * The name field, set explicitly from tokens (D-17) with the admin login's roles: focused border
     * `text`, unfocused `border`, label `textMuted`, container `surface`. No amber.
     */
    fun field(colors: BluesJamColors = BluesJamColors): AdminControlsDefaults.FieldStyle =
        AdminControlsDefaults.extraField(colors)
}
