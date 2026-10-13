package com.caplease.manager.patch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class BootPatcher(private val context: Context) {

    private val tools = ToolBundle(context)

    suspend fun inspect(source: File): BootInspection = withContext(Dispatchers.IO) {
        if (!source.isFile || !source.canRead()) {
            throw PatchException(PatchError.StorageFailure("无法读取 " + source.absolutePath))
        }

        val header = source.readRange(0L, 64)
        if (header.size < 64) {
            throw PatchException(PatchError.NotBootImage("文件过小"))
        }

        val magic = header.copyOfRange(0, 8).asAscii()
        val vendorMagic = header.copyOfRange(0, 8).asAscii()

        val format = when {
            magic == "ANDROID!" -> BootFormat.ANDROID_BOOT
            vendorMagic == "VNDRBOOT" -> BootFormat.VENDOR_BOOT
            else -> BootFormat.UNKNOWN
        }

        if (format == BootFormat.UNKNOWN) {
            throw PatchException(PatchError.NotBootImage("魔数 " + magic))
        }

        val kernelSize = header.uint32At(8)
        val ramdiskSize = header.uint32At(16)
        val pageSize = header.uint32At(36).toInt()
        val headerVersion = header.uint32At(40).toInt()

        val tail = source.readRange(source.length() - 64L, 64)
        val avbPresent = tail.size == 64 && tail.copyOfRange(0, 4).asAscii() == "AVBf"

        val notes = mutableListOf<String>()
        if (avbPresent) {
            notes.add("包含 AVB 校验页脚，刷入前需处理 vbmeta")
        }
        if (pageSize != 2048 && pageSize != 4096) {
            notes.add("页大小 " + pageSize + "，非常见值")
        }
        if (kernelSize == 0L) {
            notes.add("内核段为空，可能不是启动镜像")
        }

        BootInspection(
            path = source.absolutePath,
            sizeBytes = source.length(),
            format = format,
            kernelSize = kernelSize,
            ramdiskSize = ramdiskSize,
            pageSize = pageSize,
            headerVersion = headerVersion,
            alreadyPatched = false,
            avbFooterPresent = avbPresent,
            kernelVersion = null,
            notes = notes
        )
    }

    suspend fun patch(
        source: File,
        output: File,
        superKey: String
    ): PatchOutcome = withContext(Dispatchers.IO) {
        if (superKey.length < MIN_SUPERKEY_LENGTH) {
            throw PatchException(PatchError.ToolMissing("超级密钥至少 " + MIN_SUPERKEY_LENGTH + " 位"))
        }

        val kptools = try {
            tools.kptools()
        } catch (error: Exception) {
            throw PatchException(PatchError.ToolMissing(error.message ?: "kptools"))
        }

        val kpimg = try {
            tools.kpimg()
        } catch (error: Exception) {
            throw PatchException(PatchError.ToolMissing(error.message ?: "kpimg"))
        }

        if (!kptools.canExecute()) {
            throw PatchException(PatchError.ToolMissing("kptools 不可执行"))
        }

        val inspection = inspect(source)

        if (inspection.format != BootFormat.ANDROID_BOOT) {
            throw PatchException(PatchError.UnsupportedFormat(inspection.format.name))
        }

        output.parentFile?.mkdirs()
        if (output.exists()) {
            output.delete()
        }

        val started = System.currentTimeMillis()

        val command = listOf(
            kptools.absolutePath,
            "-p",
            "-i", source.absolutePath,
            "-k", kpimg.absolutePath,
            "-s", superKey,
            "-o", output.absolutePath
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val log = mutableListOf<String>()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (log.size < MAX_LOG_LINES) {
                    log.add(line)
                }
            }
        }

        val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw PatchException(PatchError.ProcessFailed(-1, "超时"))
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw PatchException(PatchError.ProcessFailed(exitCode, log.takeLast(6).joinToString("\n")))
        }

        if (!output.isFile || output.length() == 0L) {
            throw PatchException(PatchError.ProcessFailed(exitCode, "未生成输出文件"))
        }

        PatchOutcome(
            outputPath = output.absolutePath,
            outputSize = output.length(),
            log = log,
            durationMs = System.currentTimeMillis() - started
        )
    }

    companion object {
        const val MIN_SUPERKEY_LENGTH = 8
        private const val MAX_LOG_LINES = 200
        private const val PROCESS_TIMEOUT_SECONDS = 180L
    }
}
