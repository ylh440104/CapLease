package com.caplease.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caplease.manager.kernel.Capability
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.components.CapabilityCard
import com.caplease.manager.ui.components.MetricRow
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone
import com.caplease.manager.ui.components.formatDuration
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun GrantScreen(
    state: ManagerUiState,
    onToggle: (Capability) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDuration: (Long) -> Unit,
    onOneShot: () -> Unit,
    onInheritable: () -> Unit,
    onPackage: (String) -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.leaseDraft

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "签发租约",
            subtitle = "选择能力与有效期，授权只在租期内生效"
        )

        if (!state.canOperate) {
            Panel {
                Text(
                    text = "需要先建立会话才能签发租约",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Panel {
            OutlinedTextField(
                value = draft.packageName,
                onValueChange = onPackage,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("目标包名") },
                placeholder = { Text("com.example.app") },
                singleLine = true
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "有效期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(text = formatDuration(draft.seconds), tone = Tone.Positive)
            }

            Slider(
                value = durationToFraction(draft.seconds),
                onValueChange = { onDuration(fractionToDuration(it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DURATION_PRESETS.forEach { preset ->
                    OutlinedButton(
                        onClick = { onDuration(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 4.dp,
                            vertical = 6.dp
                        )
                    ) {
                        Text(
                            text = formatDuration(preset),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Panel {
            SectionHeader(
                title = "能力",
                subtitle = "已选 ${draft.selected.size} / ${Capability.entries.size}"
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("全选")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }
        }

        Capability.entries.forEach { capability ->
            CapabilityCard(
                capability = capability,
                selected = (draft.capabilities and capability.bit) != 0L,
                onToggle = onToggle
            )
        }

        Panel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "一次性",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "执行完成后立即回收，不驻留",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = draft.oneShot, onCheckedChange = { onOneShot() })
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "可继承",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "子进程可继承，但权限只能收缩",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = draft.inheritable, onCheckedChange = { onInheritable() })
            }
        }

        if (draft.dangerous) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(text = "高风险组合", tone = Tone.Critical)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "所选能力包含可直接控制系统或绕过隔离的权限，请确认目标应用可信。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Panel {
            MetricRow(label = "已选能力", value = draft.selected.size.toString())
            MetricRow(
                label = "风险等级",
                value = if (draft.dangerous) "高" else "常规",
                tone = if (draft.dangerous) Tone.Critical else Tone.Positive
            )
            MetricRow(label = "有效期", value = formatDuration(draft.seconds))
        }

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canOperate && draft.valid && !state.busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (draft.dangerous) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(if (draft.dangerous) "确认签发高风险租约" else "签发租约")
        }
    }
}

private val DURATION_PRESETS = listOf(60L, 300L, 900L, 3600L)

private const val DURATION_MIN_SECONDS = 5.0
private const val DURATION_MAX_SECONDS = 86400.0

private fun durationToFraction(seconds: Long): Float {
    val clamped = seconds.toDouble().coerceIn(DURATION_MIN_SECONDS, DURATION_MAX_SECONDS)
    val normalized = ln(clamped / DURATION_MIN_SECONDS) / ln(DURATION_MAX_SECONDS / DURATION_MIN_SECONDS)
    return normalized.toFloat()
}

private fun fractionToDuration(fraction: Float): Long {
    val normalized = fraction.toDouble().coerceIn(0.0, 1.0)
    val ratio = DURATION_MAX_SECONDS / DURATION_MIN_SECONDS
    val seconds = DURATION_MIN_SECONDS * ratio.pow(normalized)
    return seconds.toLong().coerceAtLeast(DURATION_MIN_SECONDS.toLong())
}