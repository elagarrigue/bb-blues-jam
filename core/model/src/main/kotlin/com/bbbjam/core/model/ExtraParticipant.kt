package com.bbbjam.core.model

/**
 * Someone playing a [JamSong] outside its [Lineup] ("Otros" in the UI), with a free-text
 * [instrument] such as `saxo`. Never open and never counted by the open-slot rules (D-18). Neither
 * field may be blank or contain `;`, `(` or `)`, the separators of the Sheet's `Otros` cell.
 */
data class ExtraParticipant(val name: String, val instrument: String) {
    init {
        require(name.isNotBlank()) { "Blank extra participant name \"$name\"" }
        require(instrument.isNotBlank()) { "Blank instrument \"$instrument\" for extra participant $name" }
        require(name.none { it in RESERVED }) { "Extra participant name \"$name\" contains one of ; ( )" }
        require(instrument.none { it in RESERVED }) {
            "Extra participant instrument \"$instrument\" contains one of ; ( )"
        }
    }

    private companion object {
        const val RESERVED = ";()"
    }
}
