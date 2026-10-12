package com.caplease.manager.ui

import com.caplease.manager.kernel.AuditRecord
import com.caplease.manager.kernel.Capability
import com.caplease.manager.kernel.KernelInfo
import com.caplease.manager.kernel.LeaseRecord
import com.caplease.manager.kernel.SessionTicket

sealed interface KernelState {
    data object Unknown : KernelState
    data object Probing : KernelState
    data class Absent(val reason: String) : KernelState
    data class Present(val info: KernelInfo) : KernelState
}

sealed interface SessionState {
    data object Closed : SessionState
    data object Opening : SessionState
    data class Open(val ticket: SessionTicket) : SessionState
}

data class LeaseDraft(
    val capabilities: Long = 0L,
    val seconds: Long = 300L,
    val oneShot: Boolean = false,
    val inheritable: Boolean = true,
    val packageName: String = ""
) {
    val selected: List<Capability> get() = Capability.describe(capabilities)

    val dangerous: Boolean get() = Capability.isDangerous(capabilities)

    val valid: Boolean get() = capabilities != 0L && seconds > 0L && packageName.isNotBlank()

    fun toggle(capability: Capability): LeaseDraft {
        val updated = capabilities xor capability.bit
        return copy(capabilities = updated)
    }
}

data class ExecDraft(
    val command: String = "",
    val capabilities: Long = Capability.FS_READ_APP.bit,
    val timeoutMs: Int = 5000
) {
    val valid: Boolean get() = command.isNotBlank() && capabilities != 0L && timeoutMs in 100..600000
}

data class ManagerUiState(
    val kernel: KernelState = KernelState.Unknown,
    val session: SessionState = SessionState.Closed,
    val paired: Boolean = false,
    val leases: List<LeaseRecord> = emptyList(),
    val audit: List<AuditRecord> = emptyList(),
    val leaseDraft: LeaseDraft = LeaseDraft(),
    val execDraft: ExecDraft = ExecDraft(),
    val nowSeconds: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null
) {
    val canOperate: Boolean get() = kernel is KernelState.Present && session is SessionState.Open
}