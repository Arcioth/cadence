package io.github.arcioth.cadence.ui

import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/** Hue / sat / brightness punch. sat 1 = identity; punch 1 is a hard grade. */
fun hsbMatrix(punch: Float): ColorMatrix {
    val p = punch.coerceIn(0f, 1f)
    val hue = Math.toRadians((p * 50f).toDouble())
    val sat = 1f + p * 1.85f
    val br = p * 0.42f
    val lumR = 0.213f
    val lumG = 0.715f
    val lumB = 0.072f
    val s = sat
    val sr = lumR * (1 - s)
    val sg = lumG * (1 - s)
    val sb = lumB * (1 - s)
    val satM = ColorMatrix(
        floatArrayOf(
            sr + s, sg, sb, 0f, 0f,
            sr, sg + s, sb, 0f, 0f,
            sr, sg, sb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val c = cos(hue).toFloat()
    val si = sin(hue).toFloat()
    val hueM = ColorMatrix(
        floatArrayOf(
            lumR + c * (1 - lumR) + si * -lumR, lumG + c * -lumG + si * -lumG, lumB + c * -lumB + si * (1 - lumB), 0f, 0f,
            lumR + c * -lumR + si * 0.143f, lumG + c * (1 - lumG) + si * 0.140f, lumB + c * -lumB + si * -0.283f, 0f, 0f,
            lumR + c * -lumR + si * -(1 - lumR), lumG + c * -lumG + si * lumG, lumB + c * (1 - lumB) + si * lumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val add = br * 255f
    val brM = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, add,
            0f, 1f, 0f, 0f, add,
            0f, 0f, 1f, 0f, add,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    hueM.timesAssign(satM)
    hueM.timesAssign(brM)
    return hueM
}
