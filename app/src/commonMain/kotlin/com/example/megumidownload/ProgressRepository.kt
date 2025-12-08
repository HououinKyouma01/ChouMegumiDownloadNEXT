package com.example.megumidownload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProgressRepository {
    // Processing State (Android / Global)
    private val _currentFile = MutableStateFlow("")
    val currentFile: StateFlow<String> = _currentFile.asStateFlow()

    private val _currentStep = MutableStateFlow("")
    val currentStep: StateFlow<String> = _currentStep.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _totalFiles = MutableStateFlow(0)
    val totalFiles: StateFlow<Int> = _totalFiles.asStateFlow()

    private val _processedFiles = MutableStateFlow(0)
    val processedFiles: StateFlow<Int> = _processedFiles.asStateFlow()

    // Download State (Desktop Optimization)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // Method for VideoProcessor (Step-based)
    fun updateProgress(fileName: String, step: String, progressValue: Float) {
        _currentFile.value = fileName
        _currentStep.value = step
        _progress.value = progressValue
    }

    // Method for Downloader (Byte-based)
    fun updateProgress(fileName: String, bytesRead: Long, totalBytes: Long) {
        _downloadState.value = DownloadState.Downloading(fileName, bytesRead, totalBytes)
        // Also update generic progress for Dashboard if needed?
        // Let's assume Dashboard uses `downloadState` for new logic or `progress` for old logic.
        // For now, keep them separate to avoid conflict, or sync them?
        // Let's sync primarily for the UI that watches generic progress:
        _currentFile.value = fileName
        _currentStep.value = "Downloading"
        _progress.value = if (totalBytes > 0) bytesRead.toFloat() / totalBytes.toFloat() else 0f
    }

    fun setTotalFiles(count: Int) {
        _totalFiles.value = count
    }

    fun incrementProcessedFiles() {
        _processedFiles.value += 1
    }

    fun setIdle() {
        _currentFile.value = ""
        _currentStep.value = ""
        _progress.value = 0f
        _downloadState.value = DownloadState.Idle
    }
    
    fun reset() {
        setIdle()
        _totalFiles.value = 0
        _processedFiles.value = 0
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val fileName: String, val currentBytes: Long, val totalBytes: Long) : DownloadState() {
             val progress: Float
                 get() = if (totalBytes > 0) currentBytes.toFloat() / totalBytes.toFloat() else 0f
        }
    }
}
