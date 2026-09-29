package com.bbbjam.core.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

/**
 * Test-only sample of the presenter pattern, with no domain types: [titles] stands in for a
 * repository flow, `expanded` is local UI state, and the event handler writes through that state.
 */
sealed interface SampleUiModel : UiModel {
    data object Loading : SampleUiModel

    data class Data(val title: String, val expanded: Boolean, val events: EventHandler<Event>) : SampleUiModel {
        sealed interface Event : UiEvent {
            data object ToggleExpanded : Event
        }
    }
}

class SamplePresenter(private val titles: Flow<String>) : Presenter<SampleUiModel, Unit> {
    @Composable
    override fun present(params: Unit): SampleUiModel {
        val title by titles.collectAsState(initial = null)
        var expanded by remember { mutableStateOf(false) }

        val current = title ?: return SampleUiModel.Loading
        return SampleUiModel.Data(
            title = current,
            expanded = expanded,
            events = EventHandler { event ->
                when (event) {
                    SampleUiModel.Data.Event.ToggleExpanded -> expanded = !expanded
                }
            },
        )
    }
}
