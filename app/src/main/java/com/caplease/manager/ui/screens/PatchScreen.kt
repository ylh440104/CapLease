package com.caplease.manager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caplease.manager.patch.BootFormat
import com.caplease.manager.patch.BootPatcher
import com.caplease.manager.patch.PatchStage
import com.caplease.manager.patch.PatchUiState
import com.caplease.manager.ui.components.MetricRow
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone

@Composable
fun PatchScreen(
    state: PatchUiState,
    onImport: (android.net.Uri) -> Unit,
    onSuperKey: (String) -> Unit,
    onPatch: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "修补启动镜像",
            subtitle = "导入 boot 镜像，在设备上直接完成内核补丁"
        )

        Panel {
            Text(
                text = "选中的镜像会复制到应用私有目录再处理，原始文件不被修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy
            ) {
                Text(if (state.sourceName == null) "选择 boot 镜像" else "重新选择")
            }
        }

        when (val stage = state.stage) {
            is PatchStage.Inspecting -> Panel {
                StatusPill(text = "正在识别", tone = Tone.Caution)
            }

            is PatchStage.Ready -> {
                Panel {
                    SectionHeader(title = "镜像信息")
                    Spacer(Modifier.height(8.dp))

                    MetricRow(label = "文件名", value = state.sourceName ?: "—")
                    MetricRow(
                        label = "大小",
                        value = formatSize(stage.inspection.sizeBytes)
                    )
                    MetricRow(
                        label = "格式",
                        value = when (stage.inspection.format) {
                            BootFormat.ANDROID_BOOT -> "Android 启动镜像"
                            BootFormat.VENDOR_BOOT -> "vendor_boot"
                            BootFormat.INIT_BOOT -> "init_boot"
                            BootFormat.UNKNOWN -> "未知"
                        }
                    )
                    MetricRow(
                        label = "内核段",
                        value = formatSize(stage.inspection.kernelSize)
                    )
                    MetricRow(
                        label = "ramdisk 段",
                        value = formatSize(stage.inspection.ramdiskSize)
                    )
                    MetricRow(
                        label = "页大小",
                        value = stage.inspection.pageSize.toString()
                    )
                    MetricRow(
                        label = "头部版本",
                        value = stage.inspection.headerVersion.toString()
                    )
                    MetricRow(
                        label = "AVB 页脚",
                        value = if (stage.inspection.avbFooterPresent) "有" else "无",
                        tone = if (stage.inspection.avbFooterPresent) Tone.Caution else Tone.Neutral
                    )
                }

                if (stage.inspection.notes.isNotEmpty()) {
                    Panel {
                        SectionHeader(title = "提示")
                        Spacer(Modifier.height(6.dp))
                        stage.inspection.notes.forEach { note ->
                            Text(
                                text = "· " + note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Panel {
                    SectionHeader(
                        title = "超级密钥",
                        subtitle = "用于解锁管理器，至少 " + BootPatcher.MIN_SUPERKEY_LENGTH + " 位"
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.superKey,
                        onValueChange = onSuperKey,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("超级密钥") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onPatch,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canPatch
                    ) {
                        Text("开始修补")
                    }
                }
            }

            is PatchStage.Patching -> Panel {
                StatusPill(text = "正在修补，请勿离开", tone = Tone.Caution)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "内核镜像需要解压、重打包，通常耗时数十秒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is PatchStage.Done -> Panel {
                StatusPill(text = "修补完成", tone = Tone.Positive)

                Spacer(Modifier.height(10.dp))

                MetricRow(label = "输出文件", value = stage.outcome.outputPath)
                MetricRow(label = "输出大小", value = formatSize(stage.outcome.outputSize))
                MetricRow(label = "耗时", value = stage.outcome.durationMs.toString() + " ms")

                if (stage.outcome.log.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "处理日志",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    stage.outcome.log.takeLast(12).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("再修补一个")
                }
            }

            is PatchStage.Failed -> Panel {
                StatusPill(text = "失败", tone = Tone.Critical)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stage.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重试")
                }
            }

            else -> Unit
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return bytes.toString() + " B"

    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KiB", kb)

    val mb = kb / 1024.0
    return String.format("%.1f MiB", mb)
}
