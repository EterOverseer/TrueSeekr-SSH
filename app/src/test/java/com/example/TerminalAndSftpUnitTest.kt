package com.example

import com.example.core.Formatters
import com.example.sftp.SftpManager
import com.example.sftp.SftpOperationException
import com.example.terminal.PocketModifierManager
import com.example.terminal.ScrollbackTextBuffer
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SftpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAndSftpUnitTest {

    @Test
    fun `scrollback buffer strips ANSI codes and supports search`() {
        val buffer = ScrollbackTextBuffer(maxLines = 100)
        val ansiText = "\u001B[32muser@host\u001B[0m:~\$ ls -la\n\u001B[1;34mdocker-compose.yml\u001B[0m\nREADME.md\n"
        buffer.appendRawBytes(ansiText.toByteArray(Charsets.UTF_8))

        val matches = buffer.search("docker-compose")
        assertEquals(1, matches.size)
        assertEquals("docker-compose.yml", matches.first().second)
        assertFalse(matches.first().second.contains("\u001B"))
    }

    @Test
    fun `modifier manager tracks sticky bits and clears transients`() {
        val manager = PocketModifierManager()
        assertEquals(0, manager.currentBitmask())

        manager.toggleCtrl()
        assertTrue(manager.isCtrlActive())
        assertEquals(PocketModifierManager.MOD_CTRL, manager.currentBitmask())

        manager.toggleAlt()
        assertEquals(
            PocketModifierManager.MOD_CTRL or PocketModifierManager.MOD_ALT,
            manager.currentBitmask()
        )

        manager.clearTransients()
        assertFalse(manager.isCtrlActive())
        assertFalse(manager.isAltActive())

        // Locked Ctrl persists across clearTransients
        manager.toggleCtrlLock()
        manager.clearTransients()
        assertTrue(manager.isCtrlActive())
    }

    @Test
    fun `sftp error mapping produces readable error messages`() {
        var caughtMessage = ""
        try {
            SftpManager.unwrap<Unit>(
                SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "denied"),
                "เปิดไฟล์ไม่สำเร็จ"
            )
        } catch (ex: SftpOperationException) {
            caughtMessage = ex.message.orEmpty()
        }
        assertTrue(caughtMessage.contains("Permission denied"))
    }

    @Test
    fun `formatters render unix permissions and byte sizes accurately`() {
        assertEquals("drwxr-xr-x", Formatters.formatUnixPermissions(0x4000 or 0b111_101_101))
        assertEquals("-rw-r--r--", Formatters.formatUnixPermissions(0x8000 or 0b110_100_100))
        assertEquals("2.0 KB", Formatters.formatBytes(2048L))
    }
}
