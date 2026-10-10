package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.strip.fullName
import com.bbbjam.core.ui.theme.BluesJamColors

/** Presentation rules of the fixed-slot assignment flow, apart from the composable so a JVM test can check them. */
internal object AssignMusicianDefaults {
    /** "Guitarra 2", "Bajo": the instrument's approved name, numbered only for guitars. */
    fun slotName(instrument: Instrument, ordinal: Int): String =
        "${instrument.fullName()}${if (instrument == Instrument.GUITAR) " $ordinal" else ""}"

    /**
     * The name field, set explicitly from tokens (D-17) with the admin login's roles: focused border
     * `text`, unfocused `border`, label `textMuted`, container `surface`. No amber.
     */
    fun field(colors: BluesJamColors = BluesJamColors): AdminControlsDefaults.FieldStyle =
        AdminControlsDefaults.extraField(colors)
}
