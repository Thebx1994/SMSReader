package com.example.smsreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsreader.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _searchString = MutableStateFlow("")
    val searchString = _searchString.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring = _isMonitoring.asStateFlow()

    private val _showDbSettings = MutableStateFlow(false)
    val showDbSettings = _showDbSettings.asStateFlow()

    private val _dbUrl = MutableStateFlow("")
    val dbUrl = _dbUrl.asStateFlow()

    private val _dbUser = MutableStateFlow("")
    val dbUser = _dbUser.asStateFlow()

    private val _dbPassword = MutableStateFlow("")
    val dbPassword = _dbPassword.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    fun updateUsername(value: String) {
        _username.value = value
    }

    fun updateSearchString(value: String) {
        _searchString.value = value
    }

    fun toggleMonitoring() {
        _isMonitoring.value = !_isMonitoring.value
    }

    fun toggleDbSettings() {
        _showDbSettings.value = !_showDbSettings.value
    }

    fun updateDbUrl(value: String) {
        _dbUrl.value = value
    }

    fun updateDbUser(value: String) {
        _dbUser.value = value
    }

    fun updateDbPassword(value: String) {
        _dbPassword.value = value
    }

    fun addLogEntry(entry: LogEntry) {
        viewModelScope.launch {
            _logEntries.value = listOf(entry) + _logEntries.value
        }
    }
}