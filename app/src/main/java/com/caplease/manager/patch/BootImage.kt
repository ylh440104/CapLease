package com.caplease.manager.patch

import java.io.File

data class BootInspection(
    val path: String,
    val sizeBytes: Long,
    val format: BootFormat,
    val kernelSize: Long,
    val ramdiskSize: Long,
    val pageSize: Int,
    val headerVersion: Int,
    val alreadyPatched: Boolean,
    val avbFooterPresent: Boolean,
    val kernelVersion: String?,
    val notes: List<String>
)

enum class BootFormat {
    ANDROID_BOOT,
    VENDOR_BOOT,
    INIT_BOOT,
    UNKNOWN
}

data class PatchOutcome(
    val outputPath: String,
    val outputSize: Long,
    val log: List<String>,
    val durationMs: Long
)

sealed interface PatchError {
    data class ToolMissing(val detail: String) : PatchError
    data class NotBootImage(val detail: String) : PatchError
    data class AlreadyPatched(val detail: String) : PatchError
    data class UnsupportedFormat(val detail: String) : PatchError
    data class ProcessFailed(val exitCode: Int, val detail: String) : PatchError
    data class StorageFailure(val detail: String) : PatchError
}

class PatchException(val error: PatchError) : Exception(describe(error)) {

    companion object {
        private fun describe(error: PatchError): String = when (error) {
            is PatchError.ToolMissing -> "工具缺失：" + error.detail
            is PatchError.NotBootImage -> "不是有效的启动镜像：" + error.detail
            is PatchError.AlreadyPatched -> "该镜像已包含补丁：" + error.detail
            is PatchError.UnsupportedFormat -> "不支持的镜像格式：" + error.detail
            is PatchError.ProcessFailed -> "修补进程失败（退出码 " + error.exitCode + "）：" + error.detail
            is PatchError.StorageFailure -> "存储操作失败：" + error.detail
        }
    }
}

internal fun File.readRange(offset: Long, length: Int): ByteArray {
    if (!isFile || length() < offset + length) return ByteArray(0)

    val buffer = ByteArray(length)
    inputStream().use { stream ->
        var skipped = 0L
        while (skipped < offset) {
            val step = stream.skip(offset - skipped)
            if (step <= 0) return ByteArray(0)
            skipped += step
        }
        var filled = 0
        while (filled < length) {
            val read = stream.read(buffer, filled, length - filled)
            if (read <= 0) return ByteArray(0)
            filled += read
        }
    }
    return buffer
}

internal fun ByteArray.asAscii(): String =
    buildString(size) { for (byte in this@asAscii) append((byte.toInt() and 0xFF).toChar()) }

internal fun ByteArray.uint32At(offset: Int): Long {
    if (offset + 4 > size) return 0
    return (this[offset].toLong() and 0xFF)
        or ((this[offset + 1].toLong() and 0xFF) shl 8)
        or ((this[offset + 2].toLong() and 0xFF) shl 16)
        or ((this[offset + 3].toLong() and 0xFF) shl 24)
}

internal fun ByteArray.uint32BigEndianAt(offset: Int): Long {
    if (offset + 4 > size) return 0
    return ((this[offset].toLong() and 0xFF) shl 24)
        or ((this[offset + 1].toLong() and 0xFF) shl 16)
        or ((this[offset + 2].toLong() and 0xFF) shl 8)
        or (this[offset + 3].toLong() and 0xFF)
}
