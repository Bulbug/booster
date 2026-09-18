package com.gameboostx.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.data.model.BoostCheckItem
import com.gameboostx.app.data.model.PerformanceStatus
import com.gameboostx.app.ui.theme.DangerRed
import com.gameboostx.app.ui.theme.SurfaceElevated
import com.gameboostx.app.ui.theme.WarningAmber

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusColorFor(status: PerformanceStatus) = when (status) {
    PerformanceStatus.EXCELLENT, PerformanceStatus.GOOD -> MaterialTheme.colorScheme.primary
    PerformanceStatus.NORMAL -> MaterialTheme.colorScheme.onSurface
    PerformanceStatus.HIGH_BACKGROUND_LOAD -> WarningAmber
    PerformanceStatus.THERMAL_LIMITED -> DangerRed
    PerformanceStatus.BATTERY_SAVING -> WarningAmber
}

@Composable
fun BoostCheckRow(item: BoostCheckItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (item.passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (item.passed) MaterialTheme.colorScheme.primary else WarningAmber,
        )
        Column {
            Text(item.label, style = MaterialTheme.typography.titleMedium)
            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
