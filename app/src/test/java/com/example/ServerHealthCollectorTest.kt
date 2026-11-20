package com.example

import com.example.domain.DashboardDataSource
import com.example.domain.MetricAvailability
import com.example.ssh.RemoteCommandExecutor
import com.example.ssh.RemoteExecResult
import com.example.ssh.ServerHealthCollector
import com.example.ssh.ServerHealthSessionCache
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerHealthCollectorTest {

    private class FakeRemoteExecutor(
        private val handler: suspend (String) -> Result<RemoteExecResult>
    ) : RemoteCommandExecutor {
        val commandsExecuted = mutableListOf<String>()
        override suspend fun exec(
            command: String,
            timeoutMs: Long,
            maxOutputBytes: Int
        ): Result<RemoteExecResult> {
            commandsExecuted += command
            return handler(command)
        }
    }

    @Test
    fun `uname non-Linux reports explicit UNSUPPORTED state without zeroing metrics`() = runBlocking {
        val executor = FakeRemoteExecutor { cmd ->
            if (cmd.contains("uname -s")) {
                Result.success(RemoteExecResult(stdout = "FreeBSD\n", stderr = "", exitCode = 0))
            } else {
                error("Should not probe Linux stats on non-Linux OS")
            }
        }
        val cache = ServerHealthSessionCache()
        val result = ServerHealthCollector.collect(executor, cache, forceSlowRefresh = true, nowMs = 1_000L)

        assertTrue(result.isSuccess)
        val metrics = result.getOrThrow()
        assertFalse(metrics.isLinux)
        assertEquals(DashboardDataSource.UNSUPPORTED, metrics.dataSource)
        assertEquals("FreeBSD", metrics.detectedOsKernelName)
        assertEquals(MetricAvailability.UNSUPPORTED, metrics.cpuAvailability)
        assertEquals(MetricAvailability.UNSUPPORTED, metrics.memoryAvailability)
        assertEquals(MetricAvailability.UNSUPPORTED, metrics.diskAvailability)
        assertNull(metrics.cpuUsagePercent)
        assertNull(metrics.temperatureCelsius)
        assertEquals(1, executor.commandsExecuted.size)
    }

    @Test
    fun `missing fastfetch uses Linux proc and sys probes without attempting install`() = runBlocking {
        val probeOut = """
            ===HOST===
            edge-node-01
            ===OS===
            Ubuntu 24.04 LTS
            ===KERNEL===
            Linux 6.8.0-generic
            ===CPUMODEL===
            model name	: AMD EPYC 7763 64-Core Processor
            ===UPTIME_PROC===
            90315.42 180000.00
            ===UPTIME_CMD===
            up 1 day, 1 hour, 5 minutes
            ===LOAD===
            0.42 0.38 0.31 2/412 19901
            ===MEMINFO===
            MemTotal:       16384000 kB
            MemFree:         2000000 kB
            MemAvailable:    8192000 kB
            Buffers:          500000 kB
            Cached:          5000000 kB
            ===STAT1===
            cpu  1000 100 300 4000 100 0 0 0 0 0
            ===NETDEV1===
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 500000 100 0 0 0 0 0 0 500000 100 0 0 0 0 0 0
              eth0: 1000000 100 0 0 0 0 0 0 2000000 100 0 0 0 0 0 0
            ===STAT2===
            cpu  1050 100 310 4140 100 0 0 0 0 0
            ===NETDEV2===
              eth0: 1102400 110 0 0 0 0 0 0 2051200 105 0 0 0 0 0 0
            ===DISK===
            Filesystem     1024-blocks      Used Available Capacity Mounted on
            tmpfs              1048576      1024   1047552       1% /run
            /dev/nvme0n1p2   104857600  41943040  62914560      40% /
            overlay          104857600  41943040  62914560      40% /var/lib/docker/overlay2/abc/merged
            ===TEMP===
            x86_pkg_temp|54300
            ===SENSORS===
            ===NETIF===
            2: eth0    inet 10.0.0.15/24 brd 10.0.0.255 scope global eth0
        """.trimIndent()

        val executor = FakeRemoteExecutor { cmd ->
            when {
                cmd.trimStart().startsWith("uname -s") -> Result.success(RemoteExecResult("Linux\n", "", 0))
                cmd.contains("command -v fastfetch") -> Result.success(RemoteExecResult("", "", 1))
                else -> Result.success(RemoteExecResult(probeOut, "", 0))
            }
        }

        // Verify no install commands are ever issued
        val cache = ServerHealthSessionCache()
        val metrics = ServerHealthCollector.collect(executor, cache, forceSlowRefresh = true, nowMs = 10_000L).getOrThrow()

        assertTrue(executor.commandsExecuted.none { it.contains("apt") || it.contains("apk") || it.contains("dnf") || it.contains("sudo") })
        assertTrue(metrics.isLinux)
        assertFalse(metrics.fastfetchAvailable)
        assertEquals(DashboardDataSource.LINUX_PROC, metrics.dataSource)
        assertEquals("edge-node-01", metrics.hostname)
        assertEquals("Ubuntu 24.04 LTS", metrics.osInfo)
        assertEquals("Linux 6.8.0-generic", metrics.kernelInfo)
        assertEquals("AMD EPYC 7763 64-Core Processor", metrics.cpuModel)
        assertEquals("1d 1h 5m", metrics.uptimeText)
        assertEquals("0.42 0.38 0.31", metrics.loadAverage)

        // Two-sample CPU delta: busyDelta = (50 + 10) = 60, totalDelta = 200 -> 30.0%
        assertEquals(MetricAvailability.AVAILABLE, metrics.cpuAvailability)
        assertNotNull(metrics.cpuUsagePercent)
        assertEquals(30.0f, metrics.cpuUsagePercent!!, 0.2f)

        // Memory: total=16384000kB=16000MB, used=16384000-8192000=8192000kB=8000MB
        assertEquals(MetricAvailability.AVAILABLE, metrics.memoryAvailability)
        assertEquals(8000L, metrics.memoryUsedMb)
        assertEquals(16000L, metrics.memoryTotalMb)

        // Disk filtered pseudo-fs (only / kept, tmpfs & overlay excluded)
        assertEquals(MetricAvailability.AVAILABLE, metrics.diskAvailability)
        assertEquals(1, metrics.diskSummary.size)
        assertEquals("/", metrics.diskSummary.single().mountPoint)
        assertEquals(40, metrics.diskSummary.single().usePercent)

        // Temperature 54.3C from x86_pkg_temp
        assertEquals(MetricAvailability.AVAILABLE, metrics.temperatureAvailability)
        assertEquals(54.3f, metrics.temperatureCelsius!!, 0.1f)
        assertEquals("x86_pkg_temp", metrics.temperatureSource)

        // Net throughput over 200ms delta: rx +102400B -> 512000 B/s, tx +51200B -> 256000 B/s (lo excluded)
        assertEquals(MetricAvailability.AVAILABLE, metrics.networkThroughputAvailability)
        assertEquals(512_000L, metrics.networkRxBytesPerSec)
        assertEquals(256_000L, metrics.networkTxBytesPerSec)

        // Sparkline history appended
        assertEquals(1, metrics.cpuHistory.size)
        assertEquals(1, metrics.memoryHistory.size)
    }

    @Test
    fun `fastfetch json mode integrates cleanly and falls back safely when fastfetch emits malformed output`() = runBlocking {
        val validFfJson = """
            [
              {"type": "OS", "result": {"prettyName": "Debian GNU/Linux 12"}},
              {"type": "Host", "result": {"name": "nas-box"}},
              {"type": "CPU", "result": {"cpu": "Intel N100"}},
              {"type": "Memory", "result": {"used": 2147483648, "total": 8589934592}}
            ]
        """.trimIndent()
        val procOut = """
            ===UPTIME_PROC===
            3600.0 0.0
            ===LOAD===
            0.10 0.05 0.01 1/100 42
            ===STAT1===
            cpu  100 0 0 900 0 0 0 0
            ===STAT2===
            cpu  120 0 0 980 0 0 0 0
            ===DISK===
            Filesystem 1024-blocks Used Available Capacity Mounted on
            /dev/sda1 20971520 10485760 10485760 50% /
            ===TEMP===
            ===SENSORS===
            ===NETIF===
        """.trimIndent()

        var emitBrokenFf = false
        val executor = FakeRemoteExecutor { cmd ->
            when {
                cmd.trimStart().startsWith("uname -s") -> Result.success(RemoteExecResult("Linux", "", 0))
                cmd.contains("command -v fastfetch") -> Result.success(RemoteExecResult("/usr/bin/fastfetch", "", 0))
                cmd.contains("fastfetch -s") -> if (emitBrokenFf) {
                    Result.success(RemoteExecResult("\u001B[31mASCII ART BROKEN\u001B[0m", "", 1))
                } else {
                    Result.success(RemoteExecResult(validFfJson, "", 0))
                }
                else -> Result.success(RemoteExecResult(procOut, "", 0))
            }
        }

        val cache = ServerHealthSessionCache()
        val m1 = ServerHealthCollector.collect(executor, cache, forceSlowRefresh = true, nowMs = 1_000L).getOrThrow()
        assertTrue(m1.fastfetchAvailable)
        assertEquals(DashboardDataSource.MIXED, m1.dataSource)
        assertEquals("Debian GNU/Linux 12", m1.osInfo)
        assertEquals("Intel N100", m1.cpuModel)
        assertEquals(2048L, m1.memoryUsedMb)
        assertEquals(8192L, m1.memoryTotalMb)
        assertEquals(MetricAvailability.UNAVAILABLE, m1.temperatureAvailability)
        assertNull(m1.temperatureCelsius)

        // Now fastfetch fails/returns ANSI garbage: should seamlessly keep LINUX_PROC without throwing
        emitBrokenFf = true
        val m2 = ServerHealthCollector.collect(executor, cache, forceSlowRefresh = false, nowMs = 6_000L).getOrThrow()
        assertEquals(DashboardDataSource.LINUX_PROC, m2.dataSource)
        assertEquals("Debian GNU/Linux 12", m2.osInfo)
        assertEquals(2, m2.cpuHistory.size)
    }

    @Test
    fun `thermal permission denied reports explicit PERMISSION_DENIED and never fabricates zero`() {
        val tempResult = ServerHealthCollector.parseTemperature(
            sysfsLines = listOf("acpitz|DENIED", "hwmon:coretemp|DENIED"),
            sensorsLines = emptyList()
        )
        assertNull(tempResult.first)
        assertEquals(MetricAvailability.PERMISSION_DENIED, tempResult.third)
    }

    @Test
    fun `single-flight mutex rejects overlapping concurrent polls cleanly`() = runBlocking {
        val executor = FakeRemoteExecutor { cmd ->
            delay(150)
            if (cmd.contains("uname -s")) Result.success(RemoteExecResult("FreeBSD", "", 0))
            else Result.success(RemoteExecResult("", "", 0))
        }
        val cache = ServerHealthSessionCache()
        val first = async { ServerHealthCollector.collect(executor, cache) }
        delay(20)
        val second = async { ServerHealthCollector.collect(executor, cache) }

        val r1 = first.await()
        val r2 = second.await()
        assertTrue(r1.isSuccess)
        assertTrue(r2.isFailure)
    }
}
