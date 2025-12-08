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
            Logger.i("DesktopWebEngine", "KCEF: Pre-initializing from ${MODULE_DIR.absolutePath}")
            try {
                kotlinx.coroutines.runBlocking {
                    withContext(Dispatchers.Main) {
                        initInternal(false) 
                    }
                }
            } catch(e: Exception) {
                Logger.e("DesktopWebEngine", "Startup failed", e)
                e.printStackTrace()
            }
        }
    }

    suspend fun init() {
         initInternal(true)
    }

    private suspend fun initInternal(isDynamicDownload: Boolean) {
        if (_state.value is State.Ready || _state.value is State.Initializing || _state.value is State.Downloading || _state.value is State.RestartRequired) {
             Logger.d("DesktopWebEngine", "Already initializing/ready: ${_state.value}")
             return
        }
        
        _state.value = State.Initializing
        Logger.i("DesktopWebEngine", "Initializing KCEF (Dynamics: $isDynamicDownload)...")
        
        withContext(Dispatchers.Main) {
            try {
                val kcefDir = MODULE_DIR
                
                KCEF.init(
                    builder = {
                        installDir(kcefDir)
                        progress {
                            onDownloading { p ->
                                _state.value = State.Downloading(p)
                                Logger.d("DesktopWebEngine", "Downloading: $p")
                            }
                        }
                        settings {
                            cachePath = kcefDir.absolutePath
                            noSandbox = true
                        }
                    },
                    onError = {
                        if (it != null) {
                            Logger.e("DesktopWebEngine", "KCEF Error: ${it.message}", it)
                            _state.value = State.Error(it.message ?: "Unknown KCEF Error")
                        }
                    },
                    onRestartRequired = {
                        Logger.w("DesktopWebEngine", "Restart Required")
                        _state.value = State.RestartRequired
                    }
                )
                  
                 Logger.i("DesktopWebEngine", "KCEF Init called. Checking client...")
                 
                 // Add small delay to allow CefApp to settle?
                 // kotlinx.coroutines.delay(500) 
                 
                 if (KCEF.newClientOrNull() != null) {
                     Logger.i("DesktopWebEngine", "KCEF Client created successfully. Ready.")
                     _state.value = State.Ready
                 } else {
                     Logger.w("DesktopWebEngine", "KCEF Client creation returned null.")
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
                Logger.e("DesktopWebEngine", "Exception during Init", e)
                _state.value = State.Error(e.message ?: "Initialization Failed")
                e.printStackTrace()
            }
        }
    }
    
    fun isReady(): Boolean {
        return _state.value is State.Ready
    }
}
