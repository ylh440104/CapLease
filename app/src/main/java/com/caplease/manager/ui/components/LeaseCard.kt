package com.caplease.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caplease.manager.kernel.Capability
import com.caplease.manager.kernel.LeaseRecord

@Composable
fun LeaseCard(
    lease: LeaseRecord,
    nowSeconds: Long,
    onRelease: (Long) -> Unit,
    onExtend: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = lease.remainingSeconds(nowSeconds)
    val total = (lease.expirySeconds - nowSeconds + remaining).coerceAtLeast(1L)
    val fraction = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val tone = when {
        remaining <= 10L -> Tone.Critical
        remaining <= 60L -> Tone.Caution
        Capability.isDangerous(lease.capabilities) -> Tone.Caution
        else -> Tone.Positive
    }

    val accent = toneColor(tone)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(tone)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "#${lease.id}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = formatDuration(remaining),
                tone = tone
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "uid ${lease.ownerUid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(9.dp))

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Capability.describe(lease.capabilities).take(MAX_VISIBLE_CAPABILITIES).forEach { capability ->
                StatusPill(
                    text = capability.title,
                    tone = if (capability.dangerous) Tone.Caution else Tone.Positive
                )
            }

            val overflow = Capability.describe(lease.capabilities).size - MAX_VISIBLE_CAPABILITIES
            if (overflow > 0) {
                StatusPill(text = "+$overflow", tone = Tone.Neutral)
            }
        }

        Spacer(Modifier.height(11.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                label = "延长",
                tone = Tone.Neutral,
                modifier = Modifier.weight(1f),
                onClick = { onExtend(lease.id) }
            )
            ActionChip(
                label = "释放",
                tone = Tone.Critical,
                modifier = Modifier.weight(1f),
                onClick = { onRelease(lease.id) }
            )
        }

        if (lease.flags != 0) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lease.isOneShot) StatusPill(text = "一次性", tone = Tone.Neutral)
                if (lease.isInheritable) StatusPill(text = "可继承", tone = Tone.Neutral)
                if (lease.isSilent) StatusPill(text = "免审计", tone = Tone.Caution)
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = toneColor(tone)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(7.dp))
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "已到期"
    if (seconds < 60L) return "${seconds}s"

    val minutes = seconds / 60L
    if (minutes < 60L) return "${minutes}m ${seconds % 60L}s"

    val hours = minutes / 60L
    return "${hours}h ${minutes % 60L}m"
}

private const val MAX_VISIBLE_CAPABILITIES = 3