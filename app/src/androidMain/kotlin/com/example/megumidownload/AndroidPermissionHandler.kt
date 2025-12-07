package com.example.megumidownload

import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.lifecycleScope

class AndroidPermissionHandler(
    private val activity: ComponentActivity
) : PermissionHandler {
    
    private val _needsPermission = MutableStateFlow(false)
    override val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()
    
    // Delegate to existing logic wrapper or implement here.
    // Since we have PermissionManager already, let's just wrap it.
    // Assuming PermissionManager is available in androidMain
    private val delegate = PermissionManager(activity)

    override fun hasStoragePermission(): Boolean {
        val hasIt = delegate.hasStoragePermission()
        _needsPermission.value = !hasIt
        return hasIt
    }

    override fun requestStoragePermission() {
        delegate.requestStoragePermission()
    }
    
    override fun hasNotificationPermission(): Boolean {
         // Android 13+
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
         }
         return true
    }
    
    override fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             androidx.core.app.ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                2001
            )
        }
    }
}
