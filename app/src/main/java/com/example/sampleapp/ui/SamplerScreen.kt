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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sampleapp.audio.EFFECT_CATALOG
import com.example.sampleapp.audio.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplerScreen(
    state: UiState,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLoop: (Boolean) -> Unit,
    onReverse: () -> Unit,
    onNormalize: () -> Unit,
    onTrimChange: (Float, Float) -> Unit,
    onEffectEnabled: (Int, Boolean) -> Unit,
    onParamChange: (Int, Int, Float) -> Unit,
    onExport: () -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sampler FX") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TransportBar(
                    state = state,
                    micGranted = micGranted,
                    onRequestMic = onRequestMic,
                    onToggleRecord = onToggleRecord,
                    onTogglePlay = onTogglePlay,
                    onToggleLoop = onToggleLoop
                )
            }

            item {
                if (state.hasSample) {
                    WaveformView(
                        waveform = state.waveform,
                        playHeadFraction = state.playHeadFraction,
                        trimStart = state.trimStart,
                        trimEnd = state.trimEnd,
                        onTrimChange = onTrimChange
                    )
                    Text(
                        "Durée : ${"%.2f".format(state.durationSec)} s · " +
                                "${state.sampleRate} Hz · glisse pour rogner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Appuie sur le bouton rouge pour enregistrer un son.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (state.hasSample) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReverse) {
                            Icon(Icons.Default.SwapHoriz, null)
                            Spacer(Modifier.size(4.dp))
                            Text("Reverse")
                        }
                        OutlinedButton(onClick = onNormalize) {
                            Icon(Icons.Default.GraphicEq, null)
                            Spacer(Modifier.size(4.dp))
                            Text("Normaliser")
                        }
                        Button(onClick = onExport) { Text("Export WAV") }
                    }
                }
            }

            item {
                Text(
                    "Chaîne d'effets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(EFFECT_CATALOG, key = { it.effectId }) { spec ->
                val fx = state.effects[spec.effectId]
                if (fx != null) {
                    EffectPedal(
                        spec = spec,
                        state = fx,
                        onEnabledChange = { onEffectEnabled(spec.effectId, it) },
                        onParamChange = { pid, v -> onParamChange(spec.effectId, pid, v) }
                    )
                }
            }

            item {
                PresetSection(
                    presetNames = state.presetNames,
                    onSave = onSavePreset,
                    onLoad = onLoadPreset,
                    onDelete = onDeletePreset
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TransportBar(
    state: UiState,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLoop: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (micGranted) onToggleRecord() else onRequestMic() },
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (state.isRecording) Color(0xFFFF5252) else Color(0xFFFF5252)
            )
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = "Enregistrer",
                modifier = Modifier.size(if (state.isRecording) 36.dp else 48.dp)
            )
        }

        IconButton(
            onClick = onTogglePlay,
            enabled = state.hasSample && !state.isRecording,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = "Lire",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        FilledIconToggleButton(
            checked = state.loop,
            onCheckedChange = onToggleLoop,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Repeat, contentDescription = "Boucle")
        }

        Spacer(Modifier.size(4.dp))
        Text(
            when {
                state.isRecording -> "REC"
                state.isPlaying -> "PLAY"
                else -> "PRÊT"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSection(
    presetNames: List<String>,
    onSave: (String) -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    Column {
        Text(
            "Presets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du preset") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (name.isNotBlank()) { onSave(name.trim()); name = "" } },
                enabled = name.isNotBlank()
            ) { Text("Sauver") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetNames.forEach { p ->
                AssistChip(
                    onClick = { onLoad(p) },
                    label = { Text(p) },
                    trailingIcon = {
                        IconButton(onClick = { onDelete(p) }, modifier = Modifier.size(20.dp)) {
                            Text("×", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    }
}
