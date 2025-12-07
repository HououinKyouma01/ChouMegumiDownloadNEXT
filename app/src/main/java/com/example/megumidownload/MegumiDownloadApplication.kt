package com.example.megumidownload

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MegumiDownloadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Register Bouncy Castle Provider for SSHJ/SFTP
        // Remove existing if any to avoid conflicts
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }
}
