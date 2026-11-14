package com.bbbjam.core.ui.jamform

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Neutral selection colours: date and time are admin form controls, never amber actions (D-17). */
@OptIn(ExperimentalMaterial3Api::class)
internal object JamPickerDefaults {
    @Composable
    fun dateColors() = DatePickerDefaults.colors(
        containerColor = BluesJamTheme.colors.surfaceRaised,
        titleContentColor = BluesJamTheme.colors.textMuted,
        headlineContentColor = BluesJamTheme.colors.text,
        weekdayContentColor = BluesJamTheme.colors.textMuted,
        subheadContentColor = BluesJamTheme.colors.text,
        navigationContentColor = BluesJamTheme.colors.text,
        yearContentColor = BluesJamTheme.colors.text,
        currentYearContentColor = BluesJamTheme.colors.text,
        selectedYearContentColor = BluesJamTheme.colors.background,
        selectedYearContainerColor = BluesJamTheme.colors.text,
        dayContentColor = BluesJamTheme.colors.text,
        selectedDayContentColor = BluesJamTheme.colors.background,
        selectedDayContainerColor = BluesJamTheme.colors.text,
        todayContentColor = BluesJamTheme.colors.text,
        todayDateBorderColor = BluesJamTheme.colors.text,
        dividerColor = BluesJamTheme.colors.border,
    )

    @Composable
    fun timeColors() = TimePickerDefaults.colors(
        clockDialColor = BluesJamTheme.colors.surface,
        clockDialSelectedContentColor = BluesJamTheme.colors.background,
        clockDialUnselectedContentColor = BluesJamTheme.colors.text,
        selectorColor = BluesJamTheme.colors.text,
        containerColor = BluesJamTheme.colors.surfaceRaised,
        timeSelectorSelectedContainerColor = BluesJamTheme.colors.text,
        timeSelectorSelectedContentColor = BluesJamTheme.colors.background,
        timeSelectorUnselectedContainerColor = BluesJamTheme.colors.surface,
        timeSelectorUnselectedContentColor = BluesJamTheme.colors.text,
    )
}
