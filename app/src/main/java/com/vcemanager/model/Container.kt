package com.vcemanager.model

data class Container(
    val name: String,
    val status: String,
    val pid: String,
    val created: String,
    val ip: String,
    val cmd: String,
    val autostart: Boolean,
    val limitCpu: String,
    val limitCpus: String,
    val storageMb: Int
) {
    val isRunning get() = status == "running"
}

data class PortRule(
    val containerName: String,
    val hostPort: String,
    val containerPort: String,
    val proto: String
) {
    override fun toString() = "$hostPort:$containerPort/$proto"
}

data class Snapshot(
    val containerName: String,
    val tag: String,
    val date: String,
    val size: String,
    val cmd: String
)

data class ContainerStats(
    val name: String,
    val pid: String,
    val cpuPercent: Float,
    val memRssMb: Int,
    val netRxKb: Long,
    val netTxKb: Long,
    val cpuLimit: String,
    val cpuCores: String,
    val uptime: String,
    val processCount: Int
)

data class NetworkInfo(
    val bridgeIp: String,
    val bridgeUp: Boolean,
    val wanIface: String,
    val natActive: Boolean
)
