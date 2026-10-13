package com.caplease.manager.patch

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

class ToolBundle(private val context: Context) {

    fun kptools(): File = locate(KPTOOLS_LIBRARY)

    fun kpimg(): File = locate(KPIMG_LIBRARY)

    private fun locate(name: String): File {
        val info: ApplicationInfo = context.applicationInfo

        val directory = info.nativeLibraryDir
            ?: throw IllegalStateException("native library directory unavailable")

        val candidate = File(directory, name)
        if (candidate.isFile && candidate.length() > 0L) {
            return candidate
        }

        val fallback = File(context.filesDir, name)
        if (fallback.isFile && fallback.length() > 0L) {
            fallback.setExecutable(true, true)
            return fallback
        }

        throw IllegalStateException("missing " + name + " under " + directory)
    }

    companion object {
        private const val KPTOOLS_LIBRARY = "libkptools.so"
        private const val KPIMG_LIBRARY = "libkpimg.so"
    }
}
