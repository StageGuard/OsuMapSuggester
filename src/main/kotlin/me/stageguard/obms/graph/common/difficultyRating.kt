package me.stageguard.obms.graph.common

import me.stageguard.obms.graph.drawTextLineWithShadow
import me.stageguard.obms.graph.lerpColor
import me.stageguard.obms.graph.semiBoldFont
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import kotlin.math.max

fun Canvas.drawDifficultyRatingCard(
    height: Float,
    star: Double, version: String, maxTextLength: Int,
    backgroundColor: Int, versionTextColor: Int, starTextColor: Int,
    paint: Paint, scale: Float = 1.0f
) {
    val versionText = TextLine.make(kotlin.run {
        if(version.length > maxTextLength) version.take(maxTextLength - 3).plus("...") else version
    }, Font(semiBoldFont, 22f * scale))
    val starText = TextLine.make("Star Difficulty: $star", Font(
        semiBoldFont, 18f * scale)
    )

    drawRRect(
        RRect.makeXYWH(0f, 0f, 60f * scale + max(versionText.width, starText.width) + 45f * scale, height, 90f),
        paint.apply {
            mode = PaintMode.FILL
            color = backgroundColor
        }
    )

    drawCircle((10f + 25f) * scale, (10f + 25f) * scale, 20f * scale, paint.apply {
        mode = PaintMode.FILL
        color = Color.makeRGB(255, 255, 255)
    })
    drawCircle((10f + 25f) * scale, (10f + 25f) * scale, 14f * scale, paint.apply {
        mode = PaintMode.STROKE
        color = difficultyColor(star)
        strokeWidth = 5f * scale
    })

    drawTextLineWithShadow(versionText, height, 17f * scale + versionText.capHeight, paint.apply {
        mode = PaintMode.FILL
        color = versionTextColor
    }, 1f * scale)

    drawTextLineWithShadow(starText, height,
        10f * scale + versionText.capHeight + starText.capHeight + 17f * scale
        , paint.apply {
            mode = PaintMode.FILL
            color = starTextColor
        }, 1f * scale)
}

private fun difficultyColor(value: Double) : Int {
    val mapping = listOf(
        1.5 to Color.makeRGB(79, 192, 255),
        2.0 to Color.makeRGB(79, 255, 213),
        2.5 to Color.makeRGB(124, 255, 79),
        3.25 to Color.makeRGB(246, 240, 92),
        4.5 to Color.makeRGB(255, 128, 104),
        6.0 to Color.makeRGB(255, 60, 113),
        7.0 to Color.makeRGB(101, 99, 222),
        8.0 to Color.makeRGB(24, 21, 142)
    )
    return when {
        value <= 1.5 -> Color.makeRGB(79, 192, 255)
        value >= 8.0 -> Color.makeRGB(0, 0, 0)
        else -> run {
            var color = Color.makeRGB(79, 192, 255)
            (0 until mapping.lastIndex).forEach {
                if(value in mapping[it].first..mapping[it + 1].first) {
                    color = lerpColor(
                        mapping[it].second, mapping[it + 1].second,
                        (value - mapping[it].first) / (mapping[it + 1].first - mapping[it].first)
                    )
                }
            }
            color
        }
    }
}
