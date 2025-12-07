package com.example.megumidownload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DesktopPermissionHandler : PermissionHandler {
    private val _needsPermission = MutableStateFlow(false)
    override val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    override fun hasStoragePermission(): Boolean = true
    override fun requestStoragePermission() {}
    
    override fun hasNotificationPermission(): Boolean = true
    override fun requestNotificationPermission() {}
}
