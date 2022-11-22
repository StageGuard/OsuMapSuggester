package me.stageguard.obms.graph.common

import me.stageguard.obms.graph.drawTextLineWithShadow
import me.stageguard.obms.graph.format1DFix
import me.stageguard.obms.graph.lerpColor
import me.stageguard.obms.graph.semiBoldFont
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

fun Canvas.drawPPPlusGraph(
    graphCardWidth: Float, graphCardHeight: Float,
    ju: Double, fl: Double, sp: Double, st: Double, pr: Double, co: Double,
    backgroundColor: Int, textColor: Int, graphColorMax: Int, graphColorMin: Int,
    paint: Paint, scale: Float = 1.0f
) {
    drawRRect(RRect.makeXYWH(0f, 0f, graphCardWidth, graphCardHeight, 16f * scale), paint.apply {
        color = backgroundColor
        mode = PaintMode.FILL
    })

    val ppPlusGraphText = TextLine.make("Strain skill of the beatmap", Font(semiBoldFont, 18f * scale))
    drawTextLineWithShadow(ppPlusGraphText,
        (graphCardWidth - ppPlusGraphText.width) / 2, graphCardHeight - 15f * scale,
        paint.setColor(textColor)
    )
    val graphCenterY = (graphCardHeight - 20f * scale - ppPlusGraphText.capHeight) / 2
    val radius = 70f * scale

    val totalSkill = ju + fl + sp + st + pr + co
    val skills = listOf(
        "Jump" to ju, "Flow" to fl, "Speed" to sp,
        "Stamina" to st, "Precision" to pr, "Complexity" to co
    )

    var lastAngle = -90f

    drawCircle(graphCardWidth / 2, graphCenterY, radius, paint.setColor(Color.makeRGB(255, 255, 255)))

    skills.forEachIndexed { idx, it ->
        val scaledRadius = (radius * (1 + 0.5 * (1.0 * it.second / totalSkill))).toFloat()
        drawArc(
            graphCardWidth / 2 - scaledRadius, graphCenterY - scaledRadius,
            graphCardWidth / 2 + scaledRadius, graphCenterY + scaledRadius,
            lastAngle, 360f * (it.second / totalSkill).toFloat() + 1f,
            true, paint.apply {
                color = lerpColor(graphColorMax, graphColorMin, 1.0 * idx / skills.size)
            }
        )

        lastAngle += 360f * (it.second / totalSkill).toFloat()

        save()
        translate(graphCardWidth / 2, graphCenterY)

        val skillName = TextLine.make(it.first, Font(semiBoldFont, 18f * scale))
        val skillValue = TextLine.make(format1DFix.format(it.second), Font(semiBoldFont, 18f * scale))
        val skillWidth = max(skillName.width, skillValue.width)
        val skillHeight = skillName.capHeight + 5f + skillValue.capHeight

        val relativeToCoord = 90 - (lastAngle - 360f * (it.second / totalSkill).toFloat() / 2)

        drawTextLineWithShadow(skillName,
            (sin(relativeToCoord / 180 * PI) * radius * 1.4 - skillWidth / 2).toFloat() + (skillWidth - skillName.width) / 2,
            (cos(relativeToCoord / 180 * PI) * radius * 1.4 - skillHeight / 2).toFloat() + skillName.capHeight,
            paint.setColor(textColor), 1f
        )
        drawTextLineWithShadow(skillValue,
            (sin(relativeToCoord / 180 * PI) * radius * 1.4 - skillWidth / 2).toFloat() + (skillWidth - skillValue.width) / 2,
            (cos(relativeToCoord / 180 * PI) * radius * 1.4 - skillHeight / 2).toFloat() + skillName.capHeight + 5f + skillValue.capHeight,
            paint.setColor(textColor), 1f
        )

        restore()
    }
}
