package com.caplease.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caplease.manager.kernel.Capability
import com.caplease.manager.ui.KernelState
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.SessionState
import com.caplease.manager.ui.components.Dot
import com.caplease.manager.ui.components.MetricRow
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone

@Composable
fun OverviewScreen(
    state: ManagerUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "状态",
            subtitle = "内核补丁与租约服务概况"
        )

        Panel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tone = when (state.kernel) {
                        is KernelState.Present -> Tone.Positive
                        is KernelState.Probing -> Tone.Caution
                        else -> Tone.Critical
                    }
                    Dot(tone)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "内核服务",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusPill(
                    text = when (state.kernel) {
                        is KernelState.Present -> "已就绪"
                        is KernelState.Probing -> "检测中"
                        is KernelState.Absent -> "不可用"
                        KernelState.Unknown -> "未知"
                    },
                    tone = when (state.kernel) {
                        is KernelState.Present -> Tone.Positive
                        is KernelState.Probing -> Tone.Caution
                        else -> Tone.Critical
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            when (val kernel = state.kernel) {
                is KernelState.Present -> {
                    MetricRow(label = "补丁版本", value = kernel.info.kernelPatchVersion)
                    MetricRow(label = "内核版本", value = kernel.info.kernelVersion)
                    MetricRow(label = "协议版本", value = kernel.info.protocolVersion.toString())
                    MetricRow(label = "租约槽位", value = kernel.info.leaseSlots.toString())
                    MetricRow(
                        label = "可用能力",
                        value = "${Capability.describe(kernel.info.capabilityMask).size} 项"
                    )
                }
                is KernelState.Absent -> {
                    Text(
                        text = kernel.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "内核侧尚未加载补丁，所有租约操作均不可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "尚未完成检测",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy
            ) {
                Text("重新检测")
            }
        }

        Panel {
            MetricRow(
                label = "配对",
                value = if (state.paired) "已完成" else "未完成",
                tone = if (state.paired) Tone.Positive else Tone.Caution
            )
            MetricRow(
                label = "会话",
                value = if (state.session is SessionState.Open) "进行中" else "空闲",
                tone = if (state.session is SessionState.Open) Tone.Positive else Tone.Neutral
            )
            MetricRow(
                label = "活跃租约",
                value = state.leases.size.toString(),
                tone = if (state.leases.isEmpty()) Tone.Neutral else Tone.Caution
            )
            MetricRow(label = "审计条目", value = state.audit.size.toString())
        }

        Panel {
            SectionHeader(
                title = "设计要点",
                subtitle = "与传统授权模型的差异"
            )

            Spacer(Modifier.height(10.dp))

            BulletLine("授权按能力拆分，而非二元的 root 开关")
            BulletLine("每个租约带独立有效期，到期自动失效")
            BulletLine("授权绑定到任务，进程退出即回收")
            BulletLine("子进程只能继承更小的能力集合")
            BulletLine("支持单次执行，调用方全程保持原身份")
            BulletLine("内核侧留存审计环，可回溯每次授权与使用")
        }

        Panel {
            MetricRow(
                label = "高风险能力",
                value = "${Capability.describe(Capability.dangerousMask).size} 项",
                tone = Tone.Caution
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "高风险能力会绕过系统隔离或直接接触内核，签发前请确认目标应用可信。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Dot(Tone.Neutral, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}