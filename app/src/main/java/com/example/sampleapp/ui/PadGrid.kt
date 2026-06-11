package com.example.sampleapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sampleapp.audio.Library
import com.example.sampleapp.audio.PadState
import com.example.sampleapp.audio.Trigger

private fun modeLabel(mode: Int): String = when (mode) {
    Trigger.GATE -> "Gate"
    Trigger.LOOP -> "Loop"
    else -> "One-shot"
}

@Composable
fun PadGrid(
    pads: List<PadState>,
    selectedPad: Int,
    library: Library,
    onPlayPad: (Int) -> Unit,
    onReleasePad: (Int) -> Unit,
    onSelectPad: (Int) -> Unit,
    onSetMode: (Int, Int) -> Unit,
    onAssign: (Int, String) -> Unit,
    onClear: (Int) -> Unit
) {
    var assignTarget by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Pads",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Touche un pad pour le jouer et le sélectionner. Menu ⋮ pour assigner / mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))

        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    val pad = pads.getOrNull(index) ?: PadState(index)
                    Pad(
                        pad = pad,
                        selected = index == selectedPad,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onPlay = { onPlayPad(index) },
                        onRelease = { onReleasePad(index) },
                        onOpenAssign = { assignTarget = index },
                        onSetMode = { mode -> onSetMode(index, mode) },
                        onClear = { onClear(index) }
                    )
                }
            }
        }
    }

    assignTarget?.let { pad ->
        AssignSampleDialog(
            library = library,
            onChoose = { sampleId -> onAssign(pad, sampleId); assignTarget = null },
            onDismiss = { assignTarget = null }
        )
    }
}

@Composable
private fun Pad(
    pad: PadState,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onRelease: () -> Unit,
    onOpenAssign: () -> Unit,
    onSetMode: (Int) -> Unit,
    onClear: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val baseColor = if (pad.hasSample) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(baseColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(pad.index, pad.triggerMode, pad.hasSample) {
                detectTapGestures(
                    onPress = {
                        // playPad déclenche la lecture ET sélectionne le pad
                        onPlay()
                        // Maintien : NOTE_OFF au relâchement (utile en mode gate)
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "${pad.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options du pad")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Assigner depuis la bibliothèque") },
                            onClick = { menu = false; onOpenAssign() }
                        )
                        DropdownMenuItem(
                            text = { Text("Mode : One-shot") },
                            onClick = { menu = false; onSetMode(Trigger.ONESHOT) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mode : Gate") },
                            onClick = { menu = false; onSetMode(Trigger.GATE) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mode : Loop") },
                            onClick = { menu = false; onSetMode(Trigger.LOOP) }
                        )
                        if (pad.hasSample) {
                            DropdownMenuItem(
                                text = { Text("Vider le pad") },
                                onClick = { menu = false; onClear() }
                            )
                        }
                    }
                }
            }

            Text(
                pad.name ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (pad.hasSample) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                modeLabel(pad.triggerMode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun AssignSampleDialog(
    library: Library,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = library.samples
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assigner un sample") },
        text = {
            if (options.isEmpty()) {
                Text("Aucun sample dans la bibliothèque. Enregistre puis sauvegarde un son.")
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.lazy.items(options, key = { it.id }) { sample ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(sample.id) {
                                    detectTapGestures(onTap = { onChoose(sample.id) })
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(sample.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${sample.category} · ${"%.2f".format(sample.durationSec)} s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
