package com.example.megumidownload

import kotlinx.coroutines.flow.StateFlow

interface PermissionHandler {
    val needsPermission: StateFlow<Boolean>
    fun hasStoragePermission(): Boolean
    fun requestStoragePermission()
    fun hasNotificationPermission(): Boolean
    fun requestNotificationPermission()
}
