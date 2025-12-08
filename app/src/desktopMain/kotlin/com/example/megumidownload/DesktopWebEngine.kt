package com.example.megumidownload

import dev.datlag.kcef.KCEF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DesktopWebEngine {
    sealed class State {
        object NotInitialized : State()
        object Initializing : State()
        data class Downloading(val progress: Float) : State()
        object Ready : State()
        object RestartRequired : State()
        data class Error(val message: String) : State()
    }
    
    val MODULE_DIR = File(File(System.getProperty("user.home"), ".megumidownload"), "kcef-bundle")

    private val _state = MutableStateFlow<State>(State.NotInitialized)
    val state: StateFlow<State> = _state

    fun startup() {
        // Called from main(), runs blocking if exists
        if (MODULE_DIR.exists() && MODULE_DIR.list()?.isNotEmpty() == true) {
            println("KCEF: Pre-initializing from ${MODULE_DIR.absolutePath}")
            // We use a simple blocking approach or runblocking, but KCEF.init is async-ish builder.
            // However, we just need to kick it off.
            // On Linux, this must happen on Main thread context if possible? 
            // In main(), we are in main thread.
            
           // We can't suspend here easily without runBlocking.
           // Let's use GlobalScope or just let it init? 
           // Better: Use runBlocking to ensure it registers before UI.
           try {
               kotlinx.coroutines.runBlocking {
                   withContext(Dispatchers.Main) {
                       initInternal(false) 
                   }
               }
           } catch(e: Exception) {
               e.printStackTrace()
           }
        }
    }

    suspend fun init() {
         initInternal(true)
    }

    private suspend fun initInternal(isDynamicDownload: Boolean) {
        if (_state.value is State.Ready || _state.value is State.Initializing || _state.value is State.Downloading || _state.value is State.RestartRequired) return
        
        // If this is dynamic download (app already running), we might fail if we try to load libs now.
        // We will download, but then set state to RestartRequired if it's linux?
        // Actually, let's try to proceed. KCEF handles download.
        
        _state.value = State.Initializing
        
        withContext(Dispatchers.Main) {
            try {
                val kcefDir = MODULE_DIR
                
                KCEF.init(
                    builder = {
                        installDir(kcefDir)
                        progress {
                            onDownloading { p ->
                                _state.value = State.Downloading(p)
                            }
                        }
                        settings {
                            cachePath = kcefDir.absolutePath
                            noSandbox = true
                        }
                    },
                    onError = {
                        if (it != null) {
                            _state.value = State.Error(it.message ?: "Unknown KCEF Error")
                        }
                    },
                    onRestartRequired = {
                        _state.value = State.RestartRequired
                    }
                )
                  
                 if (KCEF.newClientOrNull() != null) {
                     _state.value = State.Ready
                 } else {
                     // If we just downloaded it (isDynamicDownload), we likely need a restart on Linux
                     // to pick up the LD_LIBRARY_PATH or similar context changes, OR simply because of the threading issue.
                     if (isDynamicDownload && System.getProperty("os.name").contains("Linux", ignoreCase = true)) {
                          _state.value = State.RestartRequired
                     } else {
                          // Try one more check
                          if (kcefDir.exists()) _state.value = State.Ready
                     }
                 }
                 
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Initialization Failed")
                e.printStackTrace()
            }
        }
    }
    
    fun isReady(): Boolean {
        return _state.value is State.Ready
    }
}
