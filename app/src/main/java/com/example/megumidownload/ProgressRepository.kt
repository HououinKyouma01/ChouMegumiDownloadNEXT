package com.example.megumidownload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProgressRepository {
    private val _currentFile = MutableStateFlow("")
    val currentFile: StateFlow<String> = _currentFile.asStateFlow()

    private val _currentStep = MutableStateFlow("")
    val currentStep: StateFlow<String> = _currentStep.asStateFlow()

    private val _progress = MutableStateFlow(0f) // 0.0 to 1.0
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _totalFiles = MutableStateFlow(0)
    val totalFiles: StateFlow<Int> = _totalFiles.asStateFlow()

    private val _processedFiles = MutableStateFlow(0)
    val processedFiles: StateFlow<Int> = _processedFiles.asStateFlow()

    fun updateProgress(file: String, step: String, progressValue: Float) {
        _currentFile.value = file
        _currentStep.value = step
        _progress.value = progressValue
    }

    fun setTotalFiles(count: Int) {
        _totalFiles.value = count
        _processedFiles.value = 0
    }

    fun incrementProcessedFiles() {
        _processedFiles.value += 1
    }
    
    fun reset() {
        _currentFile.value = ""
        _currentStep.value = ""
        _progress.value = 0f
        _totalFiles.value = 0
        _processedFiles.value = 0
    }
}
