package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

@Composable
fun MusiclyLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFB4D3FE)), // Light Blue background circle as shown in the prompt
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Draw the green curved hills segment in the bottom-left corner
            val greenPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h * 0.48f)
                quadraticTo(w * 0.32f, h * 0.65f, w * 0.5f, h)
                close()
            }

            drawPath(
                path = greenPath,
                color = Color(0xFF539C5E) // Forest Green hill segment
            )

            // 2. Draw vertical graphic waveform bars overlay inside the green segment
            clipPath(greenPath) {
                val numBars = 10
                val startX = w * 0.03f
                val endX = w * 0.46f
                val stepX = (endX - startX) / (numBars - 1)
                for (i in 0 until numBars) {
                    val x = startX + i * stepX
                    // Make wave height peak near center and drop towards edges
                    val distFraction = kotlin.math.abs(i - numBars / 2f) / (numBars / 2f)
                    val barHt = h * 0.18f * (1.1f - distFraction * 0.8f)
                    val centerY = h * 0.83f - (i * 0.8f) // dynamic aesthetic shift
                    
                    drawLine(
                        color = Color(0xFF1F4125).copy(alpha = 0.55f),
                        start = Offset(x, centerY - barHt / 2),
                        end = Offset(x, centerY + barHt / 2),
                        strokeWidth = (w * 0.015f).coerceAtLeast(1.5f)
                    )
                }
            }

            // 3. Draw the dark slate-blue music note
            val noteColor = Color(0xFF293B4E) // Dark blue-gray note color

            // Draw note head
            val headRad = w * 0.12f
            val headX = w * 0.41f
            val headY = h * 0.68f

            drawCircle(
                color = noteColor,
                radius = headRad,
                center = Offset(headX, headY)
            )

            // Draw note stem (vertical line rising from the right-hand edge of the head)
            val stemWidth = w * 0.055f
            val stemLeft = headX + headRad - stemWidth / 2
            val stemYTop = h * 0.22f
            val stemHeight = headY - stemYTop

            drawRect(
                color = noteColor,
                topLeft = Offset(stemLeft, stemYTop),
                size = Size(stemWidth, stemHeight)
            )

            // Draw note flag (curved hook hanging from the top)
            val flagPath = Path().apply {
                moveTo(stemLeft, stemYTop)
                quadraticTo(stemLeft + w * 0.16f, stemYTop + h * 0.03f, stemLeft + w * 0.24f, stemYTop + h * 0.25f)
                quadraticTo(stemLeft + w * 0.14f, stemYTop + h * 0.16f, stemLeft + stemWidth, stemYTop + h * 0.13f)
                lineTo(stemLeft + stemWidth, stemYTop)
                close()
            }
            drawPath(
                path = flagPath,
                color = noteColor
            )
        }
    }
}
