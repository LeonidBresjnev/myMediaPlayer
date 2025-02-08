package com.equalizer.carservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MyViewModel: ViewModel() {
    private val _activeInterval = MutableStateFlow(value=-1)

    val activeInterval: StateFlow<Int> = _activeInterval.asStateFlow()

    fun updateState(interval: Int) {
        viewModelScope.launch {
            _activeInterval.emit(interval)
            //Log.d("updateState","${activeInterval.value}")
        }
    }
}