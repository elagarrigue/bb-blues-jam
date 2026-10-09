package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.ExtraParticipant

internal fun extraRemoveAccessibilityLabel(extra: ExtraParticipant): String =
    "Quitar a ${extra.name}, ${extra.instrument}"
