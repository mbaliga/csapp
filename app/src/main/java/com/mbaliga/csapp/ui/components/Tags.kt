package com.mbaliga.csapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.domain.ai.Provenance
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.theme.LocalProvenanceColors

@Composable
fun SeverityTag(s: Severity, modifier: Modifier = Modifier) {
    val m = Marks.severity(s)
    Text(
        "${m.glyph} ${m.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.alpha(Marks.severityLuminance(s)).semantics { contentDescription = "Severity ${m.label}" },
    )
}

@Composable
fun StatusTag(s: IncidentStatus, modifier: Modifier = Modifier) {
    val m = Marks.status(s)
    Text(
        "${m.glyph} ${m.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { contentDescription = "Status ${m.label}" },
    )
}

@Composable
fun ChannelTag(t: SignalType, modifier: Modifier = Modifier) {
    val m = Marks.channel(t)
    Text(
        "${m.glyph} ${m.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { contentDescription = "Channel ${m.label}" },
    )
}

/** Local = radium glow, cloud = alien glow; always glyph + word + model name. */
@Composable
fun ProvenanceBadge(p: Provenance, model: String, modifier: Modifier = Modifier) {
    val c = LocalProvenanceColors.current
    val (glyph, word, color) = when (p) {
        Provenance.Local -> Triple("◉", "On-device", c.local)
        is Provenance.Cloud -> Triple("◌", "Cloud · ${p.providerId}", c.cloud)
    }
    Row(modifier.semantics { contentDescription = "$word model $model" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(glyph, color = color, style = MaterialTheme.typography.labelMedium)
        Text("$word · $model", color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 4.dp))
}
