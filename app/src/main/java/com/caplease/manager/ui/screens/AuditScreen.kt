package com.caplease.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caplease.manager.kernel.AuditRecord
import com.caplease.manager.ui.ManagerUiState
import com.caplease.manager.ui.components.Dot
import com.caplease.manager.ui.components.SectionHeader
import com.caplease.manager.ui.components.StatusPill
import com.caplease.manager.ui.components.Tone
import com.caplease.manager.ui.components.toneColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditScreen(
    state: ManagerUiState,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
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
                    title = "审计记录",
                    subtitle = "内核环形缓冲，最近 ${state.audit.size} 条",
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = "${state.audit.size}",
                    tone = if (state.audit.isEmpty()) Tone.Neutral else Tone.Positive
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = state.canOperate
                ) {
                    Text("刷新")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    enabled = state.canOperate
                ) {
                    Text("清空")
                }
            }
        }

        if (state.audit.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无审计记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.audit) { record ->
                    AuditRow(record)
                }
            }
        }
    }
}

@Composable
private fun AuditRow(record: AuditRecord) {
    val tone = when (record.kind.name) {
        "CAPABILITY_REFUSED", "LEASE_EXPIRED" -> Tone.Critical
        "LEASE_GRANTED", "EXEC_COMPLETED" -> Tone.Caution
        "SESSION_OPENED", "SESSION_CLOSED" -> Tone.Positive
        else -> Tone.Neutral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(tone)

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.kind.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "uid ${record.actorUid} · pid ${record.actorPid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatTimestamp(record.timestampSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = toneColor(tone)
        )
    }
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

private fun formatTimestamp(seconds: Long): String =
    timeFormatter.format(Date(seconds * 1000L))