package com.vcemanager.repository

import com.vcemanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VceRepository {

    private const val VCE = "/data/adb/ksu/bin/vce"

    // ── Shell execution ───────────────────────────────────────
    suspend fun exec(vararg args: String): ShellResult = withContext(Dispatchers.IO) {
        val cmd = "$VCE ${args.joinToString(" ")}"
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit   = process.waitFor()
            ShellResult(exit == 0, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Unknown error")
        }
    }

    suspend fun execRaw(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit   = process.waitFor()
            ShellResult(exit == 0, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Unknown error")
        }
    }

    // ── Container operations ──────────────────────────────────
    suspend fun listContainers(): List<Container> = withContext(Dispatchers.IO) {
        val cfgDir = "/data/vce/config"
        val runtimeDir = "/data/vce/runtime"
        val result = execRaw("ls $cfgDir/*.conf 2>/dev/null")
        if (!result.success || result.stdout.isBlank()) return@withContext emptyList()

        result.stdout.lines().filter { it.endsWith(".conf") }.mapNotNull { confPath ->
            try {
                val conf = execRaw("cat $confPath").stdout
                val name     = conf.lineValue("NAME")
                val ip       = conf.lineValue("IP")
                val created  = conf.lineValue("CREATED")
                val cmd      = conf.lineValue("CMD")
                val autostart= conf.lineValue("AUTOSTART") == "yes"
                val limitCpu = conf.lineValue("LIMIT_CPU")
                val limitCpus= conf.lineValue("LIMIT_CPUS")
                val storage  = conf.lineValue("STORAGE_MB").toIntOrNull() ?: 0

                val pidFile = "$runtimeDir/$name.pid"
                val pidResult = execRaw("cat $pidFile 2>/dev/null")
                val pid = pidResult.stdout.trim()
                val running = pid.isNotEmpty() &&
                    execRaw("kill -0 $pid 2>/dev/null").success

                Container(
                    name = name,
                    status = if (running) "running" else "stopped",
                    pid = if (running) pid else "-",
                    created = created,
                    ip = ip,
                    cmd = cmd,
                    autostart = autostart,
                    limitCpu = limitCpu,
                    limitCpus = limitCpus,
                    storageMb = storage
                )
            } catch (e: Exception) { null }
        }
    }

    suspend fun startContainer(name: String, cmd: String = ""): ShellResult {
        val cmdArg = if (cmd.isNotBlank()) "\"$cmd\"" else ""
        return exec("start", name, cmdArg)
    }

    suspend fun stopContainer(name: String) = exec("stop", name)

    suspend fun deleteContainer(name: String) = exec("delete", name)

    suspend fun createContainer(name: String, tarball: String, storageMb: Int): ShellResult {
        val result = exec("create", name, tarball)
        if (result.success && storageMb > 0) {
            // Save storage allocation to config
            execRaw("echo 'STORAGE_MB=$storageMb' >> /data/vce/config/$name.conf")
        }
        return result
    }

    suspend fun setAutostart(name: String, enabled: Boolean) =
        exec("autostart", name, if (enabled) "on" else "off")

    suspend fun boot() = exec("boot")

    suspend fun getLogs(name: String) = exec("logs", name)

    suspend fun execInContainer(name: String, cmd: String) =
        exec("exec", name, "\"$cmd\"")

    // ── Network ───────────────────────────────────────────────
    suspend fun networkInit() = exec("network", "init")

    suspend fun networkDestroy() = exec("network", "destroy")

    suspend fun getNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        val bridgeResult = execRaw("ip addr show vce0 2>/dev/null")
        val bridgeUp = bridgeResult.success && bridgeResult.stdout.contains("UP")
        val bridgeIp = Regex("""inet (\S+)""").find(bridgeResult.stdout)?.groupValues?.get(1) ?: "-"
        val wanResult = execRaw("ip route get 8.8.8.8 2>/dev/null")
        val wanIface = Regex("""dev (\S+)""").find(wanResult.stdout)?.groupValues?.get(1) ?: "wlan0"
        val natResult = execRaw("iptables -t nat -L POSTROUTING -n 2>/dev/null | grep 10.10.0")
        NetworkInfo(bridgeIp, bridgeUp, wanIface, natResult.success && natResult.stdout.isNotBlank())
    }

    // ── Port forwarding ───────────────────────────────────────
    suspend fun getPortRules(name: String): List<PortRule> = withContext(Dispatchers.IO) {
        val result = execRaw("cat /data/vce/config/$name.ports 2>/dev/null")
        if (!result.success || result.stdout.isBlank()) return@withContext emptyList()
        result.stdout.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split(":")
            if (parts.size == 3)
                PortRule(name, parts[0], parts[1], parts[2])
            else null
        }
    }

    suspend fun addPort(name: String, hostPort: String, containerPort: String, proto: String) =
        exec("port", "add", name, "$hostPort:$containerPort/$proto")

    suspend fun delPort(name: String, hostPort: String, containerPort: String, proto: String) =
        exec("port", "del", name, "$hostPort:$containerPort/$proto")

    suspend fun flushPorts(name: String) = exec("port", "flush", name)

    // ── Resource limits ───────────────────────────────────────
    suspend fun setLimits(name: String, cpuPct: Int?, cpuCores: String?): ShellResult {
        val args = mutableListOf("limit", "set", name)
        if (cpuPct != null)   args.addAll(listOf("--cpu", cpuPct.toString()))
        if (!cpuCores.isNullOrBlank()) args.addAll(listOf("--cpus", cpuCores))
        return exec(*args.toTypedArray())
    }

    suspend fun clearLimits(name: String) = exec("limit", "clear", name)

    // ── Stats ─────────────────────────────────────────────────
    suspend fun getStats(name: String): ContainerStats? = withContext(Dispatchers.IO) {
        val pidFile = "/data/vce/runtime/$name.pid"
        val pidResult = execRaw("cat $pidFile 2>/dev/null")
        val pid = pidResult.stdout.trim().ifEmpty { return@withContext null }

        // Two-sample CPU
        val s1 = execRaw("awk '{print \$14+\$15}' /proc/$pid/stat 2>/dev/null").stdout.trim().toLongOrNull() ?: 0L
        val t1 = execRaw("awk '{s=0;for(i=2;i<=8;i++)s+=\$i;print s}' /proc/stat 2>/dev/null").stdout.trim().toLongOrNull() ?: 1L
        Thread.sleep(500)
        val s2 = execRaw("awk '{print \$14+\$15}' /proc/$pid/stat 2>/dev/null").stdout.trim().toLongOrNull() ?: 0L
        val t2 = execRaw("awk '{s=0;for(i=2;i<=8;i++)s+=\$i;print s}' /proc/stat 2>/dev/null").stdout.trim().toLongOrNull() ?: 1L
        val cpuPct = if (t2 - t1 > 0) ((s2 - s1) * 100f / (t2 - t1)) else 0f

        val status = execRaw("cat /proc/$pid/status 2>/dev/null").stdout
        val rss = Regex("""VmRSS:\s+(\d+)""").find(status)?.groupValues?.get(1)?.toIntOrNull()?.div(1024) ?: 0

        val netDev = execRaw("nsenter --target $pid --net awk '/eth0/{print \$2,\$10}' /proc/net/dev 2>/dev/null").stdout.trim()
        val netParts = netDev.split(" ")
        val rx = netParts.getOrNull(0)?.toLongOrNull()?.div(1024) ?: 0L
        val tx = netParts.getOrNull(1)?.toLongOrNull()?.div(1024) ?: 0L

        val cpuLimit = execRaw("cat /dev/cpuctl/vce/$name/cpu.uclamp.max 2>/dev/null").stdout.trim().ifEmpty { "none" }
        val cpuCores = execRaw("cat /dev/cpuset/vce/$name/cpus 2>/dev/null").stdout.trim().ifEmpty { "all" }

        val uptimeSec = execRaw("awk '{print int(\$1)}' /proc/uptime 2>/dev/null").stdout.trim().toIntOrNull() ?: 0
        val startTick = execRaw("awk '{print \$22}' /proc/$pid/stat 2>/dev/null").stdout.trim().toLongOrNull() ?: 0L
        val hz = 100
        val containerUptime = uptimeSec - (startTick / hz).toInt()
        val uptimeStr = when {
            containerUptime > 3600 -> "${containerUptime/3600}h ${(containerUptime%3600)/60}m"
            containerUptime > 60   -> "${containerUptime/60}m ${containerUptime%60}s"
            else                   -> "${containerUptime}s"
        }

        val procs = execRaw("nsenter --target $pid --pid ls /proc 2>/dev/null | grep -c '^[0-9]'").stdout.trim().toIntOrNull() ?: 0

        ContainerStats(name, pid, cpuPct, rss, rx, tx, cpuLimit, cpuCores, uptimeStr, procs)
    }

    // ── Snapshots ─────────────────────────────────────────────
    suspend fun getSnapshots(name: String): List<Snapshot> = withContext(Dispatchers.IO) {
        val result = execRaw("ls /data/vce/snapshots/$name/*.meta 2>/dev/null")
        if (!result.success || result.stdout.isBlank()) return@withContext emptyList()
        result.stdout.lines().filter { it.endsWith(".meta") }.mapNotNull { metaPath ->
            try {
                val meta = execRaw("cat $metaPath").stdout
                Snapshot(
                    containerName = name,
                    tag  = meta.lineValue("TAG"),
                    date = meta.lineValue("DATE"),
                    size = meta.lineValue("SIZE"),
                    cmd  = meta.lineValue("CMD")
                )
            } catch (e: Exception) { null }
        }
    }

    suspend fun saveSnapshot(name: String, tag: String) = exec("snapshot", "save", name, tag)

    suspend fun restoreSnapshot(name: String, tag: String) = exec("snapshot", "restore", name, tag)

    suspend fun deleteSnapshot(name: String, tag: String) = exec("snapshot", "delete", name, tag)

    // ── Helpers ───────────────────────────────────────────────
    private fun String.lineValue(key: String): String =
        lines().firstOrNull { it.startsWith("$key=") }
               ?.substringAfter("=")?.trim() ?: ""
}

data class ShellResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String
)
