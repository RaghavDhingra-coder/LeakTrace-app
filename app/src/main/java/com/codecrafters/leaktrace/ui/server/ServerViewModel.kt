package com.codecrafters.leaktrace.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.codecrafters.leaktrace.LeakTraceApplication
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.data.server.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerUiState(
    val baseUrl: String,
    val isChecking: Boolean = false,
    /** Last completed check; kept while a new check runs. */
    val report: ServerReport? = null,
) {
    fun availability(feature: BackendFeature): FeatureAvailability? = report?.features?.get(feature)
}

/** Activity-scoped: every screen reads the same server check. */
class ServerViewModel(private val repository: ServerRepository) : ViewModel() {
    private val _state = MutableStateFlow(ServerUiState(baseUrl = repository.baseUrl))
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isChecking) return
        _state.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val report = repository.check()
            _state.update { it.copy(isChecking = false, report = report) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LeakTraceApplication
                ServerViewModel(app.container.serverRepository)
            }
        }
    }
}
