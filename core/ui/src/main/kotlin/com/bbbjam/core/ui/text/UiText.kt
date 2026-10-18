package com.bbbjam.core.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * User-facing text in a `UiModel`. Copy lives in `strings.xml` (Rioplatense Spanish, D-12); a
 * presenter names the resource and its arguments, and the screen resolves it. Presenters stay
 * Android-free: a JVM test compares [UiText] values, never resolved strings.
 *
 * Arguments may themselves be [UiText] (an instrument name inside a sentence); they are resolved
 * first. [Raw] carries data that is not copy (a song title, a musician's name).
 */
@Immutable
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    data class Raw(val value: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

        fun raw(value: String): UiText = Raw(value)
    }
}

/** Resolves this text outside composition (a semantics block, a callback). */
fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) {
        resources.getString(id)
    } else {
        resources.getString(id, *args.resolvedWith(resources))
    }

    is UiText.Plural -> resources.getQuantityString(id, count, *args.resolvedWith(resources))

    is UiText.Raw -> value
}

/** Resolves this text in a composable, recomposing on a configuration change. */
@Composable
fun UiText.resolve(): String {
    // Reading the configuration subscribes the caller to locale and resource changes.
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

private fun List<Any>.resolvedWith(resources: Resources): Array<Any> =
    map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
