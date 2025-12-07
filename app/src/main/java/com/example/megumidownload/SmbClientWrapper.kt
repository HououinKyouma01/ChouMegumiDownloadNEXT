package com.example.megumidownload

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2CreateOptions
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmbClientWrapper {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    suspend fun connect(host: String, user: String, pass: String, shareName: String) = withContext(Dispatchers.IO) {
        try {
            client = SMBClient()
            connection = client?.connect(host)
            val authContext = AuthenticationContext(user, pass.toCharArray(), "")
            session = connection?.authenticate(authContext)
            share = session?.connectShare(shareName) as? DiskShare
            if (share == null) throw Exception("Could not connect to share: $shareName")
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            share?.close()
            session?.close()
            connection?.close()
            client?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            share = null
            session = null
            connection = null
            client = null
        }
    }

    suspend fun listFiles(path: String): List<SmbFile> = withContext(Dispatchers.IO) {
        val s = share ?: throw IllegalStateException("Not connected")
        // Ensure path doesn't start with / or \ as SMBJ might not like it relative to share
        val cleanPath = path.trimStart('/', '\\').ifEmpty { "" }
        
        try {
            return@withContext s.list(cleanPath).map { 
                SmbFile(
                    name = it.fileName,
                    path = if (cleanPath.isEmpty()) it.fileName else "$cleanPath/${it.fileName}",
                    isDirectory = (it.fileAttributes and com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L,
                    size = it.endOfFile,
                    lastModified = it.changeTime.toEpochMillis()
                )
            }.filter { it.name != "." && it.name != ".." }
        } catch (e: Exception) {
            android.util.Log.e("SmbClientWrapper", "Error listing files at $cleanPath", e)
            return@withContext emptyList()
        }
    }

    suspend fun downloadFile(remotePath: String, localFile: File, onProgress: ((Float) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val s = share ?: throw IllegalStateException("Not connected")
        val cleanPath = remotePath.trimStart('/', '\\')
        
        if (!s.fileExists(cleanPath)) throw java.io.FileNotFoundException("Remote file not found: $cleanPath")

        val file = s.openFile(
            cleanPath,
            setOf(AccessMask.GENERIC_READ),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            java.util.EnumSet.noneOf(SMB2CreateOptions::class.java)
        )

        file.use { smbFile ->
            val fileSize = smbFile.fileInformation.standardInformation.endOfFile
            var totalBytesRead = 0L
            
            smbFile.inputStream.use { input ->
                localFile.outputStream().use { output ->
                    val buffer = ByteArray(8192) // 8KB buffer
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileSize > 0) {
                            onProgress?.invoke(totalBytesRead.toFloat() / fileSize)
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            }
        }
    }

    suspend fun uploadFile(localFile: File, remotePath: String, onProgress: ((Float) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val s = share ?: throw IllegalStateException("Not connected")
        val cleanPath = remotePath.trimStart('/', '\\')

        val file = s.openFile(
            cleanPath,
            setOf(AccessMask.GENERIC_WRITE),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            java.util.EnumSet.noneOf(SMB2CreateOptions::class.java)
        )

        file.use { smbFile ->
            val fileSize = localFile.length()
            var totalBytesWritten = 0L
            
            smbFile.outputStream.use { output ->
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead
                        if (fileSize > 0) {
                            onProgress?.invoke(totalBytesWritten.toFloat() / fileSize)
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            }
        }
    }
    
    suspend fun exists(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val s = share ?: throw IllegalStateException("Not connected")
        val cleanPath = remotePath.trimStart('/', '\\')
        return@withContext s.fileExists(cleanPath) || s.folderExists(cleanPath)
    }
    
    suspend fun getFileInfo(remotePath: String): SmbFile? = withContext(Dispatchers.IO) {
        val s = share ?: throw IllegalStateException("Not connected")
        val cleanPath = remotePath.trimStart('/', '\\')
        if (!s.fileExists(cleanPath)) return@withContext null
        
        val file = s.openFile(
            cleanPath,
            setOf(AccessMask.GENERIC_READ),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            java.util.EnumSet.noneOf(SMB2CreateOptions::class.java)
        )
        
        return@withContext file.use { f ->
            val info = f.fileInformation
            SmbFile(
                name = File(cleanPath).name,
                path = cleanPath,
                isDirectory = false,
                size = info.standardInformation.endOfFile,
                lastModified = info.basicInformation.changeTime.toEpochMillis()
            )
        }
    }

    data class SmbFile(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )
}
