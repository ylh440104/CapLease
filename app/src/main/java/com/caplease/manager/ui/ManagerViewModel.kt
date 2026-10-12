package com.caplease.manager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caplease.manager.core.CredentialVault
import com.caplease.manager.core.LeaseClient
import com.caplease.manager.kernel.Capability
import com.caplease.manager.kernel.KernelResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val vault = CredentialVault(application)
    private val client = LeaseClient(vault)

    private val operationLock = Mutex()

    private val _state = MutableStateFlow(ManagerUiState())
    val state: StateFlow<ManagerUiState> = _state.asStateFlow()

    init {
        refreshKernel()
        observeClock()
        refreshPairing()
    }

    private fun observeClock() {
        viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(nowSeconds = System.currentTimeMillis() / 1000L) }
                delay(CLOCK_TICK_MS)
            }
        }
    }

    private suspend fun refreshPairing() {
        _state.update { it.copy(paired = vault.isPaired()) }
    }

    fun refreshKernel() {
        viewModelScope.launch {
            _state.update { it.copy(kernel = KernelState.Probing, busy = true) }

            when (val result = client.probe()) {
                is KernelResult.Success -> {
                    when (val info = client.info()) {
                        is KernelResult.Success -> _state.update {
                            it.copy(kernel = KernelState.Present(info.value), busy = false)
                        }
                        is KernelResult.Failure -> _state.update {
                            it.copy(kernel = KernelState.Absent(info.label), busy = false)
                        }
                    }
                }
                is KernelResult.Failure -> _state.update {
                    it.copy(kernel = KernelState.Absent(result.label), busy = false)
                }
            }
        }
    }

    fun pair(secret: CharArray) {
        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, message = null) }

                when (val result = client.pair(secret)) {
                    is KernelResult.Success -> {
                        refreshPairing()
                        _state.update { it.copy(busy = false, message = "配对完成") }
                    }
                    is KernelResult.Failure -> _state.update {
                        it.copy(busy = false, message = result.label)
                    }
                }
            }
        }
    }

    fun unpair() {
        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, message = null) }

                when (val result = client.unpair()) {
                    is KernelResult.Success -> {
                        refreshPairing()
                        _state.update {
                            it.copy(
                                busy = false,
                                session = SessionState.Closed,
                                leases = emptyList(),
                                audit = emptyList(),
                                message = "已解除配对"
                            )
                        }
                    }
                    is KernelResult.Failure -> _state.update {
                        it.copy(busy = false, message = result.label)
                    }
                }
            }
        }
    }

    fun openSession(secret: CharArray) {
        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, session = SessionState.Opening, message = null) }

                when (val result = client.openSession(secret)) {
                    is KernelResult.Success -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                session = SessionState.Open(result.value),
                                message = "会话已建立"
                            )
                        }
                        refreshLeases()
                        refreshAudit()
                    }
                    is KernelResult.Failure -> _state.update {
                        it.copy(
                            busy = false,
                            session = SessionState.Closed,
                            message = result.label
                        )
                    }
                }
            }
        }
    }

    fun closeSession() {
        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, message = null) }

                client.closeSession()

                _state.update {
                    it.copy(
                        busy = false,
                        session = SessionState.Closed,
                        leases = emptyList(),
                        message = "会话已结束"
                    )
                }
            }
        }
    }

    fun toggleCapability(capability: Capability) {
        _state.update { it.copy(leaseDraft = it.leaseDraft.toggle(capability)) }
    }

    fun selectAllCapabilities() {
        _state.update { it.copy(leaseDraft = it.leaseDraft.copy(capabilities = Capability.all)) }
    }

    fun clearCapabilities() {
        _state.update { it.copy(leaseDraft = it.leaseDraft.copy(capabilities = 0L)) }
    }

    fun updateDuration(seconds: Long) {
        _state.update {
            it.copy(leaseDraft = it.leaseDraft.copy(seconds = seconds.coerceIn(MIN_LEASE_SECONDS, MAX_LEASE_SECONDS)))
        }
    }

    fun toggleOneShot() {
        _state.update { it.copy(leaseDraft = it.leaseDraft.copy(oneShot = !it.leaseDraft.oneShot)) }
    }

    fun toggleInheritable() {
        _state.update { it.copy(leaseDraft = it.leaseDraft.copy(inheritable = !it.leaseDraft.inheritable)) }
    }

    fun updatePackageName(name: String) {
        _state.update { it.copy(leaseDraft = it.leaseDraft.copy(packageName = name)) }
    }

    fun acquireLease() {
        val draft = _state.value.leaseDraft
        if (!draft.valid) {
            _state.update { it.copy(message = "请填写目标包名并至少选择一项能力") }
            return
        }

        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, message = null) }

                val result = client.acquire(
                    capabilities = draft.capabilities,
                    seconds = draft.seconds,
                    oneShot = draft.oneShot,
                    inheritable = draft.inheritable,
                    packageName = draft.packageName
                )

                when (result) {
                    is KernelResult.Success -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                message = "租约已签发 #${result.value.leaseId}",
                                leaseDraft = it.leaseDraft.copy(capabilities = 0L)
                            )
                        }
                        refreshLeases()
                        refreshAudit()
                    }
                    is KernelResult.Failure -> _state.update {
                        it.copy(busy = false, message = result.label)
                    }
                }
            }
        }
    }

    fun releaseLease(leaseId: Long) {
        viewModelScope.launch {
            operationLock.withLock {
                when (val result = client.release(leaseId)) {
                    is KernelResult.Success -> {
                        refreshLeases()
                        refreshAudit()
                        _state.update { it.copy(message = "租约已释放") }
                    }
                    is KernelResult.Failure -> _state.update { it.copy(message = result.label) }
                }
            }
        }
    }

    fun extendLease(leaseId: Long, seconds: Long) {
        viewModelScope.launch {
            operationLock.withLock {
                when (val result = client.extend(leaseId, seconds)) {
                    is KernelResult.Success -> {
                        refreshLeases()
                        _state.update { it.copy(message = "租约已延长") }
                    }
                    is KernelResult.Failure -> _state.update { it.copy(message = result.label) }
                }
            }
        }
    }

    fun revokeAll() {
        viewModelScope.launch {
            operationLock.withLock {
                when (val result = client.revokeAll()) {
                    is KernelResult.Success -> {
                        refreshLeases()
                        refreshAudit()
                        _state.update { it.copy(message = "全部租约已撤销") }
                    }
                    is KernelResult.Failure -> _state.update { it.copy(message = result.label) }
                }
            }
        }
    }

    fun updateExecCommand(command: String) {
        _state.update { it.copy(execDraft = it.execDraft.copy(command = command)) }
    }

    fun toggleExecCapability(capability: Capability) {
        _state.update {
            it.copy(execDraft = it.execDraft.copy(capabilities = it.execDraft.capabilities xor capability.bit))
        }
    }

    fun updateExecTimeout(timeoutMs: Int) {
        _state.update {
            it.copy(execDraft = it.execDraft.copy(timeoutMs = timeoutMs.coerceIn(MIN_EXEC_TIMEOUT, MAX_EXEC_TIMEOUT)))
        }
    }

    fun executeOnce() {
        val draft = _state.value.execDraft
        if (!draft.valid) {
            _state.update { it.copy(message = "请填写命令并选择能力") }
            return
        }

        viewModelScope.launch {
            operationLock.withLock {
                _state.update { it.copy(busy = true, message = null) }

                when (val result = client.exec(draft.command, draft.capabilities, draft.timeoutMs)) {
                    is KernelResult.Success -> {
                        refreshAudit()
                        _state.update { it.copy(busy = false, message = "一次性执行已提交") }
                    }
                    is KernelResult.Failure -> _state.update {
                        it.copy(busy = false, message = result.label)
                    }
                }
            }
        }
    }

    fun refreshLeases() {
        viewModelScope.launch {
            when (val result = client.list()) {
                is KernelResult.Success -> _state.update { it.copy(leases = result.value) }
                is KernelResult.Failure -> Unit
            }
        }
    }

    fun refreshAudit() {
        viewModelScope.launch {
            when (val result = client.readAudit(AUDIT_PAGE_SIZE)) {
                is KernelResult.Success -> _state.update { it.copy(audit = result.value) }
                is KernelResult.Failure -> Unit
            }
        }
    }

    fun clearAudit() {
        viewModelScope.launch {
            operationLock.withLock {
                when (val result = client.clearAudit()) {
                    is KernelResult.Success -> {
                        _state.update { it.copy(audit = emptyList(), message = "审计记录已清空") }
                    }
                    is KernelResult.Failure -> _state.update { it.copy(message = result.label) }
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        client.closeSession()
    }

    private companion object {
        const val CLOCK_TICK_MS = 1000L
        const val AUDIT_PAGE_SIZE = 64
        const val MIN_LEASE_SECONDS = 5L
        const val MAX_LEASE_SECONDS = 86400L
        const val MIN_EXEC_TIMEOUT = 100
        const val MAX_EXEC_TIMEOUT = 600000
    }
}