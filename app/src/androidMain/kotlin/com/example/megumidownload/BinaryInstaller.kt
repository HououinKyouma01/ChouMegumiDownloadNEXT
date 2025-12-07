package com.example.megumidownload

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Helper class to install external binaries (mkvmerge, mkvextract) from assets to internal storage.
 */
object BinaryInstaller {
    private const val TAG = "BinaryInstaller"
    private val BINARIES = listOf("mkvmerge", "mkvextract")

    /**
     * Extracts binaries from assets to the app's internal files directory if they don't exist.
     * Also sets the executable permission.
     */
    fun installBinaries(context: Context) {
        val filesDir = context.filesDir
        
        for (binaryName in BINARIES) {
            val destinationFile = File(filesDir, binaryName)
            
            // In a real app, you might want to check for version updates or checksums.
            // For now, we just check if it exists.
            if (!destinationFile.exists()) {
                Log.d(TAG, "Installing binary: $binaryName")
                try {
                    copyAssetToFile(context, binaryName, destinationFile)
                    setExecutable(destinationFile)
                    Log.d(TAG, "Successfully installed: $binaryName")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to install binary: $binaryName", e)
                }
            } else {
                Log.d(TAG, "Binary already exists: $binaryName")
                // Ensure it is executable even if it exists
                if (!destinationFile.canExecute()) {
                    setExecutable(destinationFile)
                }
            }
        }
    }

    private fun copyAssetToFile(context: Context, assetName: String, destinationFile: File) {
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
    }

    private fun setExecutable(file: File) {
        // Try using Java API first
        if (!file.setExecutable(true, true)) {
            // Fallback to shell command if Java API fails (rare on modern Android for internal storage)
            try {
                Runtime.getRuntime().exec("chmod 700 ${file.absolutePath}").waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to chmod ${file.absolutePath}", e)
            }
        }
    }
    
    fun getBinaryPath(context: Context, binaryName: String): String {
        return File(context.filesDir, binaryName).absolutePath
    }
}
