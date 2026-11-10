package com.natanael.ntplay.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícones de traço fino desenhados à mão (no lugar dos emojis). Grade de 24 unidades. */
enum class Ic(val prefix: String) {
    SEARCH("🔍"), CALENDAR("📅"), MENU("☰"), TRASH("🗑"), KEY("🔑"), REFRESH("↻"), PLAY("▶"),
    PLUS("＋"), PLUS2("+"), CLOSE("✕"), CLOCK("⏳"), INFO("ⓘ"), STAR_ON("★"), STAR_OFF("☆"), BACK("‹")
}

@Composable
fun NtIcon(ic: Ic, color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun ln(a: Float, b: Float, c: Float, d: Float) = drawLine(color, o(a, b), o(c, d), 2f * u, StrokeCap.Round)
        when (ic) {
            Ic.SEARCH -> { drawCircle(color, 6.5f * u, o(10.5f, 10.5f), style = st); ln(15.5f, 15.5f, 20.5f, 20.5f) }
            Ic.CALENDAR -> { drawRoundRect(color, o(4f, 5f), Size(16f * u, 15f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = st); ln(4f, 10f, 20f, 10f); ln(8f, 3f, 8f, 7f); ln(16f, 3f, 16f, 7f) }
            Ic.MENU -> { ln(4f, 7f, 20f, 7f); ln(4f, 12f, 20f, 12f); ln(4f, 17f, 20f, 17f) }
            Ic.TRASH -> {
                ln(4f, 7f, 20f, 7f)
                val p = Path().apply { moveTo(6.5f * u, 7f * u); lineTo(7.5f * u, 20f * u); lineTo(16.5f * u, 20f * u); lineTo(17.5f * u, 7f * u) }
                drawPath(p, color, style = st)
                val h = Path().apply { moveTo(9.5f * u, 7f * u); lineTo(9.5f * u, 4f * u); lineTo(14.5f * u, 4f * u); lineTo(14.5f * u, 7f * u) }
                drawPath(h, color, style = st)
            }
            Ic.KEY -> { drawCircle(color, 4f * u, o(8f, 15f), style = st); ln(11f, 12f, 20f, 4f); ln(16.5f, 7.5f, 19f, 10f) }
            Ic.REFRESH -> {
                drawArc(color, -50f, 290f, false, o(5f, 5f), Size(14f * u, 14f * u), style = st)
                val p = Path().apply { moveTo(15f * u, 2.5f * u); lineTo(18.5f * u, 5.2f * u); lineTo(14.5f * u, 8f * u) }
                drawPath(p, color, style = st)
            }
            Ic.PLAY -> { val p = Path().apply { moveTo(7f * u, 4.5f * u); lineTo(19.5f * u, 12f * u); lineTo(7f * u, 19.5f * u); close() }; drawPath(p, color, style = Fill) }
            Ic.PLUS, Ic.PLUS2 -> { ln(12f, 5f, 12f, 19f); ln(5f, 12f, 19f, 12f) }
            Ic.CLOSE -> { ln(6f, 6f, 18f, 18f); ln(18f, 6f, 6f, 18f) }
            Ic.CLOCK -> { drawCircle(color, 8.5f * u, o(12f, 12f), style = st); val p = Path().apply { moveTo(12f * u, 7f * u); lineTo(12f * u, 12f * u); lineTo(15.5f * u, 14f * u) }; drawPath(p, color, style = st) }
            Ic.INFO -> { drawCircle(color, 8.5f * u, o(12f, 12f), style = st); ln(12f, 11f, 12f, 16.5f); drawCircle(color, 1.1f * u, o(12f, 7.8f)) }
            Ic.STAR_ON, Ic.STAR_OFF -> star(this, u, color, ic == Ic.STAR_ON, st)
            Ic.BACK -> { val p = Path().apply { moveTo(15f * u, 5f * u); lineTo(8f * u, 12f * u); lineTo(15f * u, 19f * u) }; drawPath(p, color, style = st) }
        }
    }
}

private fun star(d: DrawScope, u: Float, color: Color, filled: Boolean, st: Stroke) {
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 9.5f else 4.2f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = (12f + r * Math.cos(a).toFloat()) * u; val y = (12.6f + r * Math.sin(a).toFloat()) * u
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    if (filled) d.drawPath(p, color, style = Fill) else d.drawPath(p, color, style = st)
}

/** interruptor no lugar de ☑ / ☐ */
@Composable
fun NtSwitch(on: Boolean, fg: Color) {
    val track = if (on) Color(0xFF5B8CFF) else Color(0x55FFFFFF)
    Box(Modifier.size(width = 28.dp, height = 16.dp).background(track, RoundedCornerShape(50)).padding(2.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(12.dp).background(Color.White, RoundedCornerShape(50)))
    }
}
