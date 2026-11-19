package com.example.sftp

import android.content.ContentResolver
import android.net.Uri
import com.example.core.Formatters
import com.example.domain.SftpFileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.SftpAttributes
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpOpenFlag
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SftpStatusCode

class SftpOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SftpManager {
    private const val CHUNK_SIZE = 32 * 1024

    suspend fun resolvePath(client: SftpClient, path: String = "."): String = withContext(Dispatchers.IO) {
        unwrap(client.realpath(path), "ไม่สามารถอ่านพาธ $path ได้")
    }

    suspend fun listDirectory(client: SftpClient, dirPath: String): List<SftpFileItem> = withContext(Dispatchers.IO) {
        val entries = unwrap(client.listdir(dirPath), "ไม่สามารถเปิดโฟลเดอร์ $dirPath ได้")
        entries
            .filter { it.filename != "." && it.filename != ".." }
            .map { entry ->
                val perms = entry.attrs.permissions
                val isDir = Formatters.isDirectoryMode(perms) || entry.longname.startsWith("d")
                val isSymlink = entry.longname.startsWith("l")
                val cleanBase = if (dirPath == "/") "" else dirPath.trimEnd('/')
                val fullPath = "$cleanBase/${entry.filename}"
                SftpFileItem(
                    name = entry.filename,
                    fullPath = fullPath,
                    isDirectory = isDir,
                    isSymlink = isSymlink,
                    sizeBytes = entry.attrs.size,
                    permissionsOctal = perms,
                    permissionsDisplay = Formatters.formatUnixPermissions(perms),
                    modifiedEpochSec = entry.attrs.mtime,
                    longName = entry.longname
                )
            }
            .sortedWith(compareByDescending<SftpFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun createDirectory(client: SftpClient, dirPath: String) = withContext(Dispatchers.IO) {
        unwrap(
            client.mkdir(dirPath, SftpAttributes(permissions = 493)), // 0755
            "สร้างโฟลเดอร์ $dirPath ไม่สำเร็จ"
        )
    }

    suspend fun rename(client: SftpClient, oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        unwrap(client.rename(oldPath, newPath), "เปลี่ยนชื่อไฟล์ไม่สำเร็จ")
    }

    suspend fun deleteItem(client: SftpClient, item: SftpFileItem) = withContext(Dispatchers.IO) {
        if (item.isDirectory) {
            unwrap(client.rmdir(item.fullPath), "ลบโฟลเดอร์ไม่สำเร็จ (โฟลเดอร์ต้องว่างเปล่า)")
        } else {
            unwrap(client.remove(item.fullPath), "ลบไฟล์ ${item.name} ไม่สำเร็จ")
        }
    }

    suspend fun uploadFromUri(
        client: SftpClient,
        contentResolver: ContentResolver,
        localUri: Uri,
        remotePath: String,
        onProgress: (bytesTransferred: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(localUri)
            ?: throw SftpOperationException("ไม่สามารถเปิดไฟล์จากอุปกรณ์ได้")
        val handle = unwrap(
            client.open(
                remotePath,
                setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
                SftpAttributes()
            ),
            "ไม่สามารถสร้างไฟล์ปลายทาง $remotePath ได้"
        )
        var offset = 0L
        try {
            input.use { stream ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    unwrap(client.write(handle, offset, chunk), "ส่งข้อมูลไฟล์ล้มเหลวที่ offset $offset")
                    offset += read
                    onProgress(offset)
                }
            }
            offset
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            runCatching { client.close(handle) }
        }
    }

    suspend fun downloadToUri(
        client: SftpClient,
        contentResolver: ContentResolver,
        remotePath: String,
        localUri: Uri,
        onProgress: (bytesTransferred: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val output = contentResolver.openOutputStream(localUri)
            ?: throw SftpOperationException("ไม่สามารถสร้างไฟล์ปลายทางบนอุปกรณ์ได้")
        val handle = unwrap(
            client.open(remotePath, setOf(SftpOpenFlag.READ), SftpAttributes()),
            "ไม่สามารถเปิดไฟล์ $remotePath บนเซิร์ฟเวอร์ได้"
        )
        var offset = 0L
        try {
            output.use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val chunk = unwrap(
                        client.read(handle, offset, CHUNK_SIZE),
                        "ดาวน์โหลดไฟล์ล้มเหลวที่ offset $offset"
                    ) ?: ByteArray(0)
                    if (chunk.isEmpty()) break
                    stream.write(chunk)
                    offset += chunk.size
                    onProgress(offset)
                    if (chunk.size < CHUNK_SIZE) {
                        // Continue reading until empty EOF chunk or break cleanly on next loop
                    }
                }
                stream.flush()
            }
            offset
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            runCatching { client.close(handle) }
        }
    }

    fun <T> unwrap(result: SftpResult<T>, fallbackMessage: String): T {
        return when (result) {
            is SftpResult.Success -> result.value
            is SftpResult.ServerError -> throw SftpOperationException(
                mapServerError(result.statusCode, result.message, fallbackMessage)
            )
            is SftpResult.ProtocolError -> throw SftpOperationException("$fallbackMessage: ${result.message}")
            is SftpResult.IoError -> throw SftpOperationException(
                "$fallbackMessage: การเชื่อมต่อขัดข้อง (${result.cause.message ?: "I/O Error"})",
                result.cause
            )
        }
    }

    private fun mapServerError(code: SftpStatusCode, serverMessage: String, fallback: String): String {
        val detail = when (code) {
            SftpStatusCode.NO_SUCH_FILE -> "ไม่พบไฟล์หรือโฟลเดอร์ที่ระบุ"
            SftpStatusCode.PERMISSION_DENIED -> "ไม่มีสิทธิ์เข้าถึง (Permission denied)"
            SftpStatusCode.OP_UNSUPPORTED -> "เซิร์ฟเวอร์ไม่รองรับคำสั่ง SFTP นี้"
            SftpStatusCode.NO_CONNECTION, SftpStatusCode.CONNECTION_LOST -> "การเชื่อมต่อ SFTP ขาดหาย"
            else -> serverMessage.ifBlank { code.name }
        }
        return "$fallback ($detail)"
    }
}
