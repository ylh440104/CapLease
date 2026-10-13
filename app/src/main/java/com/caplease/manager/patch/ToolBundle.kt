package com.caplease.manager.patch

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ToolBundle(private val context: Context) {

    private val root: File by lazy { File(context.filesDir, "tools") }

    fun kptools(): File = materialize(KPTOOLS, KPTOOLS_DIGEST)

    fun kpimg(): File = materialize(KPIMG, KPIMG_DIGEST)

    private fun materialize(name: String, digest: String): File {
        root.mkdirs()

        val target = File(root, name)

        if (target.isFile && target.length() > 0 && digestOf(target) == digest) {
            target.setExecutable(true, true)
            return target
        }

        val staging = File(root, name + ".staging")
        if (staging.exists()) {
            staging.delete()
        }

        context.assets.open("tools/" + name).use { source ->
            staging.outputStream().use { sink ->
                source.copyTo(sink, DEFAULT_BUFFER_SIZE)
            }
        }

        if (digestOf(staging) != digest) {
            staging.delete()
            throw IOException("tool integrity check failed for " + name)
        }

        if (target.exists()) {
            target.delete()
        }

        if (!staging.renameTo(target)) {
            staging.copyTo(target, overwrite = true)
            staging.delete()
        }

        target.setReadable(true, true)
        target.setExecutable(true, true)
        target.setWritable(false, false)

        return target
    }

    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KPTOOLS = "kptools"
        private const val KPIMG = "kpimg"

        private const val KPTOOLS_DIGEST =
            "63a8656a79b74baadcf0ce57a7e89a8c8a94beb35b25df58aec0bcf1d066bc5b"

        private const val KPIMG_DIGEST =
            "eb2e3d16fe69410150efcf6f9425baf8cad9ac0c39f051fc8b40ce57c7143adb"
    }
}
