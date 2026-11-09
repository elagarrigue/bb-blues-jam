package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.musician.MusicianFilterPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [MusicianFilterPreference]: [stored] is what the device remembers; [selections] records each write. */
class FakeMusicianFilterPreference(initial: String? = null) : MusicianFilterPreference {
    val stored = MutableStateFlow(initial?.takeUnless { it.isBlank() })
    val selections = mutableListOf<String?>()

    override fun observeSelected(): Flow<String?> = stored

    override suspend fun select(name: String?) {
        selections += name
        stored.value = name?.takeUnless { it.isBlank() }
    }
}
