package com.example.sampleapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sampleapp.audio.EffectSpec
import com.example.sampleapp.audio.FxState
import kotlin.math.roundToInt

@Composable
fun EffectPedal(
    spec: EffectSpec,
    state: FxState,
    onEnabledChange: (Boolean) -> Unit,
    onParamChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
            }

            AnimatedVisibility(visible = state.enabled) {
                Column {
                    spec.params.forEach { p ->
                        val value = state.params[p.paramId] ?: p.default
                        val display = if (p.stepInt) value.roundToInt().toString()
                        else String.format("%.2f", value)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(p.label, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "$display ${p.unit}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = value,
                            onValueChange = { v ->
                                val nv = if (p.stepInt) v.roundToInt().toFloat() else v
                                onParamChange(p.paramId, nv)
                            },
                            valueRange = p.min..p.max,
                            steps = if (p.stepInt) (p.max - p.min).toInt() - 1 else 0
                        )
                    }
                }
            }
        }
    }
}
