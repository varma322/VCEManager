package com.vcemanager.viewmodel

import androidx.lifecycle.*
import com.vcemanager.model.*
import com.vcemanager.repository.VceRepository
import kotlinx.coroutines.*

// ── Main VM — shared state across all fragments ───────────────
class MainViewModel : ViewModel() {

    private val _containers = MutableLiveData<List<Container>>(emptyList())
    val containers: LiveData<List<Container>> = _containers

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _networkInfo = MutableLiveData<NetworkInfo>()
    val networkInfo: LiveData<NetworkInfo> = _networkInfo

    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _containers.value = VceRepository.listContainers()
                _networkInfo.value = VceRepository.getNetworkInfo()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun startAutoRefresh(intervalMs: Long = 5000L) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    _containers.value = VceRepository.listContainers()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopAutoRefresh() { refreshJob?.cancel() }

    fun startContainer(name: String, cmd: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.startContainer(name, cmd)
            onResult(r.success, if (r.success) "Container started" else r.stderr)
            if (r.success) refresh()
        }
    }

    fun stopContainer(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.stopContainer(name)
            onResult(r.success, if (r.success) "Container stopped" else r.stderr)
            if (r.success) refresh()
        }
    }

    fun deleteContainer(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.deleteContainer(name)
            onResult(r.success, if (r.success) "Container deleted" else r.stderr)
            if (r.success) refresh()
        }
    }

    fun createContainer(name: String, tarball: String, storageMb: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val r = VceRepository.createContainer(name, tarball, storageMb)
            _loading.value = false
            onResult(r.success, if (r.success) "Container '$name' created" else r.stderr)
            if (r.success) refresh()
        }
    }

    fun setAutostart(name: String, enabled: Boolean) {
        viewModelScope.launch {
            VceRepository.setAutostart(name, enabled)
            refresh()
        }
    }

    fun networkInit(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.networkInit()
            onResult(r.success, if (r.success) r.stdout else r.stderr)
            _networkInfo.value = VceRepository.getNetworkInfo()
        }
    }

    fun networkDestroy(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.networkDestroy()
            onResult(r.success, if (r.success) r.stdout else r.stderr)
            _networkInfo.value = VceRepository.getNetworkInfo()
        }
    }

    fun boot(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val r = VceRepository.boot()
            _loading.value = false
            onResult(r.success, if (r.success) r.stdout else r.stderr)
            refresh()
        }
    }

    fun clearError() { _error.value = null }
}

// ── Container Detail VM ───────────────────────────────────────
class ContainerDetailViewModel : ViewModel() {

    private val _ports = MutableLiveData<List<PortRule>>(emptyList())
    val ports: LiveData<List<PortRule>> = _ports

    private val _snapshots = MutableLiveData<List<Snapshot>>(emptyList())
    val snapshots: LiveData<List<Snapshot>> = _snapshots

    private val _stats = MutableLiveData<ContainerStats?>()
    val stats: LiveData<ContainerStats?> = _stats

    private val _logs = MutableLiveData<String>()
    val logs: LiveData<String> = _logs

    private var statsJob: Job? = null
    private var currentName: String = ""

    fun load(name: String) {
        currentName = name
        loadPorts(name)
        loadSnapshots(name)
    }

    fun loadPorts(name: String) {
        viewModelScope.launch {
            _ports.value = VceRepository.getPortRules(name)
        }
    }

    fun addPort(name: String, host: String, container: String, proto: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.addPort(name, host, container, proto)
            onResult(r.success, if (r.success) "Port forward added" else r.stderr)
            if (r.success) loadPorts(name)
        }
    }

    fun delPort(name: String, rule: PortRule, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.delPort(name, rule.hostPort, rule.containerPort, rule.proto)
            onResult(r.success, if (r.success) "Port forward removed" else r.stderr)
            if (r.success) loadPorts(name)
        }
    }

    fun flushPorts(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.flushPorts(name)
            onResult(r.success, if (r.success) "All ports flushed" else r.stderr)
            if (r.success) loadPorts(name)
        }
    }

    fun setLimits(name: String, cpu: Int?, cores: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.setLimits(name, cpu, cores)
            onResult(r.success, if (r.success) "Limits applied" else r.stderr)
        }
    }

    fun clearLimits(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.clearLimits(name)
            onResult(r.success, if (r.success) "Limits cleared" else r.stderr)
        }
    }

    fun startStatsPolling(name: String, intervalMs: Long = 2000L) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive) {
                try {
                    _stats.value = VceRepository.getStats(name)
                } catch (_: Exception) {}
                delay(intervalMs)
            }
        }
    }

    fun stopStatsPolling() { statsJob?.cancel() }

    fun loadSnapshots(name: String) {
        viewModelScope.launch {
            _snapshots.value = VceRepository.getSnapshots(name)
        }
    }

    fun saveSnapshot(name: String, tag: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.saveSnapshot(name, tag)
            onResult(r.success, if (r.success) "Snapshot '$tag' saved" else r.stderr)
            if (r.success) loadSnapshots(name)
        }
    }

    fun restoreSnapshot(name: String, tag: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.restoreSnapshot(name, tag)
            onResult(r.success, if (r.success) "Restored to '$tag'" else r.stderr)
        }
    }

    fun deleteSnapshot(name: String, tag: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.deleteSnapshot(name, tag)
            onResult(r.success, if (r.success) "Snapshot deleted" else r.stderr)
            if (r.success) loadSnapshots(name)
        }
    }

    fun loadLogs(name: String) {
        viewModelScope.launch {
            val r = VceRepository.getLogs(name)
            _logs.value = r.stdout
        }
    }

    fun execCommand(name: String, cmd: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val r = VceRepository.execInContainer(name, cmd)
            onResult(r.success, if (r.success) r.stdout else r.stderr)
        }
    }

    override fun onCleared() {
        super.onCleared()
        statsJob?.cancel()
    }
}
