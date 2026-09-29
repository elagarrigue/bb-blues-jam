package com.bbbjam.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The `rounded` tokens of `DESIGN.md`, read through `BluesJamTheme.shapes`. */
object BluesJamShapes {
    val sm: CornerBasedShape = RoundedCornerShape(4.dp)
    val md: CornerBasedShape = RoundedCornerShape(8.dp)
    val lg: CornerBasedShape = RoundedCornerShape(12.dp)
}

/** The `spacing` tokens of `DESIGN.md`, read through `BluesJamTheme.spacing`. */
object BluesJamSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
}
