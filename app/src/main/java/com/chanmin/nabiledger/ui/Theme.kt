package com.chanmin.nabiledger.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 나비(삼색 고양이) 파스텔 팔레트 */
object Nabi {
    val Cream = Color(0xFFFFF8EE)
    val CreamDeep = Color(0xFFFCEBD3)
    val Peach = Color(0xFFFFF1DE)
    val Orange = Color(0xFFF2A65A)
    val OrangeDeep = Color(0xFFD9813A)
    val Brown = Color(0xFF5B4636)
    val BrownSoft = Color(0xFF8A7262)
    val Ink = Color(0xFF3E2F25)
    val Pink = Color(0xFFF7B7C3)
    val PinkDeep = Color(0xFFE58FA0)
    val Fur = Color(0xFFFFFCF7)
    val Mint = Color(0xFF8CCFB0)
    val MintDeep = Color(0xFF3F9A73)
    val MintBg = Color(0xFFE9F6EF)
    val Warn = Color(0xFFFFE6CC)
    val Alarm = Color(0xFFFDE0E0)
    val Red = Color(0xFFE07070)
    val Sweat = Color(0xFF9FD3F2)
}

private val scheme = lightColorScheme(
    primary = Nabi.OrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Nabi.CreamDeep,
    onPrimaryContainer = Nabi.Brown,
    secondary = Nabi.PinkDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE2E7),
    onSecondaryContainer = Nabi.Brown,
    tertiary = Nabi.MintDeep,
    background = Nabi.Cream,
    onBackground = Nabi.Ink,
    surface = Nabi.Cream,
    onSurface = Nabi.Ink,
    surfaceVariant = Nabi.CreamDeep,
    onSurfaceVariant = Nabi.BrownSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF5),
    surfaceContainer = Nabi.Peach,
    surfaceContainerHigh = Color(0xFFFFF6EA),
    surfaceContainerHighest = Nabi.CreamDeep,
    outline = Color(0xFFE3CDB4),
    outlineVariant = Color(0xFFF0E0CC),
    error = Nabi.Red,
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun NabiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
