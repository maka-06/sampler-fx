package com.example.sampleapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class DragMode { NONE, LEFT, RIGHT, CENTER }

/**
 * Affiche la forme d'onde, la zone de trim et la tête de lecture.
 *
 * Gestes de rognage :
 *  - toucher près de la poignée gauche -> déplace uniquement le début ;
 *  - toucher près de la poignée droite -> déplace uniquement la fin ;
 *  - toucher entre les deux -> déplace la sélection entière (écart conservé).
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
    handleColor: Color = Color(0xFFFFFFFF),
    playHeadColor: Color = Color(0xFFFFFFFF)
) {
    // Évite les captures périmées des valeurs dans la lambda de geste
    val curStart by rememberUpdatedState(trimStart)
    val curEnd by rememberUpdatedState(trimEnd)
    val onTrim by rememberUpdatedState(onTrimChange)
    var mode by remember { mutableStateOf(DragMode.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                val handlePx = 28.dp.toPx()
                val minGap = 0.01f
                detectDragGestures(
                    onDragStart = { pos ->
                        val l = curStart * size.width
                        val r = curEnd * size.width
                        mode = when {
                            abs(pos.x - l) <= handlePx -> DragMode.LEFT
                            abs(pos.x - r) <= handlePx -> DragMode.RIGHT
                            pos.x > l && pos.x < r -> DragMode.CENTER
                            else -> if (abs(pos.x - l) < abs(pos.x - r)) DragMode.LEFT else DragMode.RIGHT
                        }
                    },
                    onDragEnd = { mode = DragMode.NONE },
                    onDragCancel = { mode = DragMode.NONE },
                    onDrag = { change, drag ->
                        change.consume()
                        val df = drag.x / size.width
                        when (mode) {
                            DragMode.LEFT ->
                                onTrim((curStart + df).coerceIn(0f, curEnd - minGap), curEnd)
                            DragMode.RIGHT ->
                                onTrim(curStart, (curEnd + df).coerceIn(curStart + minGap, 1f))
                            DragMode.CENTER -> {
                                val w = curEnd - curStart
                                val ns = (curStart + df).coerceIn(0f, 1f - w)
                                onTrim(ns, ns + w)
                            }
                            DragMode.NONE -> {}
                        }
                    }
                )
            }
    ) {
        val midY = size.height / 2f
        val n = waveform.size
        val selLeft = curStart * size.width
        val selRight = curEnd * size.width

        // Zone sélectionnée (trim)
        drawRect(
            color = selectionColor,
            topLeft = Offset(selLeft, 0f),
            size = Size(selRight - selLeft, size.height)
        )

        // Forme d'onde
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

        // Poignées de trim avec capuchons (zone de préhension visible)
        val capH = 22f
        val capW = 14f
        listOf(selLeft, selRight).forEach { x ->
            drawLine(
                color = handleColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 5f
            )
            // Capuchon en haut
            drawRect(
                color = handleColor,
                topLeft = Offset(x - capW / 2f, 0f),
                size = Size(capW, capH)
            )
            // Capuchon en bas
            drawRect(
                color = handleColor,
                topLeft = Offset(x - capW / 2f, size.height - capH),
                size = Size(capW, capH)
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
