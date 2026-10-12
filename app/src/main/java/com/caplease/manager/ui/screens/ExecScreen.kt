package com.caplease.manager.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caplease.manager.kernel.Capability
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.components.CapabilityCard
import com.caplease.manager.ui.components.MetricRow
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.Tone

@Composable
fun ExecScreen(
    state: ManagerUiState,
    onCommand: (String) -> Unit,
    onToggleCapability: (Capability) -> Unit,
    onTimeout: (Int) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.execDraft

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "一次性执行",
            subtitle = "以指定能力执行单条命令，进程退出即回收权限"
        )

        Panel {
            Text(
                text = "调用方进程全程保持原身份，权限只存在于被派生的子进程中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Panel {
            OutlinedTextField(
                value = draft.command,
                onValueChange = onCommand,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("命令") },
                placeholder = { Text("/system/bin/id") },
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = draft.timeoutMs.toString(),
                onValueChange = { onTimeout(it.toIntOrNull() ?: draft.timeoutMs) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("超时（毫秒）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
        }

        Panel {
            SectionHeader(
                title = "本次执行的能力",
                subtitle = "已选 ${Capability.describe(draft.capabilities).size}"
            )
        }

        Capability.entries.forEach { capability ->
            CapabilityCard(
                capability = capability,
                selected = (draft.capabilities and capability.bit) != 0L,
                onToggle = onToggleCapability
            )
        }

        Panel {
            MetricRow(
                label = "风险等级",
                value = if (Capability.isDangerous(draft.capabilities)) "高" else "常规",
                tone = if (Capability.isDangerous(draft.capabilities)) Tone.Critical else Tone.Positive
            )
            MetricRow(label = "超时", value = "${draft.timeoutMs} ms")
        }

        Button(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canOperate && draft.valid && !state.busy
        ) {
            Text("执行")
        }
    }
}