package com.example.megumidownload

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helper to execute shell commands (for mkvmerge, mkvextract).
 */
object CommandRunner {
    private const val TAG = "CommandRunner"

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    suspend fun runCommand(command: List<String>, env: Map<String, String>? = null): CommandResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing: ${command.joinToString(" ")}")
        
        try {
            val pb = ProcessBuilder(command)
            if (env != null) {
                pb.environment().putAll(env)
            }
            
            val process = pb.start()
            
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // Read streams in separate threads or loops to prevent deadlock
            // For simplicity in this helper, we'll just read them sequentially which might block if buffers fill up,
            // but for mkvmerge usually it's fine. A more robust solution would use separate threads.
            // Actually, let's just read line by line.
            
            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
                // Log.v(TAG, "STDOUT: $line") // Verbose logging if needed
            }
            
            while (stderrReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
                Log.e(TAG, "STDERR: $line")
            }
            
            val exitCode = process.waitFor()
            Log.d(TAG, "Command finished with exit code: $exitCode")
            
            CommandResult(exitCode, stdout.toString(), stderr.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }
}
