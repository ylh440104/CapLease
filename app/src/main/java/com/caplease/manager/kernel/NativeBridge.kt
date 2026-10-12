package com.caplease.manager.kernel

object NativeBridge {

    init {
        System.loadLibrary("caplease")
    }

    external fun nativeProbe(): Int

    external fun nativeInfo(): LongArray?

    external fun nativePair(password: ByteArray, salt: ByteArray): Int

    external fun nativeUnpair(): Int

    external fun nativeOpenSession(password: ByteArray, salt: ByteArray): LongArray?

    external fun nativeCloseSession(): Int

    external fun nativeAcquireLease(
        capabilities: Long,
        seconds: Long,
        flags: Int,
        packageName: String?
    ): LongArray?

    external fun nativeReleaseLease(leaseId: Long): Int

    external fun nativeExtendLease(leaseId: Long, seconds: Long): Int

    external fun nativeRevokeAll(): Int

    external fun nativeListLeases(): LongArray?

    external fun nativeExec(command: String, capabilities: Long, timeoutMs: Int): Int

    external fun nativeReadAudit(limit: Int): LongArray?

    external fun nativeClearAudit(): Int

    external fun nativeSessionActive(): Boolean

    external fun nativeStatusName(status: Int): String
}