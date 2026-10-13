package com.caplease.manager.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caplease.manager.patch.BootPatcher
import com.caplease.manager.patch.PatchException
import com.caplease.manager.patch.PatchStage
import com.caplease.manager.patch.PatchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PatchViewModel(application: Application) : AndroidViewModel(application) {

    private val patcher = BootPatcher(application)

    private val _state = MutableStateFlow(PatchUiState())
    val state: StateFlow<PatchUiState> = _state.asStateFlow()

    private var sourceFile: File? = null

    fun importBoot(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(stage = PatchStage.Inspecting) }

            try {
                val name = displayName(uri)
                val staged = stage(uri, name)
                sourceFile = staged

                val inspection = patcher.inspect(staged)

                _state.update {
                    it.copy(
                        stage = PatchStage.Ready(inspection),
                        sourceName = name,
                        sourcePath = staged.absolutePath
                    )
                }
            } catch (error: PatchException) {
                sourceFile = null
                _state.update {
                    it.copy(stage = PatchStage.Failed(error.message ?: "导入失败"))
                }
            } catch (error: Exception) {
                sourceFile = null
                _state.update {
                    it.copy(stage = PatchStage.Failed(error.message ?: "导入失败"))
                }
            }
        }
    }

    fun updateSuperKey(value: String) {
        _state.update { it.copy(superKey = value) }
    }

    fun patch() {
        val source = sourceFile ?: return
        val key = _state.value.superKey

        viewModelScope.launch {
            _state.update { it.copy(stage = PatchStage.Patching) }

            try {
                val output = outputTarget()
                val outcome = patcher.patch(source, output, key)

                _state.update {
                    it.copy(
                        stage = PatchStage.Done(outcome),
                        lastOutput = outcome.outputPath,
                        superKey = ""
                    )
                }
            } catch (error: PatchException) {
                _state.update {
                    it.copy(stage = PatchStage.Failed(error.message ?: "修补失败"))
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(stage = PatchStage.Failed(error.message ?: "修补失败"))
                }
            }
        }
    }

    fun reset() {
        sourceFile = null
        _state.value = PatchUiState()
    }

    private fun outputTarget(): File {
        val base = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir

        val directory = File(base, "patched")
        directory.mkdirs()

        val stamp = System.currentTimeMillis() / 1000L
        return File(directory, "caplease-boot-" + stamp + ".img")
    }

    private suspend fun stage(uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        val directory = File(getApplication<Application>().cacheDir, "incoming")
        directory.mkdirs()

        val target = File(directory, name)
        if (target.exists()) {
            target.delete()
        }

        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw PatchException(
                com.caplease.manager.patch.PatchError.StorageFailure("无法打开所选文件")
            )

        input.use { source ->
            target.outputStream().use { sink ->
                source.copyTo(sink, DEFAULT_BUFFER_SIZE)
            }
        }

        target
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val value = cursor.getString(index)
                if (!value.isNullOrBlank()) return value
            }
        }

        return "boot.img"
    }
}
