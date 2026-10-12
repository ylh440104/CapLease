package com.caplease.manager.kernel

enum class Status(val code: Int) {
    OK(0),
    NOT_READY(-1),
    BAD_MAGIC(-2),
    BAD_VERSION(-3),
    UNKNOWN_OP(-4),
    INVALID_ARGUMENT(-5),
    UNAUTHENTICATED(-6),
    BAD_CREDENTIAL(-7),
    CHALLENGE_MISSING(-8),
    CHALLENGE_EXPIRED(-9),
    ALREADY_PAIRED(-10),
    NOT_PAIRED(-11),
    CAPABILITY_DENIED(-12),
    LEASE_MISSING(-13),
    LEASE_EXPIRED(-14),
    LEASE_LIMIT_REACHED(-15),
    TARGET_DENIED(-16),
    INTERNAL(-17),
    NOT_SUPPORTED(-18),
    BUFFER_TOO_SMALL(-19),
    UNKNOWN(-1000);

    val isSuccess: Boolean get() = this == OK

    companion object {
        fun from(code: Int): Status = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

sealed interface KernelResult<out T> {
    data class Success<T>(val value: T) : KernelResult<T>
    data class Failure(val status: Status, val label: String) : KernelResult<Nothing>
}

inline fun <T> KernelResult<T>.onSuccess(block: (T) -> Unit): KernelResult<T> {
    if (this is KernelResult.Success) block(value)
    return this
}

inline fun <T> KernelResult<T>.onFailure(block: (Status, String) -> Unit): KernelResult<T> {
    if (this is KernelResult.Failure) block(status, label)
    return this
}