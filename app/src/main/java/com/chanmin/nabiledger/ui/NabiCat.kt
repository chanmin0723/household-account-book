package com.chanmin.nabiledger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.chanmin.nabiledger.logic.Mood

/**
 * 이미지 파일 없이 Canvas로 그리는 삼색 고양이 나비.
 * 100x100 가상 좌표계 위에 그린 뒤 실제 크기로 스케일.
 */
@Composable
fun NabiCat(mood: Mood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "nabi")
    val tailAngle by t.animateFloat(
        initialValue = -14f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == Mood.HAPPY) 700 else 1500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "tail"
    )
    val bob by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val shake by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(80, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake"
    )

    Canvas(modifier) {
        val u = minOf(size.width, size.height) / 100f
        val jitter = if (mood == Mood.SHOCKED) shake * 1.2f * u else 0f
        val ox = size.width / 2f - 50f * u + jitter
        val oy = (size.height - 100f * u) / 2f - bob * 1.6f * u
        fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
        fun sz(w: Float, h: Float) = Size(w * u, h * u)

        val line = Nabi.Brown
        val outline = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thin = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // ---- tail (뒤) ----
        rotate(tailAngle, pivot = p(72f, 84f)) {
            val tail = Path().apply {
                moveTo(p(72f, 84f).x, p(72f, 84f).y)
                quadraticTo(p(94f, 82f).x, p(94f, 82f).y, p(90f, 60f).x, p(90f, 60f).y)
            }
            drawPath(tail, line, style = Stroke(width = 9.5f * u, cap = StrokeCap.Round))
            drawPath(tail, Nabi.Fur, style = Stroke(width = 5.5f * u, cap = StrokeCap.Round))
            drawCircle(Nabi.Orange, radius = 2.6f * u, center = p(90f, 60f))
        }

        // ---- body ----
        val bodyTopLeft = p(24f, 58f)
        val bodySize = sz(52f, 36f)
        drawOval(Nabi.Fur, topLeft = bodyTopLeft, size = bodySize)
        val bodyPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(bodyTopLeft, bodySize)) }
        clipPath(bodyPath) {
            drawCircle(Nabi.Orange, radius = 12f * u, center = p(66f, 66f))
            drawCircle(Nabi.Brown, radius = 7f * u, center = p(30f, 80f))
        }
        drawOval(line, topLeft = bodyTopLeft, size = bodySize, style = outline)
        // paws
        for (x in listOf(34f, 54f)) {
            drawOval(Nabi.Fur, topLeft = p(x, 86f), size = sz(12f, 8f))
            drawOval(line, topLeft = p(x, 86f), size = sz(12f, 8f), style = thin)
        }

        // ---- head (궁금할 땐 갸웃) ----
        val tilt = when (mood) {
            Mood.CURIOUS -> -9f
            Mood.WORRIED -> 4f
            else -> 0f
        }
        rotate(tilt, pivot = p(50f, 58f)) {
            // ears
            val leftEar = Path().apply {
                moveTo(p(29f, 36f).x, p(29f, 36f).y)
                lineTo(p(30f, 12f).x, p(30f, 12f).y)
                lineTo(p(47f, 24f).x, p(47f, 24f).y)
                close()
            }
            val rightEar = Path().apply {
                moveTo(p(71f, 36f).x, p(71f, 36f).y)
                lineTo(p(70f, 12f).x, p(70f, 12f).y)
                lineTo(p(53f, 24f).x, p(53f, 24f).y)
                close()
            }
            val innerEar = Path().apply {
                moveTo(p(33f, 31f).x, p(33f, 31f).y)
                lineTo(p(33.5f, 18f).x, p(33.5f, 18f).y)
                lineTo(p(43f, 25f).x, p(43f, 25f).y)
                close()
            }
            drawPath(leftEar, Nabi.Fur)
            drawPath(leftEar, line, style = outline)
            drawPath(innerEar, Nabi.Pink)
            drawPath(rightEar, Nabi.Brown)
            drawPath(rightEar, line, style = outline)

            // head + calico patches
            val headC = p(50f, 44f)
            val headR = 24f * u
            drawCircle(Nabi.Fur, radius = headR, center = headC)
            val headPath = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(headC, headR))
            }
            clipPath(headPath) {
                drawCircle(Nabi.Orange, radius = 14f * u, center = p(34f, 33f))
                drawCircle(Nabi.Brown, radius = 10f * u, center = p(69f, 28f))
            }
            drawCircle(line, radius = headR, center = headC, style = outline)

            // blush
            val blushAlpha = if (mood == Mood.HAPPY) 0.85f else 0.5f
            drawOval(Nabi.Pink.copy(alpha = blushAlpha), topLeft = p(31f, 51f), size = sz(8f, 5f))
            drawOval(Nabi.Pink.copy(alpha = blushAlpha), topLeft = p(61f, 51f), size = sz(8f, 5f))

            // eyes
            val eyeL = p(41f, 46f)
            val eyeR = p(59f, 46f)
            when (mood) {
                Mood.HAPPY -> {
                    for (e in listOf(eyeL, eyeR)) {
                        drawArc(
                            line, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                            topLeft = Offset(e.x - 4f * u, e.y - 2f * u), size = sz(8f, 7f), style = outline
                        )
                    }
                }
                Mood.SHOCKED -> {
                    for (e in listOf(eyeL, eyeR)) {
                        drawCircle(androidx.compose.ui.graphics.Color.White, radius = 5f * u, center = e)
                        drawCircle(line, radius = 5f * u, center = e, style = thin)
                        drawCircle(Nabi.Ink, radius = 1.8f * u, center = e)
                    }
                }
                else -> {
                    for (e in listOf(eyeL, eyeR)) {
                        drawCircle(Nabi.Ink, radius = 3.2f * u, center = e)
                        drawCircle(
                            androidx.compose.ui.graphics.Color.White, radius = 1.1f * u,
                            center = Offset(e.x + 1f * u, e.y - 1.1f * u)
                        )
                    }
                }
            }
            if (mood == Mood.WORRIED) {
                drawLine(line, p(35f, 39f), p(44f, 37f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                drawLine(line, p(65f, 39f), p(56f, 37f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
            }

            // nose
            val nose = Path().apply {
                moveTo(p(48f, 51.5f).x, p(48f, 51.5f).y)
                lineTo(p(52f, 51.5f).x, p(52f, 51.5f).y)
                lineTo(p(50f, 54f).x, p(50f, 54f).y)
                close()
            }
            drawPath(nose, Nabi.PinkDeep)

            // mouth
            when (mood) {
                Mood.SHOCKED -> drawOval(Nabi.Ink, topLeft = p(47.5f, 56f), size = sz(5f, 6.5f))
                Mood.WORRIED -> drawLine(line, p(47f, 58f), p(53f, 57f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                else -> {
                    drawArc(line, 0f, 180f, false, topLeft = p(46f, 52.5f), size = sz(4f, 4f), style = thin)
                    drawArc(line, 0f, 180f, false, topLeft = p(50f, 52.5f), size = sz(4f, 4f), style = thin)
                }
            }

            // whiskers
            val wc = Nabi.BrownSoft.copy(alpha = 0.7f)
            drawLine(wc, p(30f, 50f), p(18f, 48f), strokeWidth = 1f * u)
            drawLine(wc, p(30f, 53f), p(18f, 55f), strokeWidth = 1f * u)
            drawLine(wc, p(70f, 50f), p(82f, 48f), strokeWidth = 1f * u)
            drawLine(wc, p(70f, 53f), p(82f, 55f), strokeWidth = 1f * u)
        }

        // ---- 감정 표시 ----
        when (mood) {
            Mood.WORRIED -> {
                val drop = Path().apply {
                    moveTo(p(78f, 26f).x, p(78f, 26f).y)
                    quadraticTo(p(82f, 33f).x, p(82f, 33f).y, p(78f, 35f).x, p(78f, 35f).y)
                    quadraticTo(p(74f, 33f).x, p(74f, 33f).y, p(78f, 26f).x, p(78f, 26f).y)
                    close()
                }
                drawPath(drop, Nabi.Sweat)
            }
            Mood.SHOCKED -> {
                drawLine(Nabi.Red, p(82f, 10f), p(82f, 22f), strokeWidth = 3f * u, cap = StrokeCap.Round)
                drawCircle(Nabi.Red, radius = 1.6f * u, center = p(82f, 27f))
                drawLine(Nabi.Red, p(89f, 14f), p(89f, 24f), strokeWidth = 3f * u, cap = StrokeCap.Round)
                drawCircle(Nabi.Red, radius = 1.6f * u, center = p(89f, 29f))
            }
            Mood.HAPPY -> {
                drawCircle(Nabi.Orange, radius = 2f * u, center = p(16f, 22f))
                drawCircle(Nabi.Pink, radius = 1.6f * u, center = p(84f, 16f))
                drawCircle(Nabi.Mint, radius = 1.4f * u, center = p(88f, 30f))
            }
            Mood.CURIOUS -> {
                drawArc(
                    Nabi.BrownSoft, startAngle = 180f, sweepAngle = 270f, useCenter = false,
                    topLeft = p(78f, 8f), size = sz(10f, 10f), style = thin
                )
                drawLine(Nabi.BrownSoft, p(83f, 18f), p(83f, 21f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                drawCircle(Nabi.BrownSoft, radius = 1f * u, center = p(83f, 25f))
            }
            Mood.CALM -> Unit
        }
    }
}
