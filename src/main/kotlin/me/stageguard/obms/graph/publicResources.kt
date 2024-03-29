package me.stageguard.obms.graph

import me.stageguard.obms.utils.bmf.BitmapFont
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

// Fonts
val regularFont = typeface("Regular")
val semiBoldFont = typeface("SemiBold")
val boldFont = typeface("Bold")

val format2DFix = DecimalFormat("#######0.00")
val format1DFix = DecimalFormat("#######0.0")

val usNumber: NumberFormat = NumberFormat.getNumberInstance(Locale.US)

val osuFont by lazy { BitmapFont.readFromFile(resourcePath("font/osuFont.fnt")) }