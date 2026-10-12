package com.caplease.manager.kernel

data class LeaseRecord(
    val id: Long,
    val capabilities: Long,
    val expirySeconds: Long,
    val ownerUid: Int,
    val ownerPid: Int,
    val flags: Int
) {
    val isOneShot: Boolean get() = (flags and LEASE_FLAG_ONE_SHOT) != 0
    val isInheritable: Boolean get() = (flags and LEASE_FLAG_INHERIT) != 0
    val isSilent: Boolean get() = (flags and LEASE_FLAG_NO_AUDIT) != 0

    fun remainingSeconds(nowSeconds: Long): Long = (expirySeconds - nowSeconds).coerceAtLeast(0L)

    companion object {
        const val LEASE_FLAG_ONE_SHOT = 1
        const val LEASE_FLAG_INHERIT = 2
        const val LEASE_FLAG_NO_AUDIT = 4

        const val STRIDE = 6

        fun decode(values: LongArray): List<LeaseRecord> {
            if (values.isEmpty()) return emptyList()

            val status = Status.from(values[0].toInt())
            if (!status.isSuccess) return emptyList()

            val count = (values.size - 1) / STRIDE
            return List(count) { index ->
                val base = 1 + index * STRIDE
                LeaseRecord(
                    id = values[base],
                    capabilities = values[base + 1],
                    expirySeconds = values[base + 2],
                    ownerUid = values[base + 3].toInt(),
                    ownerPid = values[base + 4].toInt(),
                    flags = values[base + 5].toInt()
                )
            }
        }
    }
}

enum class AuditKind(val code: Int, val title: String) {
    SESSION_OPENED(1, "会话建立"),
    SESSION_CLOSED(2, "会话结束"),
    LEASE_GRANTED(3, "租约签发"),
    LEASE_RELEASED(4, "租约释放"),
    LEASE_EXPIRED(5, "租约到期"),
    CAPABILITY_USED(6, "能力使用"),
    CAPABILITY_REFUSED(7, "能力拒绝"),
    EXEC_COMPLETED(8, "一次性执行完成"),
    PAIRING_CHANGED(9, "配对状态变更"),
    UNKNOWN(0, "未知事件");

    companion object {
        fun from(code: Int): AuditKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class AuditRecord(
    val timestampSeconds: Long,
    val kind: AuditKind,
    val actorUid: Int,
    val actorPid: Int,
    val detail: Int,
    val value: Long
) {
    companion object {
        const val STRIDE = 6

        fun decode(values: LongArray): List<AuditRecord> {
            if (values.isEmpty()) return emptyList()

            val status = Status.from(values[0].toInt())
            if (!status.isSuccess) return emptyList()

            val count = (values.size - 1) / STRIDE
            return List(count) { index ->
                val base = 1 + index * STRIDE
                AuditRecord(
                    timestampSeconds = values[base],
                    kind = AuditKind.from(values[base + 1].toInt()),
                    actorUid = values[base + 2].toInt(),
                    actorPid = values[base + 3].toInt(),
                    detail = values[base + 4].toInt(),
                    value = values[base + 5]
                )
            }
        }
    }
}

data class KernelInfo(
    val kernelPatchVersion: String,
    val kernelVersion: String,
    val protocolVersion: Int,
    val leaseSlots: Int,
    val capabilityMask: Long,
    val bootSeconds: Long
) {
    companion object {
        fun decode(values: LongArray?): KernelInfo? {
            if (values == null || values.size < 7) return null

            val status = Status.from(values[0].toInt())
            if (!status.isSuccess) return null

            return KernelInfo(
                kernelPatchVersion = formatVersion(values[1].toInt()),
                kernelVersion = formatVersion(values[2].toInt()),
                protocolVersion = values[3].toInt(),
                leaseSlots = values[4].toInt(),
                capabilityMask = values[5],
                bootSeconds = values[6]
            )
        }

        private fun formatVersion(raw: Int): String {
            if (raw <= 0) return "未知"
            val major = (raw shr 16) and 0xFF
            val minor = (raw shr 8) and 0xFF
            val patch = raw and 0xFF
            return "$major.$minor.$patch"
        }
    }
}

data class SessionTicket(
    val sessionId: Long,
    val issuedSeconds: Long,
    val windowSeconds: Long
) {
    companion object {
        fun decode(values: LongArray?): SessionTicket? {
            if (values == null || values.size < 4) return null

            val status = Status.from(values[0].toInt())
            if (!status.isSuccess) return null

            return SessionTicket(
                sessionId = values[1],
                issuedSeconds = values[2],
                windowSeconds = values[3]
            )
        }
    }
}

data class LeaseGrant(val leaseId: Long, val slotCount: Int) {
    companion object {
        fun decode(values: LongArray?): LeaseGrant? {
            if (values == null || values.size < 3) return null

            val status = Status.from(values[0].toInt())
            if (!status.isSuccess) return null

            return LeaseGrant(leaseId = values[1], slotCount = values[2].toInt())
        }
    }
}