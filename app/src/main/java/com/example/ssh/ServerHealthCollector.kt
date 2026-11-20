package com.example.ssh

import com.example.core.Formatters
import com.example.domain.DashboardDataSource
import com.example.domain.DiskMountInfo
import com.example.domain.MetricAvailability
import com.example.domain.NetworkInterfaceInfo
import com.example.domain.ServerMetrics
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.connectbot.sshlib.SessionExit
import org.connectbot.sshlib.SshClient
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class RemoteExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean = false,
    val rttMs: Long = 0L
)

interface RemoteCommandExecutor {
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES
    ): Result<RemoteExecResult>

    companion object {
        const val DEFAULT_EXEC_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAX_OUTPUT_BYTES = 131_072
    }
}

/**
 * Executes bounded, timeout-protected read-only commands on an authenticated SshClient,
 * draining both stdout and stderr concurrently to prevent channel deadlocks.
 */
class SshRemoteCommandExecutor(
    private val client: SshClient
) : RemoteCommandExecutor {
    override suspend fun exec(
        command: String,
        timeoutMs: Long,
        maxOutputBytes: Int
    ): Result<RemoteExecResult> {
        if (!client.isAuthenticated) {
            return Result.failure(IllegalStateException("SSH session is not authenticated"))
        }

        val startNs = System.nanoTime()
        val execSession = client.openSession()
            ?: return Result.failure(IllegalStateException("ไม่สามารถเปิด SSH exec channel เพื่ออ่านข้อมูลเซิร์ฟเวอร์ได้"))

        return try {
            val started = withTimeoutOrNull(timeoutMs) { execSession.requestExec(command) }
            if (started != true) {
                return if (started == null) {
                    Result.success(RemoteExecResult(stdout = "", stderr = "Command timed out", exitCode = null, timedOut = true))
                } else {
                    Result.failure(IllegalStateException("เซิร์ฟเวอร์ไม่อนุญาตให้รันคำสั่งอ่านสถิติ (exec disabled)"))
                }
            }

            var timedOut = false
            val execOutputs = withTimeoutOrNull<Triple<String, String, Int?>>(timeoutMs) {
                coroutineScope {
                    val outDeferred = async {
                        val buffer = ByteArrayOutputStream()
                        for (chunk in execSession.stdout) {
                            val remaining = maxOutputBytes - buffer.size()
                            if (remaining <= 0) break
                            buffer.write(chunk, 0, minOf(chunk.size, remaining))
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }
                    val errDeferred = async {
                        val buffer = ByteArrayOutputStream()
                        for (chunk in execSession.stderr) {
                            val remaining = 32_768 - buffer.size()
                            if (remaining <= 0) break
                            buffer.write(chunk, 0, minOf(chunk.size, remaining))
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }
                    val exit = runCatching {
                        (execSession.exitInfo.await() as? SessionExit.Status)?.code?.toInt()
                    }.getOrNull()
                    Triple(outDeferred.await(), errDeferred.await(), exit)
                }
            }
            val (outText, errText, exitCode) = execOutputs ?: run {
                timedOut = true
                Triple("", "Command timed out after ${timeoutMs}ms", null)
            }

            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            Result.success(
                RemoteExecResult(
                    stdout = outText,
                    stderr = errText,
                    exitCode = exitCode,
                    timedOut = timedOut,
                    rttMs = rttMs
                )
            )
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("อ่านข้อมูลเซิร์ฟเวอร์ไม่สำเร็จ: ${Formatters.redactDiagnosticText(t.message)}"))
        } finally {
            runCatching { execSession.close() }
        }
    }
}

data class ProcStatCpuSample(
    val busyTicks: Long,
    val totalTicks: Long,
    val timestampMs: Long
)

data class ProcNetTotalsSample(
    val rxBytes: Long,
    val txBytes: Long,
    val timestampMs: Long
)

class ServerHealthSessionCache {
    val pollMutex = Mutex()
    var osChecked: Boolean = false
    var isLinux: Boolean = true
    var detectedOsKernelName: String? = null
    var fastfetchChecked: Boolean = false
    var fastfetchAvailable: Boolean = false

    var cachedHostname: String? = null
    var cachedOsInfo: String? = null
    var cachedKernelInfo: String? = null
    var cachedCpuModel: String? = null

    var lastSlowProbeMs: Long = 0L
    var cachedDisks: List<DiskMountInfo> = emptyList()
    var cachedDiskAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE
    var cachedTemperatureC: Float? = null
    var cachedTemperatureSource: String? = null
    var cachedTemperatureAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE
    var cachedNetworkInterfaces: List<NetworkInterfaceInfo> = emptyList()

    var previousCpuSample: ProcStatCpuSample? = null
    var previousNetSample: ProcNetTotalsSample? = null

    val cpuHistory = ArrayDeque<Float>(MAX_HISTORY)
    val memoryHistory = ArrayDeque<Float>(MAX_HISTORY)

    fun recordHistory(cpuPct: Float?, memUsedMb: Long?, memTotalMb: Long?) {
        if (cpuPct != null && cpuPct.isFinite()) {
            if (cpuHistory.size >= MAX_HISTORY) cpuHistory.removeFirst()
            cpuHistory.addLast(cpuPct.coerceIn(0f, 100f))
        }
        if (memUsedMb != null && memTotalMb != null && memTotalMb > 0L) {
            val pct = ((memUsedMb.toFloat() / memTotalMb.toFloat()) * 100f).coerceIn(0f, 100f)
            if (memoryHistory.size >= MAX_HISTORY) memoryHistory.removeFirst()
            memoryHistory.addLast(pct)
        }
    }

    companion object {
        const val MAX_HISTORY = 20
        const val SLOW_PROBE_INTERVAL_MS = 28_000L
    }
}

/**
 * Collects authentic Linux server telemetry over an existing SSH session using:
 * 1. OS verification (`uname -s`)
 * 2. Optional Fastfetch JSON (`command -v fastfetch` -> `--format json`, never ANSI text)
 * 3. Reliable read-only Linux `/proc`, `/sys`, and POSIX `df` fallbacks with two-sample deltas
 *    for CPU% and Network RX/TX rates.
 */
object ServerHealthCollector {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun collect(
        executor: RemoteCommandExecutor,
        cache: ServerHealthSessionCache,
        forceSlowRefresh: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ServerMetrics> {
        if (!cache.pollMutex.tryLock()) {
            return Result.failure(IllegalStateException("กำลังอ่านข้อมูลอยู่แล้ว (Single-flight guard)"))
        }
        try {
            // 1. OS check once per connection
            if (!cache.osChecked) {
                val osCheck = executor.exec("uname -s 2>/dev/null || echo Unknown", timeoutMs = 4_000L)
                    .getOrElse { return Result.failure(it) }
                if (osCheck.timedOut) {
                    return Result.failure(IllegalStateException("หมดเวลาในการตรวจสอบระบบปฏิบัติการ (uname timeout)"))
                }
                val detected = osCheck.stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                cache.osChecked = true
                cache.detectedOsKernelName = detected.ifBlank { "Unknown" }
                cache.isLinux = detected.equals("Linux", ignoreCase = true)
            }

            if (!cache.isLinux) {
                return Result.success(
                    ServerMetrics(
                        retrievedAt = nowMs,
                        dataSource = DashboardDataSource.UNSUPPORTED,
                        isLinux = false,
                        detectedOsKernelName = cache.detectedOsKernelName,
                        fastfetchAvailable = false,
                        osInfo = cache.detectedOsKernelName,
                        cpuAvailability = MetricAvailability.UNSUPPORTED,
                        memoryAvailability = MetricAvailability.UNSUPPORTED,
                        temperatureAvailability = MetricAvailability.UNSUPPORTED,
                        networkThroughputAvailability = MetricAvailability.UNSUPPORTED,
                        diskAvailability = MetricAvailability.UNSUPPORTED,
                        availabilityNote = "Server Health Dashboard รองรับเฉพาะ Linux (ตรวจพบระบบ: ${cache.detectedOsKernelName ?: "Unknown"})"
                    )
                )
            }

            // 2. Fastfetch binary availability check once per connection (NEVER auto-install)
            if (!cache.fastfetchChecked) {
                val ffCheck = executor.exec(
                    "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin\"; command -v fastfetch 2>/dev/null || true",
                    timeoutMs = 3_500L
                ).getOrNull()
                cache.fastfetchChecked = true
                cache.fastfetchAvailable = !ffCheck?.stdout.isNullOrBlank()
            }

            var usedFastfetch = false
            var ffSnapshot: FastfetchParsed? = null
            if (cache.fastfetchAvailable) {
                val ffNeedFull = cache.cachedOsInfo.isNullOrBlank() || forceSlowRefresh
                val modules = if (ffNeedFull) "title:os:host:kernel:uptime:cpu:memory:disk" else "cpu:memory:uptime"
                val ffCmd = "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin\"; fastfetch -s $modules --format json 2>/dev/null"
                val ffRes = executor.exec(ffCmd, timeoutMs = 5_000L).getOrNull()
                if (ffRes != null && !ffRes.timedOut && ffRes.stdout.trimStart().startsWith("[")) {
                    val parsed = parseFastfetchJson(ffRes.stdout)
                    if (parsed != null) {
                        usedFastfetch = true
                        ffSnapshot = parsed
                        parsed.hostname?.let { cache.cachedHostname = it }
                        parsed.osInfo?.let { cache.cachedOsInfo = it }
                        parsed.kernelInfo?.let { cache.cachedKernelInfo = it }
                        parsed.cpuModel?.let { cache.cachedCpuModel = it }
                    }
                }
            }

            // 3. Read-only Linux native probes
            val needIdentity = cache.cachedHostname.isNullOrBlank() ||
                cache.cachedOsInfo.isNullOrBlank() ||
                cache.cachedKernelInfo.isNullOrBlank() ||
                cache.cachedCpuModel.isNullOrBlank()
            val needSlowProbe = forceSlowRefresh ||
                cache.lastSlowProbeMs == 0L ||
                (nowMs - cache.lastSlowProbeMs) >= ServerHealthSessionCache.SLOW_PROBE_INTERVAL_MS
            val needSecondSampleForDelta = cache.previousCpuSample == null || cache.previousNetSample == null

            val probeScript = buildLinuxProbeCommand(
                includeIdentity = needIdentity,
                includeSlow = needSlowProbe,
                includeInlineSecondSample = needSecondSampleForDelta
            )

            val probeExec = executor.exec(probeScript, timeoutMs = 6_000L)
                .getOrElse { return Result.failure(it) }
            if (probeExec.timedOut && probeExec.stdout.isBlank()) {
                return Result.failure(IllegalStateException("หมดเวลาอ่านข้อมูล Telemetry จากเซิร์ฟเวอร์"))
            }

            val sections = splitSections(probeExec.stdout)

            if (needIdentity) {
                sections["HOST"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { cache.cachedHostname = it }
                sections["OS"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { cache.cachedOsInfo = it }
                sections["KERNEL"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { cache.cachedKernelInfo = it }
                parseCpuModel(sections["CPUMODEL"].orEmpty())?.let { cache.cachedCpuModel = it }
            }

            // Parse uptime & loadavg
            val uptimeText = sections["UPTIME_PROC"]?.firstOrNull()?.let { formatProcUptime(it) }
                ?: ffSnapshot?.uptimeText
                ?: sections["UPTIME_CMD"]?.firstOrNull()
            val loadAvg = parseLoadAverage(sections["LOAD"].orEmpty())

            // Parse CPU% strictly from two samples (never cumulative boot average)
            var cpuPct: Float? = null
            var cpuAvail = MetricAvailability.UNAVAILABLE
            val stat1 = parseProcStatLine(sections["STAT1"].orEmpty(), nowMs)
            val stat2 = sections["STAT2"]?.let { parseProcStatLine(it, nowMs + 200L) }
            if (stat1 != null && stat2 != null) {
                cpuPct = computeCpuUsagePercent(stat1, stat2)
                cache.previousCpuSample = stat2
                if (cpuPct != null) cpuAvail = MetricAvailability.AVAILABLE
            } else if (stat1 != null) {
                val prev = cache.previousCpuSample
                if (prev != null && stat1.totalTicks > prev.totalTicks) {
                    cpuPct = computeCpuUsagePercent(prev, stat1)
                    if (cpuPct != null) cpuAvail = MetricAvailability.AVAILABLE
                }
                cache.previousCpuSample = stat1
            }

            // Parse /proc/meminfo first, then Fastfetch fallback
            val parsedMem = parseProcMemInfoMb(sections["MEMINFO"].orEmpty())
            val memUsedMb = parsedMem?.first ?: ffSnapshot?.memoryUsedMb
            val memTotalMb = parsedMem?.second ?: ffSnapshot?.memoryTotalMb
            val memAvail = if (memUsedMb != null && memTotalMb != null && memTotalMb > 0L) {
                MetricAvailability.AVAILABLE
            } else {
                MetricAvailability.UNAVAILABLE
            }

            // Parse Network throughput from two /proc/net/dev samples
            var rxRate: Long? = null
            var txRate: Long? = null
            var netRateAvail = MetricAvailability.UNAVAILABLE
            val net1 = parseProcNetDevTotals(sections["NETDEV1"].orEmpty(), nowMs)
            val net2 = sections["NETDEV2"]?.let { parseProcNetDevTotals(it, nowMs + 200L) }
            if (net1 != null && net2 != null) {
                val rates = computeNetworkRates(net1, net2)
                cache.previousNetSample = net2
                if (rates != null) {
                    rxRate = rates.first
                    txRate = rates.second
                    netRateAvail = MetricAvailability.AVAILABLE
                }
            } else if (net1 != null) {
                val prevNet = cache.previousNetSample
                if (prevNet != null && nowMs > prevNet.timestampMs) {
                    val rates = computeNetworkRates(prevNet, net1)
                    if (rates != null) {
                        rxRate = rates.first
                        txRate = rates.second
                        netRateAvail = MetricAvailability.AVAILABLE
                    }
                }
                cache.previousNetSample = net1
            }

            // Slow probes (Disk, Temperature, Interfaces)
            if (needSlowProbe) {
                cache.lastSlowProbeMs = nowMs
                val parsedDisks = parseDfOutput(sections["DISK"].orEmpty())
                if (parsedDisks.isNotEmpty()) {
                    cache.cachedDisks = parsedDisks
                    cache.cachedDiskAvailability = MetricAvailability.AVAILABLE
                } else if (!ffSnapshot?.disks.isNullOrEmpty()) {
                    cache.cachedDisks = ffSnapshot!!.disks
                    cache.cachedDiskAvailability = MetricAvailability.AVAILABLE
                } else {
                    cache.cachedDisks = emptyList()
                    cache.cachedDiskAvailability = MetricAvailability.UNAVAILABLE
                }

                val tempResult = parseTemperature(sections["TEMP"].orEmpty(), sections["SENSORS"].orEmpty())
                cache.cachedTemperatureC = tempResult.first
                cache.cachedTemperatureSource = tempResult.second
                cache.cachedTemperatureAvailability = tempResult.third

                val parsedNets = parseNetworkInterfaces(sections["NETIF"].orEmpty())
                if (parsedNets.isNotEmpty()) {
                    cache.cachedNetworkInterfaces = parsedNets
                }
            }

            cache.recordHistory(cpuPct, memUsedMb, memTotalMb)

            val dataSource = when {
                usedFastfetch && (cpuPct != null || parsedMem != null) -> DashboardDataSource.MIXED
                usedFastfetch -> DashboardDataSource.FASTFETCH
                else -> DashboardDataSource.LINUX_PROC
            }

            val missingList = buildList {
                if (cpuAvail != MetricAvailability.AVAILABLE) add("CPU")
                if (memAvail != MetricAvailability.AVAILABLE) add("Memory")
                if (cache.cachedDiskAvailability != MetricAvailability.AVAILABLE) add("Disk")
                if (cache.cachedTemperatureAvailability == MetricAvailability.PERMISSION_DENIED) add("Temp (permission denied)")
            }
            val note = if (missingList.isNotEmpty()) {
                "บางค่าไม่สามารถอ่านได้ในสภาพแวดล้อมนี้: ${missingList.joinToString(", ")}"
            } else null

            return Result.success(
                ServerMetrics(
                    retrievedAt = nowMs,
                    dataSource = dataSource,
                    isLinux = true,
                    detectedOsKernelName = cache.detectedOsKernelName,
                    fastfetchAvailable = cache.fastfetchAvailable,
                    osInfo = cache.cachedOsInfo,
                    kernelInfo = cache.cachedKernelInfo,
                    hostname = cache.cachedHostname,
                    uptimeText = uptimeText,
                    loadAverage = loadAvg,
                    cpuModel = cache.cachedCpuModel,
                    cpuUsagePercent = cpuPct,
                    cpuAvailability = cpuAvail,
                    memoryUsedMb = memUsedMb,
                    memoryTotalMb = memTotalMb,
                    memoryAvailability = memAvail,
                    temperatureCelsius = cache.cachedTemperatureC,
                    temperatureSource = cache.cachedTemperatureSource,
                    temperatureAvailability = cache.cachedTemperatureAvailability,
                    networkRxBytesPerSec = rxRate,
                    networkTxBytesPerSec = txRate,
                    networkThroughputAvailability = netRateAvail,
                    diskSummary = cache.cachedDisks,
                    diskAvailability = cache.cachedDiskAvailability,
                    networkInterfaces = cache.cachedNetworkInterfaces,
                    availabilityNote = note,
                    cpuHistory = cache.cpuHistory.toList(),
                    memoryHistory = cache.memoryHistory.toList()
                )
            )
        } finally {
            cache.pollMutex.unlock()
        }
    }

    internal fun buildLinuxProbeCommand(
        includeIdentity: Boolean,
        includeSlow: Boolean,
        includeInlineSecondSample: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (includeIdentity) {
            parts += "echo '===HOST==='; (hostname 2>/dev/null || uname -n)"
            parts += "echo '===OS==='; (awk -F= '/^PRETTY_NAME=/{gsub(/\"/,\"\",$2); print $2; exit}' /etc/os-release 2>/dev/null || uname -s)"
            parts += "echo '===KERNEL==='; uname -sr"
            parts += "echo '===CPUMODEL==='; head -n 40 /proc/cpuinfo 2>/dev/null || true"
        }
        parts += "echo '===UPTIME_PROC==='; cat /proc/uptime 2>/dev/null || true"
        parts += "echo '===UPTIME_CMD==='; (uptime -p 2>/dev/null || uptime 2>/dev/null || true)"
        parts += "echo '===LOAD==='; cat /proc/loadavg 2>/dev/null || true"
        parts += "echo '===MEMINFO==='; head -n 12 /proc/meminfo 2>/dev/null || true"
        parts += "echo '===STAT1==='; head -n 1 /proc/stat 2>/dev/null || true"
        parts += "echo '===NETDEV1==='; cat /proc/net/dev 2>/dev/null || true"
        if (includeInlineSecondSample) {
            parts += "sleep 0.2"
            parts += "echo '===STAT2==='; head -n 1 /proc/stat 2>/dev/null || true"
            parts += "echo '===NETDEV2==='; cat /proc/net/dev 2>/dev/null || true"
        }
        if (includeSlow) {
            parts += "echo '===DISK==='; (df -kP 2>/dev/null || df -k 2>/dev/null || true)"
            parts += "echo '===TEMP==='; for f in /sys/class/thermal/thermal_zone*/temp; do [ -e \"\$f\" ] || continue; t=\$(cat \"\$f\" 2>/dev/null || echo \"DENIED\"); n=\$(cat \"\${f%/temp}/type\" 2>/dev/null || basename \"\${f%/temp}\"); echo \"\$n|\$t\"; done; for f in /sys/class/hwmon/hwmon*/temp1_input; do [ -e \"\$f\" ] || continue; t=\$(cat \"\$f\" 2>/dev/null || echo \"DENIED\"); n=\$(cat \"\${f%/temp1_input}/name\" 2>/dev/null || basename \"\${f%/temp1_input}\"); echo \"hwmon:\$n|\$t\"; done"
            parts += "echo '===SENSORS==='; (command -v sensors >/dev/null 2>&1 && sensors 2>/dev/null | head -n 25 || true)"
            parts += "echo '===NETIF==='; (PATH=\"\$PATH:/sbin:/usr/sbin\"; ip -o -4 addr show 2>/dev/null || hostname -I 2>/dev/null || true)"
        }
        return parts.joinToString("; ")
    }

    internal fun splitSections(rawOutput: String): Map<String, List<String>> {
        val sections = linkedMapOf<String, MutableList<String>>()
        var currentTag = ""
        rawOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===") && trimmed.length > 6) {
                currentTag = trimmed.removePrefix("===").removeSuffix("===")
                sections.getOrPut(currentTag) { mutableListOf() }
            } else if (currentTag.isNotEmpty() && trimmed.isNotEmpty()) {
                sections.getOrPut(currentTag) { mutableListOf() }.add(line)
            }
        }
        return sections
    }

    internal data class FastfetchParsed(
        val hostname: String? = null,
        val osInfo: String? = null,
        val kernelInfo: String? = null,
        val uptimeText: String? = null,
        val cpuModel: String? = null,
        val memoryUsedMb: Long? = null,
        val memoryTotalMb: Long? = null,
        val disks: List<DiskMountInfo> = emptyList()
    )

    internal fun parseFastfetchJson(rawJson: String): FastfetchParsed? {
        return runCatching {
            val root = json.parseToJsonElement(rawJson.trim())
            if (root !is JsonArray) return@runCatching null

            var hostname: String? = null
            var osInfo: String? = null
            var kernelInfo: String? = null
            var uptimeText: String? = null
            var cpuModel: String? = null
            var memUsedMb: Long? = null
            var memTotalMb: Long? = null
            val disks = mutableListOf<DiskMountInfo>()

            root.forEach { elem ->
                val obj = elem as? JsonObject ?: return@forEach
                if (obj["error"] != null) return@forEach
                val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val result = obj["result"] ?: return@forEach

                when (type.lowercase()) {
                    "title" -> {
                        val r = result as? JsonObject
                        hostname = hostname ?: r?.get("hostName")?.jsonPrimitive?.contentOrNull
                    }
                    "os" -> {
                        val r = result as? JsonObject
                        osInfo = r?.get("prettyName")?.jsonPrimitive?.contentOrNull
                            ?: r?.get("name")?.jsonPrimitive?.contentOrNull
                    }
                    "host" -> {
                        val r = result as? JsonObject
                        hostname = hostname ?: r?.get("name")?.jsonPrimitive?.contentOrNull
                    }
                    "kernel" -> {
                        val r = result as? JsonObject
                        val sysName = r?.get("name")?.jsonPrimitive?.contentOrNull
                        val release = r?.get("release")?.jsonPrimitive?.contentOrNull
                        kernelInfo = listOfNotNull(sysName, release).joinToString(" ").ifBlank { null }
                    }
                    "uptime" -> {
                        val r = result as? JsonObject
                        val uptimeMs = r?.get("uptime")?.jsonPrimitive?.longOrNull
                        if (uptimeMs != null && uptimeMs > 0L) {
                            uptimeText = Formatters.formatDuration(uptimeMs)
                        }
                    }
                    "cpu" -> {
                        val r = result as? JsonObject
                        cpuModel = r?.get("cpu")?.jsonPrimitive?.contentOrNull
                    }
                    "memory" -> {
                        val r = result as? JsonObject
                        val usedBytes = r?.get("used")?.jsonPrimitive?.longOrNull
                        val totalBytes = r?.get("total")?.jsonPrimitive?.longOrNull
                        if (usedBytes != null && totalBytes != null && totalBytes > 0L) {
                            memUsedMb = usedBytes / (1024L * 1024L)
                            memTotalMb = totalBytes / (1024L * 1024L)
                        }
                    }
                    "disk" -> {
                        val arr = result as? JsonArray
                        arr?.forEach { item ->
                            val d = item as? JsonObject ?: return@forEach
                            val mount = d["mountpoint"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            val fs = d["filesystem"]?.jsonPrimitive?.contentOrNull ?: mount
                            if (isPseudoFilesystem(fs, mount)) return@forEach
                            val bytesObj = d["bytes"] as? JsonObject ?: return@forEach
                            val used = bytesObj["used"]?.jsonPrimitive?.longOrNull ?: return@forEach
                            val total = bytesObj["total"]?.jsonPrimitive?.longOrNull ?: return@forEach
                            if (total <= 0L) return@forEach
                            val pct = ((used * 100.0) / total).roundToInt().coerceIn(0, 100)
                            disks += DiskMountInfo(
                                filesystem = fs,
                                mountPoint = mount,
                                usedText = Formatters.formatBytes(used),
                                totalText = Formatters.formatBytes(total),
                                usePercent = pct
                            )
                        }
                    }
                }
            }

            FastfetchParsed(
                hostname = hostname,
                osInfo = osInfo,
                kernelInfo = kernelInfo,
                uptimeText = uptimeText,
                cpuModel = cpuModel,
                memoryUsedMb = memUsedMb,
                memoryTotalMb = memTotalMb,
                disks = disks.take(8)
            )
        }.getOrNull()
    }

    internal fun parseCpuModel(lines: List<String>): String? {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("model name", ignoreCase = true) ||
                trimmed.startsWith("Hardware", ignoreCase = true) ||
                trimmed.startsWith("Processor", ignoreCase = true)
            ) {
                val value = trimmed.substringAfter(':', "").trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    internal fun formatProcUptime(rawLine: String): String? {
        val seconds = rawLine.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull() ?: return null
        if (seconds < 0) return null
        val totalSec = seconds.toLong()
        val days = totalSec / 86400
        val hours = (totalSec % 86400) / 3600
        val mins = (totalSec % 3600) / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${mins}m")
        }.trim()
    }

    internal fun parseLoadAverage(lines: List<String>): String? {
        val parts = lines.firstOrNull()?.trim()?.split(Regex("\\s+")).orEmpty()
        if (parts.size < 3) return null
        return "${parts[0]} ${parts[1]} ${parts[2]}"
    }

    internal fun parseProcStatLine(lines: List<String>, timestampMs: Long): ProcStatCpuSample? {
        val cpuLine = lines.firstOrNull { it.trim().startsWith("cpu ") } ?: return null
        val tokens = cpuLine.trim().split(Regex("\\s+"))
        if (tokens.size < 5) return null
        val values = tokens.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val user = values.getOrElse(0) { 0L }
        val nice = values.getOrElse(1) { 0L }
        val system = values.getOrElse(2) { 0L }
        val idle = values.getOrElse(3) { 0L }
        val iowait = values.getOrElse(4) { 0L }
        val irq = values.getOrElse(5) { 0L }
        val softirq = values.getOrElse(6) { 0L }
        val steal = values.getOrElse(7) { 0L }

        val busy = user + nice + system + irq + softirq + steal
        val total = busy + idle + iowait
        return if (total > 0L) ProcStatCpuSample(busy, total, timestampMs) else null
    }

    internal fun computeCpuUsagePercent(prev: ProcStatCpuSample, curr: ProcStatCpuSample): Float? {
        val totalDelta = curr.totalTicks - prev.totalTicks
        val busyDelta = curr.busyTicks - prev.busyTicks
        if (totalDelta <= 0L || busyDelta < 0L) return null
        val pct = (busyDelta.toDouble() / totalDelta.toDouble()) * 100.0
        return ((pct * 10.0).roundToInt() / 10.0f).coerceIn(0f, 100f)
    }

    internal fun parseProcMemInfoMb(lines: List<String>): Pair<Long, Long>? {
        var totalKb: Long? = null
        var availableKb: Long? = null
        var freeKb: Long = 0L
        var buffersKb: Long = 0L
        var cachedKb: Long = 0L

        lines.forEach { line ->
            val parts = line.trim().split(Regex(":\\s+|\\s+"))
            val key = parts.getOrNull(0) ?: return@forEach
            val valueKb = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            when (key) {
                "MemTotal" -> totalKb = valueKb
                "MemAvailable" -> availableKb = valueKb
                "MemFree" -> freeKb = valueKb
                "Buffers" -> buffersKb = valueKb
                "Cached" -> cachedKb = valueKb
            }
        }

        val total = totalKb ?: return null
        if (total <= 0L) return null
        val avail = availableKb ?: (freeKb + buffersKb + cachedKb)
        val used = (total - avail).coerceAtLeast(0L)
        return (used / 1024L) to (total / 1024L)
    }

    internal fun parseProcNetDevTotals(lines: List<String>, timestampMs: Long): ProcNetTotalsSample? {
        var totalRx = 0L
        var totalTx = 0L
        var parsedAny = false

        lines.forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) return@forEach
            val iface = line.substring(0, colonIdx).trim()
            if (iface == "lo" || iface.isBlank()) return@forEach
            val nums = line.substring(colonIdx + 1).trim().split(Regex("\\s+"))
            if (nums.size < 9) return@forEach
            val rx = nums[0].toLongOrNull() ?: return@forEach
            val tx = nums[8].toLongOrNull() ?: return@forEach
            totalRx += rx
            totalTx += tx
            parsedAny = true
        }
        return if (parsedAny) ProcNetTotalsSample(totalRx, totalTx, timestampMs) else null
    }

    internal fun computeNetworkRates(prev: ProcNetTotalsSample, curr: ProcNetTotalsSample): Pair<Long, Long>? {
        val deltaMs = curr.timestampMs - prev.timestampMs
        if (deltaMs <= 0L) return null
        val rxDelta = (curr.rxBytes - prev.rxBytes).coerceAtLeast(0L)
        val txDelta = (curr.txBytes - prev.txBytes).coerceAtLeast(0L)
        val rxPerSec = (rxDelta * 1000.0 / deltaMs).toLong()
        val txPerSec = (txDelta * 1000.0 / deltaMs).toLong()
        return rxPerSec to txPerSec
    }

    internal fun parseDfOutput(lines: List<String>): List<DiskMountInfo> {
        if (lines.isEmpty()) return emptyList()
        val joinedLines = mutableListOf<String>()
        lines.drop(1).forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val tokens = trimmed.split(Regex("\\s+"))
            // Handle POSIX wrapped long filesystem lines
            if (tokens.size == 1 && joinedLines.isEmpty() || (joinedLines.isNotEmpty() && joinedLines.last().split(Regex("\\s+")).size == 1)) {
                if (joinedLines.isNotEmpty() && joinedLines.last().split(Regex("\\s+")).size == 1) {
                    joinedLines[joinedLines.lastIndex] = "${joinedLines.last()} $trimmed"
                } else {
                    joinedLines += trimmed
                }
            } else {
                joinedLines += trimmed
            }
        }

        val disks = mutableListOf<DiskMountInfo>()
        joinedLines.forEach { line ->
            val cols = line.split(Regex("\\s+"))
            if (cols.size < 6) return@forEach
            val fs = cols[0]
            val totalKb = cols[1].toLongOrNull() ?: return@forEach
            val usedKb = cols[2].toLongOrNull() ?: return@forEach
            val pct = cols[4].removeSuffix("%").toIntOrNull() ?: return@forEach
            val mount = cols.drop(5).joinToString(" ")
            if (isPseudoFilesystem(fs, mount) || totalKb <= 0L) return@forEach
            disks += DiskMountInfo(
                filesystem = fs,
                mountPoint = mount,
                usedText = Formatters.formatBytes(usedKb * 1024L),
                totalText = Formatters.formatBytes(totalKb * 1024L),
                usePercent = pct.coerceIn(0, 100)
            )
        }
        return disks.take(8)
    }

    internal fun parseTemperature(
        sysfsLines: List<String>,
        sensorsLines: List<String>
    ): Triple<Float?, String?, MetricAvailability> {
        var sawDenied = false
        val readings = mutableListOf<Pair<String, Float>>()

        sysfsLines.forEach { line ->
            val parts = line.trim().split("|")
            if (parts.size != 2) return@forEach
            val name = parts[0].trim()
            val rawVal = parts[1].trim()
            if (rawVal.equals("DENIED", ignoreCase = true)) {
                sawDenied = true
                return@forEach
            }
            val rawNum = rawVal.toFloatOrNull() ?: return@forEach
            val celsius = if (rawNum > 200f) rawNum / 1000f else rawNum
            if (celsius in -30f..140f) {
                readings += name to ((celsius * 10f).roundToInt() / 10f)
            }
        }

        val preferred = readings.firstOrNull { (name, _) ->
            name.contains("x86_pkg", true) ||
                name.contains("cpu", true) ||
                name.contains("coretemp", true) ||
                name.contains("k10temp", true) ||
                name.contains("soc", true)
        } ?: readings.maxByOrNull { it.second }

        if (preferred != null) {
            return Triple(preferred.second, preferred.first, MetricAvailability.AVAILABLE)
        }

        // Fallback to `sensors` output if present
        val tempRegex = Regex("""\+(-?\d+(?:\.\d+)?)°C""")
        sensorsLines.forEach { line ->
            val match = tempRegex.find(line) ?: return@forEach
            val celsius = match.groupValues[1].toFloatOrNull() ?: return@forEach
            if (celsius in -30f..140f) {
                val label = line.substringBefore(':').trim().ifBlank { "sensors" }
                return Triple((celsius * 10f).roundToInt() / 10f, label, MetricAvailability.AVAILABLE)
            }
        }

        return if (sawDenied) {
            Triple(null, null, MetricAvailability.PERMISSION_DENIED)
        } else {
            Triple(null, null, MetricAvailability.UNAVAILABLE)
        }
    }

    internal fun parseNetworkInterfaces(lines: List<String>): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        lines.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            // Format A: `ip -o -4 addr show`: "2: eth0    inet 192.168.1.10/24 brd ..."
            val inetIdx = trimmed.indexOf(" inet ")
            if (inetIdx > 0) {
                val header = trimmed.substring(0, inetIdx).trim().split(Regex("\\s+"))
                val iface = header.getOrNull(1)?.substringBefore('@') ?: return@forEach
                if (iface == "lo") return@forEach
                val addr = trimmed.substring(inetIdx + 6).trim().split(Regex("\\s+")).firstOrNull() ?: return@forEach
                result += NetworkInterfaceInfo(name = iface, addresses = addr)
            } else if (!trimmed.contains(":") && trimmed.contains(".")) {
                // Format B: `hostname -I` fallback
                trimmed.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("127.") }.forEachIndexed { idx, ip ->
                    result += NetworkInterfaceInfo(name = "if$idx", addresses = ip)
                }
            }
        }
        return result.distinctBy { "${it.name}|${it.addresses}" }.take(8)
    }

    private fun isPseudoFilesystem(fs: String, mount: String): Boolean {
        val fsLower = fs.lowercase()
        val mountLower = mount.lowercase()
        return fsLower in setOf("tmpfs", "devtmpfs", "udev", "overlay", "squashfs", "efivarfs", "proc", "sysfs", "cgroup", "cgroup2") ||
            mountLower.startsWith("/dev") ||
            mountLower.startsWith("/proc") ||
            mountLower.startsWith("/sys") ||
            mountLower.startsWith("/run/credentials")
    }
}
