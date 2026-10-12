package com.caplease.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.components.LeaseCard
import com.caplease.manager.ui.components.Panel
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone

@Composable
fun LeasesScreen(
    state: ManagerUiState,
    onRelease: (Long) -> Unit,
    onExtend: (Long) -> Unit,
    onRevokeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader(
                    title = "活跃租约",
                    subtitle = "到期自动失效，无需手动清理",
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = "${state.leases.size}",
                    tone = if (state.leases.isEmpty()) Tone.Neutral else Tone.Positive
                )
            }
        }

        if (state.leases.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前没有生效中的租约",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.leases, key = { it.id }) { lease ->
                    LeaseCard(
                        lease = lease,
                        nowSeconds = state.nowSeconds,
                        onRelease = onRelease,
                        onExtend = onExtend
                    )
                }

                item {
                    Panel {
                        Text(
                            text = "撤销全部租约会立即收回所有已签发的能力，影响所有目标应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = onRevokeAll,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canOperate && !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("撤销全部租约")
                        }
                    }
                }
            }
        }
    }
}