package com.bbbjam.core.model

/**
 * An instrument a [Slot] can hold. The default [Lineup] has two [GUITAR] slots and one of each
 * other value; "two guitars" is two slots, not two instruments.
 */
enum class Instrument {
    GUITAR,
    BASS,
    DRUMS,
    VOCALS,
    HARMONICA,
    KEYBOARDS,
}
