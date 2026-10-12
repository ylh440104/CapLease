package com.caplease.manager.core

import com.caplease.manager.kernel.AuditRecord
import com.caplease.manager.kernel.KernelInfo
import com.caplease.manager.kernel.KernelResult
import com.caplease.manager.kernel.LeaseGrant
import com.caplease.manager.kernel.LeaseRecord
import com.caplease.manager.kernel.NativeBridge
import com.caplease.manager.kernel.SessionTicket
import com.caplease.manager.kernel.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LeaseClient(private val vault: CredentialVault) {

    private val mutex = Mutex()

    @Volatile
    private var ticket: SessionTicket? = null

    @Volatile
    private var passphrase: ByteArray? = null

    val hasSession: Boolean get() = ticket != null

    val currentTicket: SessionTicket? get() = ticket

    suspend fun probe(): KernelResult<Unit> = withContext(Dispatchers.IO) {
        val status = Status.from(NativeBridge.nativeProbe())
        if (status.isSuccess) {
            KernelResult.Success(Unit)
        } else {
            KernelResult.Failure(status, NativeBridge.nativeStatusName(status.code))
        }
    }

    suspend fun info(): KernelResult<KernelInfo> = withContext(Dispatchers.IO) {
        val decoded = KernelInfo.decode(NativeBridge.nativeInfo())
            ?: return@withContext KernelResult.Failure(
                Status.NOT_READY,
                NativeBridge.nativeStatusName(Status.NOT_READY.code)
            )
        KernelResult.Success(decoded)
    }

    suspend fun pair(secret: CharArray): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val validation = PassphraseEncoder.validate(secret)
            if (validation !is PassphraseEncoder.Validation.Acceptable) {
                return@withLock KernelResult.Failure(
                    Status.INVALID_ARGUMENT,
                    describe(validation)
                )
            }

            val salt = PassphraseEncoder.generateSalt()
            val encoded = PassphraseEncoder.encode(secret)
            val status = Status.from(NativeBridge.nativePair(encoded, salt))

            encoded.fill(0)

            if (status.isSuccess) {
                vault.storeSalt(salt)
                KernelResult.Success(Unit)
            } else {
                KernelResult.Failure(status, NativeBridge.nativeStatusName(status.code))
            }
        }
    }

    suspend fun unpair(): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ticket = null
            passphrase?.fill(0)
            passphrase = null

            val status = Status.from(NativeBridge.nativeUnpair())
            vault.clear()

            if (status.isSuccess) {
                KernelResult.Success(Unit)
            } else {
                KernelResult.Failure(status, NativeBridge.nativeStatusName(status.code))
            }
        }
    }

    suspend fun openSession(secret: CharArray): KernelResult<SessionTicket> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val salt = vault.loadSalt()
                    ?: return@withLock KernelResult.Failure(
                        Status.NOT_PAIRED,
                        NativeBridge.nativeStatusName(Status.NOT_PAIRED.code)
                    )

                val encoded = PassphraseEncoder.encode(secret)
                val decoded = SessionTicket.decode(NativeBridge.nativeOpenSession(encoded, salt))
                encoded.fill(0)

                if (decoded == null) {
                    return@withLock KernelResult.Failure(
                        Status.BAD_CREDENTIAL,
                        NativeBridge.nativeStatusName(Status.BAD_CREDENTIAL.code)
                    )
                }

                passphrase = PassphraseEncoder.encode(secret)
                ticket = decoded

                KernelResult.Success(decoded)
            }
        }

    suspend fun closeSession(): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ticket = null
            passphrase?.fill(0)
            passphrase = null

            val status = Status.from(NativeBridge.nativeCloseSession())
            if (status.isSuccess) {
                KernelResult.Success(Unit)
            } else {
                KernelResult.Failure(status, NativeBridge.nativeStatusName(status.code))
            }
        }
    }

    suspend fun acquire(
        capabilities: Long,
        seconds: Long,
        oneShot: Boolean,
        inheritable: Boolean,
        packageName: String
    ): KernelResult<LeaseGrant> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (ticket == null) {
                return@withLock KernelResult.Failure(
                    Status.UNAUTHENTICATED,
                    NativeBridge.nativeStatusName(Status.UNAUTHENTICATED.code)
                )
            }

            val flags = buildFlags(oneShot, inheritable)
            val grant = LeaseGrant.decode(
                NativeBridge.nativeAcquireLease(capabilities, seconds, flags, packageName)
            ) ?: return@withLock KernelResult.Failure(
                Status.INTERNAL,
                NativeBridge.nativeStatusName(Status.INTERNAL.code)
            )

            KernelResult.Success(grant)
        }
    }

    suspend fun release(leaseId: Long): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val status = Status.from(NativeBridge.nativeReleaseLease(leaseId))
            toResult(status)
        }
    }

    suspend fun extend(leaseId: Long, seconds: Long): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val status = Status.from(NativeBridge.nativeExtendLease(leaseId, seconds))
            toResult(status)
        }
    }

    suspend fun revokeAll(): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val status = Status.from(NativeBridge.nativeRevokeAll())
            toResult(status)
        }
    }

    suspend fun list(): KernelResult<List<LeaseRecord>> = withContext(Dispatchers.IO) {
        val records = LeaseRecord.decode(NativeBridge.nativeListLeases() ?: LongArray(0))
        KernelResult.Success(records)
    }

    suspend fun exec(command: String, capabilities: Long, timeoutMs: Int): KernelResult<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (ticket == null) {
                    return@withLock KernelResult.Failure(
                        Status.UNAUTHENTICATED,
                        NativeBridge.nativeStatusName(Status.UNAUTHENTICATED.code)
                    )
                }

                val status = Status.from(NativeBridge.nativeExec(command, capabilities, timeoutMs))
                toResult(status)
            }
        }

    suspend fun readAudit(limit: Int): KernelResult<List<AuditRecord>> = withContext(Dispatchers.IO) {
        val records = AuditRecord.decode(NativeBridge.nativeReadAudit(limit) ?: LongArray(0))
        KernelResult.Success(records)
    }

    suspend fun clearAudit(): KernelResult<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val status = Status.from(NativeBridge.nativeClearAudit())
            toResult(status)
        }
    }

    private fun toResult(status: Status): KernelResult<Unit> =
        if (status.isSuccess) {
            KernelResult.Success(Unit)
        } else {
            KernelResult.Failure(status, NativeBridge.nativeStatusName(status.code))
        }

    private fun buildFlags(oneShot: Boolean, inheritable: Boolean): Int {
        var flags = 0
        if (oneShot) flags = flags or LeaseRecord.LEASE_FLAG_ONE_SHOT
        if (inheritable) flags = flags or LeaseRecord.LEASE_FLAG_INHERIT
        return flags
    }

    private fun describe(validation: PassphraseEncoder.Validation): String = when (validation) {
        is PassphraseEncoder.Validation.TooShort -> "口令至少需要 ${validation.required} 个字符"
        is PassphraseEncoder.Validation.Weak -> "口令强度不足，需要至少三类字符"
        is PassphraseEncoder.Validation.Acceptable -> "口令可用"
    }
}