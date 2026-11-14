package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bbbjam.core.ui.filter.InstrumentFilterBar
import com.bbbjam.core.ui.filter.MusicianFilterBar
import com.bbbjam.core.ui.theme.BluesJamTheme

/** One two-segment mode control, followed by only the active filter's controls and results. */
@Composable
internal fun NextJamFilters(model: SetlistUiModel.Songs) {
    Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            JamFilterMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = model.filterMode.selected == mode,
                    onClick = { model.filterMode.events(FilterModeUiModel.Event(mode)) },
                    modifier = Modifier.weight(1f).heightIn(min = LocalMinimumInteractiveComponentSize.current),
                    shape = SegmentedButtonDefaults.itemShape(
                        index,
                        JamFilterMode.entries.size,
                        BluesJamTheme.shapes.lg,
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = BluesJamTheme.colors.surfaceRaised,
                        activeContentColor = BluesJamTheme.colors.text,
                        activeBorderColor = BluesJamTheme.colors.text,
                        inactiveContainerColor = BluesJamTheme.colors.surface,
                        inactiveContentColor = BluesJamTheme.colors.textMuted,
                        inactiveBorderColor = BluesJamTheme.colors.border,
                    ),
                    label = {
                        Text(
                            stringResource(
                                if (mode ==
                                    JamFilterMode.INSTRUMENT
                                ) {
                                    R.string.filter_mode_instrument
                                } else {
                                    R.string.filter_mode_musician
                                },
                            ),
                            style = BluesJamTheme.typography.body,
                        )
                    },
                )
            }
        }
        model.filterBar?.let { InstrumentFilterBar(it, showHeading = false) }
        model.musicianFilter?.let {
            if (it.options.isEmpty() && it.selectedName == null) {
                Text(
                    stringResource(R.string.filter_no_musicians),
                    style = BluesJamTheme.typography.body,
                    color = BluesJamTheme.colors.textMuted,
                )
            } else {
                MusicianFilterBar(it, showHeading = false)
            }
        }
    }
}
