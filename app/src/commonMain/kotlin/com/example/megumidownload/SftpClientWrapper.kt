package com.example.megumidownload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.TransferListener
import net.schmizz.sshj.common.StreamCopier
import java.io.File

class SftpClientWrapper(
    private val host: String,
    private val port: Int = 22,
    private val username: String,
    private val keyPath: String? = null,
    private val password: String? = null
) {
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            Logger.d("SftpWrapper", "Initializing SSHClient...")
            
            // Optimize Config for Speed (Window Size is key)
            val config = net.schmizz.sshj.DefaultConfig()
            // config.windowSize and maxPacketSize are not directly accessible in this version
            // relying on default large window of modern SSHJ or FileTransfer pipelining
            
            val client = SSHClient(config)
            client.addHostKeyVerifier(PromiscuousVerifier())
            
            // Optimization: Compression is disabled by default in SSHJ, enabling it requires calling useCompression()
            // So we simply do nothing here to ensure it's off.
            
            Logger.d("SftpWrapper", "Connecting to $host:$port...")
            client.connect(host, port)
            
            Logger.d("SftpWrapper", "Authenticating...")
            if (password != null) {
                client.authPassword(username, password)
            } else if (keyPath != null) {
                client.authPublickey(username, keyPath)
            } else {
                throw IllegalArgumentException("Either password or keyPath must be provided.")
            }
            
            ssh = client
            sftp = client.newSFTPClient()
            Logger.d("SftpWrapper", "SFTP Client created.")
        } catch (e: Exception) {
            Logger.e("SftpWrapper", "Connection failed", e)
            disconnect() // Cleanup if half-initialized
            throw e
        }
    }

    suspend fun downloadFile(remotePath: String, localPath: String, onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val sftpClient = sftp ?: throw IllegalStateException("SFTP not initialized")
        
        Logger.d("SftpWrapper", "Stat remote file: $remotePath")
        val attr = sftpClient.stat(remotePath) ?: throw Exception("Failed to stat remote file $remotePath")
        val totalSize = attr.size
        Logger.d("SftpWrapper", "Remote size: $totalSize bytes")
        
        try {
            Logger.d("SftpWrapper", "Starting optimized native transfer: $remotePath")
            
            val transfer = sftpClient.fileTransfer
            val listener = object : TransferListener {
                override fun directory(name: String?): TransferListener {
                    return this
                }
                override fun file(name: String?, size: Long): StreamCopier.Listener {
                    return object : StreamCopier.Listener {
                        override fun reportProgress(transferred: Long) {
                            onProgress(transferred, totalSize)
                        }
                    }
                }
            }

            transfer.transferListener = listener
            transfer.download(remotePath, localPath)
            
            Logger.d("SftpWrapper", "Transfer finished.")
            onProgress(totalSize, totalSize)
            
        } catch (e: Exception) {
            Logger.e("SftpWrapper", "Transfer failed", e)
            throw e
        }
    }
    
    suspend fun listFiles(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        sftp?.ls(remotePath)?.map { it.name } ?: emptyList()
    }

    suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        sftp?.rm(remotePath)
    }

    fun disconnect() {
        try {
            sftp?.close()
        } catch (e: Exception) { /* ignore */ }
        
        try {
            ssh?.disconnect()
        } catch (e: Exception) { /* ignore */ }
        
        sftp = null
        ssh = null
    }
}
