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
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            delegate.hasStoragePermission()
        }
    }

    override fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = android.net.Uri.parse(String.format("package:%s", activity.packageName))
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            delegate.requestStoragePermission()
        }
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

    override fun checkPermissions() {
        val storage = hasStoragePermission()
        // val notification = hasNotificationPermission() // Not tracking notification in flow currently, but could
        _needsPermission.value = !storage
    }
}
