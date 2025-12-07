package com.example.megumidownload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File

class SftpClientWrapper(
    private val host: String,
    private val port: Int = 22,
    private val username: String,
    private val keyPath: String? = null,
    private val password: String? = null
) {
    private val ssh = SSHClient()
    private var sftp: SFTPClient? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(host, port)
        
        if (keyPath != null) {
            ssh.authPublickey(username, keyPath)
        } else if (password != null) {
            ssh.authPassword(username, password)
        }
        
        sftp = ssh.newSFTPClient()
    }

    suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        sftp?.get(remotePath, localPath)
    }
    
    suspend fun listFiles(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        sftp?.ls(remotePath)?.map { it.name } ?: emptyList()
    }

    suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        sftp?.rm(remotePath)
    }

    fun disconnect() {
        sftp?.close()
        ssh.disconnect()
    }
}
