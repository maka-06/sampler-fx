package com.example.sampleapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Affiche la forme d'onde, la zone de trim (poignées glissables) et la tête de lecture.
 */
@Composable
fun WaveformView(
    waveform: List<Float>,
    playHeadFraction: Float,
    trimStart: Float,
    trimEnd: Float,
    onTrimChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    waveColor: Color = Color(0xFF1ED760),
    selectionColor: Color = Color(0x331ED760),
    playHeadColor: Color = Color(0xFFFFFFFF)
) {
    var width by remember { mutableStateOf(1f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(waveform.size) {
                detectHorizontalDragGestures { change, _ ->
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    // Déplace la poignée la plus proche
                    if (abs(frac - trimStart) <= abs(frac - trimEnd)) {
                        onTrimChange(frac.coerceAtMost(trimEnd - 0.01f), trimEnd)
                    } else {
                        onTrimChange(trimStart, frac.coerceAtLeast(trimStart + 0.01f))
                    }
                }
            }
    ) {
        width = size.width
        val midY = size.height / 2f
        val n = waveform.size

        // Zone sélectionnée (trim)
        val selLeft = trimStart * size.width
        val selRight = trimEnd * size.width
        drawRect(
            color = selectionColor,
            topLeft = Offset(selLeft, 0f),
            size = androidx.compose.ui.geometry.Size(selRight - selLeft, size.height)
        )

        if (n > 0) {
            val step = size.width / n
            for (i in 0 until n) {
                val amp = waveform[i].coerceIn(0f, 1f) * midY
                val x = i * step
                drawLine(
                    color = waveColor,
                    start = Offset(x, midY - amp),
                    end = Offset(x, midY + amp),
                    strokeWidth = step.coerceAtLeast(1f)
                )
            }
        }

        // Poignées de trim
        listOf(selLeft, selRight).forEach { x ->
            drawLine(
                color = waveColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 4f
            )
        }

        // Tête de lecture
        if (playHeadFraction > 0f) {
            val x = playHeadFraction * size.width
            drawLine(
                color = playHeadColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 3f
            )
        }
    }
}
