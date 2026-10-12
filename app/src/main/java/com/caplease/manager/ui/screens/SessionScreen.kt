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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caplease.manager.core.PassphraseEncoder
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.SessionState
import com.caplease.manager.ui.components.MetricRow
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone

@Composable
fun SessionScreen(
    state: ManagerUiState,
    onPair: (CharArray) -> Unit,
    onUnpair: () -> Unit,
    onOpen: (CharArray) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var secret by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }

    val sessionOpen = state.session as? SessionState.Open

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "会话",
            subtitle = "凭据用于与内核完成一次性握手，不落盘明文"
        )

        Panel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "配对状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = if (state.paired) "已配对" else "未配对",
                    tone = if (state.paired) Tone.Positive else Tone.Caution
                )
            }

            Spacer(Modifier.height(10.dp))

            MetricRow(
                label = "会话状态",
                value = when {
                    sessionOpen != null -> "已建立 · 窗口 ${sessionOpen.ticket.windowSeconds}s"
                    state.session is SessionState.Opening -> "建立中"
                    else -> "未建立"
                },
                tone = if (sessionOpen != null) Tone.Positive else Tone.Neutral
            )
        }

        Panel {
            OutlinedTextField(
                value = secret,
                onValueChange = {
                    secret = it
                    hint = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("通行口令") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            val evaluation = remember(secret) {
                if (secret.isEmpty()) null else PassphraseEncoder.validate(secret.toCharArray())
            }

            if (evaluation != null) {
                Spacer(Modifier.height(8.dp))

                val label = when (evaluation) {
                    is PassphraseEncoder.Validation.Acceptable -> "强度 ${evaluation.entropyBits.toInt()} bit"
                    is PassphraseEncoder.Validation.TooShort -> "至少 ${evaluation.required} 位"
                    is PassphraseEncoder.Validation.Weak -> "需要大小写、数字、符号中至少三类"
                }

                val tone = when (evaluation) {
                    is PassphraseEncoder.Validation.Acceptable -> Tone.Positive
                    else -> Tone.Caution
                }

                StatusPill(text = label, tone = tone)
            }

            val currentHint = hint
            if (currentHint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.paired) {
                    Button(
                        onClick = {
                            val validation = PassphraseEncoder.validate(secret.toCharArray())
                            if (validation is PassphraseEncoder.Validation.Acceptable) {
                                onPair(secret.toCharArray())
                                secret = ""
                            } else {
                                hint = "口令不满足强度要求"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy && secret.isNotEmpty()
                    ) {
                        Text("建立配对")
                    }
                } else {
                    Button(
                        onClick = {
                            onOpen(secret.toCharArray())
                            secret = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy && secret.isNotEmpty() && sessionOpen == null
                    ) {
                        Text("开启会话")
                    }

                    OutlinedButton(
                        onClick = {
                            onClose()
                            secret = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy && sessionOpen != null
                    ) {
                        Text("结束会话")
                    }
                }
            }

            if (state.paired) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onUnpair()
                        secret = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("解除配对并清除本地盐值")
                }
            }
        }

        Panel {
            SectionHeader(title = "当前会话凭据")
            Spacer(Modifier.height(8.dp))

            MetricRow(label = "会话标识", value = sessionOpen?.ticket?.sessionId?.toString() ?: "—")

            MetricRow(
                label = "剩余窗口",
                value = sessionOpen?.let {
                    val elapsed = state.nowSeconds - it.ticket.issuedSeconds
                    "${(it.ticket.windowSeconds - elapsed).coerceAtLeast(0L)}s"
                } ?: "—"
            )

            MetricRow(label = "口令明文落盘", value = "否", tone = Tone.Positive)
        }
    }
}