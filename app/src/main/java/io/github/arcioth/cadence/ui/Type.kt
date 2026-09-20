package io.github.arcioth.cadence.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.arcioth.cadence.R

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.montserrat_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

val Phosphor = FontFamily(Font(R.font.phosphor))

val CadenceTypography = Typography(
    bodyLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    labelLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 14.sp),
)

object Ph {
    const val Search = "\uE30C"
    const val Squares = "\uE464"
    const val List = "\uE2F0"
    const val BatEmpty = "\uE0BE"
    const val BatLow = "\uE0C4"
    const val BatMed = "\uE0C6"
    const val BatHigh = "\uE0C2"
    const val BatFull = "\uE0C0"
    const val BatCharge = "\uE0BA"
    const val Play = "\uE3D0"
    const val Pause = "\uE39E"
    const val SkipBack = "\uE5A4"
    const val SkipFwd = "\uE5A6"
    const val Image = "\uE2CA"
    const val Images = "\uE836"
    const val CaretLeft = "\uE138"
    const val CornersOut = "\uE1D0"
    const val CornersIn = "\uE1CE"
}

fun batteryGlyph(pct: Int, charging: Boolean): String = when {
    charging -> Ph.BatCharge
    pct >= 90 -> Ph.BatFull
    pct >= 65 -> Ph.BatHigh
    pct >= 40 -> Ph.BatMed
    pct >= 15 -> Ph.BatLow
    else -> Ph.BatEmpty
}
