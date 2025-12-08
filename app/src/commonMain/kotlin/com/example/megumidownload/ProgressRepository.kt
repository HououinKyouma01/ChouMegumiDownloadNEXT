package com.example.megumidownload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    // Map of FileName -> Progress (0.0 to 1.0)
    private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Float>> = _activeDownloads.asStateFlow()

    // Map of FileName -> Speed (Bytes per second)
    private val _activeSpeed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val activeSpeed: StateFlow<Map<String, Long>> = _activeSpeed.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // Method for VideoProcessor (Step-based)
    fun updateProgress(fileName: String, step: String, progressValue: Float) {
        _currentFile.value = fileName
        _currentStep.value = step
        _progress.value = progressValue
    }

    // Method for Downloader (Byte-based)
    fun updateProgress(fileName: String, bytesRead: Long, totalBytes: Long, updatePrimary: Boolean = true) {
        if (updatePrimary) {
            _downloadState.value = DownloadState.Downloading(fileName, bytesRead, totalBytes)
        }
        
        val progressFloat = if (totalBytes > 0) bytesRead.toFloat() / totalBytes.toFloat() else 0f
        
        // Update active downloads map (Atomic update)
        _activeDownloads.update { current ->
            val newMap = current.toMutableMap()
            newMap[fileName] = progressFloat
            newMap.toMap()
        }

        // Update generic single-file tracking (backward compatibility or main status)
        if (updatePrimary) {
            _currentFile.value = fileName
            _currentStep.value = "Downloading"
            _progress.value = progressFloat
        }
    }

    fun updateSpeed(fileName: String, speed: Long) {
        _activeSpeed.update { current ->
            val newMap = current.toMutableMap()
            newMap[fileName] = speed
            newMap.toMap()
        }
    }
    
    fun startDownload(fileName: String) {
        _activeDownloads.update { current ->
            val newMap = current.toMutableMap()
            newMap[fileName] = 0f
            newMap.toMap()
        }
        // Init speed
        _activeSpeed.update { current ->
            val newMap = current.toMutableMap()
            newMap[fileName] = 0L
            newMap.toMap()
        }
    }
    
    fun endDownload(fileName: String) {
        _activeDownloads.update { current ->
            val newMap = current.toMutableMap()
            newMap.remove(fileName)
            newMap.toMap()
        }
        _activeSpeed.update { current ->
            val newMap = current.toMutableMap()
            newMap.remove(fileName)
            newMap.toMap()
        }
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
        _activeDownloads.value = emptyMap()
        _activeSpeed.value = emptyMap()
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
