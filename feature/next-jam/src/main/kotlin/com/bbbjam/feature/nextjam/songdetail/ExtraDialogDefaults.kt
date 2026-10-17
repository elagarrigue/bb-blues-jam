package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.nextjam.admin.AdminControlsDefaults

/**
 * The "Agregar a Otros" dialog's colours (`extras-dialog-and-lines`): `surfaceRaised` container,
 * title, "Agregar" and "Cancelar" in `text` (adding is not destructive: never `error`, never amber),
 * a disabled "Agregar" in `textMuted`. The fields reuse [AdminControlsDefaults.extraField].
 */
internal object ExtraDialogDefaults {
    data class Style(
        val container: Color,
        val title: Color,
        val confirm: Color,
        val disabled: Color,
        val cancel: Color,
    )

    fun style(colors: BluesJamColors = BluesJamColors): Style = Style(
        container = colors.surfaceRaised,
        title = colors.text,
        confirm = colors.text,
        disabled = colors.textMuted,
        cancel = colors.text,
    )
}
