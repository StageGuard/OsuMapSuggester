package me.stageguard.obms.graph.common

import me.stageguard.obms.graph.boldFont
import me.stageguard.obms.graph.drawTextLineWithShadow
import me.stageguard.obms.graph.semiBoldFont
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect

fun Canvas.drawPpCurveGraph(
    graphCardWidth: Float, graphCardHeight: Float,
    actualPpCurvePoints: MutableList<Pair<Double, Double>>, ifFCPpCurvePoints: MutableList<Pair<Double, Double>>,
    actualPp: Double, actualAccuracy: Double,
    backgroundColor: Int, textColor: Int, gridLineColor: Int, scaleTextColor: Int,
    actualPpCurveLineColor: Int, ifFcPpCurveLineColor: Int,
    actualPpDotColor: Int, ifFcPpDotColor: Int,
    paint: Paint
) {
    drawRRect(RRect.makeXYWH(0f, 0f, graphCardWidth, graphCardHeight, 16f), paint.apply {
        color = backgroundColor
        mode = PaintMode.FILL
    })

    val lineRow = 8
    val ppCurveText = TextLine.make("PP curve", Font(semiBoldFont, 18f))
    drawTextLineWithShadow(ppCurveText,
        (graphCardWidth - ppCurveText.width) / 2, graphCardHeight - 15f,
        paint.setColor(textColor)
    )

    val maxValue = ifFCPpCurvePoints.last().second
    val minValue = actualPpCurvePoints[2].second
    val interval = ((maxValue - minValue) / lineRow).run { this + (10 - this % 10) }.toInt()
    val startValue = (minValue - minValue % interval).toInt()
    val intervalValues = (startValue..startValue + interval * lineRow step interval).toList()
    val valueTexts = intervalValues.map {
        TextLine.make(it.toString(), Font(semiBoldFont, 14f))
    }
    val maxValueTextWidth = valueTexts.maxOf { it.width }
    val accuracyValues = (0..10).map {
        TextLine.make("${90 + it}", Font(semiBoldFont, 14f))
    }
    val maxAccuracyTextHeight = accuracyValues.maxOf { it.capHeight }

    translate(10f, graphCardHeight - 2 * 15f - ppCurveText.capHeight)
    val scaledChartHeight = graphCardHeight - 10f - 2 * 15f - ppCurveText.capHeight - 5f - 10f //the last 10f is for overdrew row and column
    val scaledChartWidth = graphCardWidth - 20f - maxValueTextWidth - 5f - 10f //the last 10f is for overdrew row and column
    valueTexts.forEachIndexed { idx, it ->
        drawLine(
            5f + maxValueTextWidth + 5f, -5f - maxAccuracyTextHeight -10f - scaledChartHeight / (lineRow + 1) * idx,
            5f + maxValueTextWidth + 5f + scaledChartWidth, -5f - maxAccuracyTextHeight -10f - scaledChartHeight / (lineRow + 1) * idx,
            paint.apply {
                color = gridLineColor
                strokeWidth = 2f
            }
        )
        drawTextLine(it,
            maxValueTextWidth - it.width,
            -5f - maxAccuracyTextHeight -10f - scaledChartHeight / (lineRow + 1) * idx + it.capHeight / 2,
            paint.setColor(scaleTextColor)
        )
    }
    accuracyValues.forEachIndexed { idx, it ->
        drawLine(
            5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx, -maxAccuracyTextHeight - 5f,
            5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx, -maxAccuracyTextHeight - 5f - scaledChartHeight + 5f,
            paint.apply {
                color = gridLineColor
                strokeWidth = 2f
            }
        )
        drawTextLine(it,
            5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx - it.width / 2,
            0f, paint.setColor(scaleTextColor)
        )
    }
    translate(maxValueTextWidth + 20f, -maxAccuracyTextHeight - 15f)
    val actualChatWidth = scaledChartWidth - 22f
    val actualCharHeight = scaledChartHeight - 23f
    (1 until ifFCPpCurvePoints.size - 1).forEach {
        //the first element of ppCurePoints.first is the pp of current score
        drawLine(
            ((actualPpCurvePoints[it].first - 90) / 10 * actualChatWidth).toFloat(),
            (-((actualPpCurvePoints[it].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
            ((actualPpCurvePoints[it + 1].first - 90) / 10 * actualChatWidth).toFloat(),
            (-((actualPpCurvePoints[it + 1].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
            paint.apply {
                color = actualPpCurveLineColor
                strokeWidth = 3f
            }
        )
        //the first element of ppCurePoints.second is the pp of current score if full combo
        drawLine(
            ((ifFCPpCurvePoints[it].first - 90) / 10 * actualChatWidth).toFloat(),
            (-((ifFCPpCurvePoints[it].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
            ((ifFCPpCurvePoints[it + 1].first - 90) / 10 * actualChatWidth).toFloat(),
            (-((ifFCPpCurvePoints[it + 1].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
            paint.apply {
                color = ifFcPpCurveLineColor
                strokeWidth = 3f
            }
        )
    }

    val ifFullComboText = TextLine.make("if fc", Font(boldFont, 18f))
    drawTextLineWithShadow(ifFullComboText,
        ((ifFCPpCurvePoints.last().first - 90) / 10 * actualChatWidth).toFloat() + 8f,
        (-((ifFCPpCurvePoints.last().second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat() + ifFullComboText.capHeight / 2,
        paint.apply {
            color = ifFcPpDotColor
        }, 2f
    )

    val ifFullComboPp = ifFCPpCurvePoints.first().second
    val ppText = TextLine.make("pp", Font(semiBoldFont, 18f))
    val actualPpValueText = TextLine.make(actualPp.toInt().toString(), Font(boldFont, 22f))
    val ifFullComboPpValueText = TextLine.make(ifFullComboPp.toInt().toString(), Font(boldFont, 22f))

    if(actualAccuracy * 100.0 < 90.0) {
        //actual pp text
        drawPoint(-5f, 0f, paint.apply {
            color = actualPpDotColor
            strokeWidth = 6f
        })
        drawTextLineWithShadow(actualPpValueText, -5f + 5f, actualPpValueText.capHeight / 2, paint.apply {
            color = Color.makeRGB(255, 102, 171)
            strokeWidth = 2f
        }, 2f)
        drawTextLineWithShadow(ppText, -5f + 5f + actualPpValueText.width, actualPpValueText.capHeight / 2, paint.apply {
            color = Color.makeRGB(209, 148, 175)
            strokeWidth = 2f
        }, 2f)
        //if full combo pp text
        if(ifFullComboPp - actualPp > 2.0) {
            val yCoord = ((ifFullComboPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
            drawPoint(-5f, -yCoord, paint.apply {
                color = actualPpDotColor
                strokeWidth = 6f
            })
            drawTextLineWithShadow(ifFullComboPpValueText, -5f + 5f, -5f - yCoord, paint.apply {
                color = Color.makeRGB(255, 102, 171)
                strokeWidth = 2f
            }, 2f)
            drawTextLineWithShadow(ppText, -5f + 5f + ifFullComboPpValueText.width, -5f - yCoord, paint.apply {
                color = Color.makeRGB(209, 148, 175)
                strokeWidth = 2f
            }, 2f)
        }
    } else {
        //actual pp text
        val xCoord = ((actualAccuracy * 100.0 - 90) / 10 * actualChatWidth).toFloat()
        val yCoordActualPp = ((actualPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
        val textOffset = if(yCoordActualPp > ppText.capHeight) ppText.capHeight + 10f else 0f
        // actual pp
        drawPoint(xCoord, -yCoordActualPp, paint.apply {
            color = actualPpDotColor
            strokeWidth = 6f
        })
        drawTextLineWithShadow(actualPpValueText, 5f + xCoord, -5f - yCoordActualPp + textOffset, paint.apply {
            color = Color.makeRGB(255, 102, 171)
            strokeWidth = 2f
        }, 2f)
        drawTextLineWithShadow(ppText, 5f + xCoord + actualPpValueText.width, -5f - yCoordActualPp + textOffset, paint.apply {
            color = Color.makeRGB(209, 148, 175)
            strokeWidth = 2f
        }, 2f)
        //if full combo pp text
        if(ifFullComboPp - actualPp > 2.0) {
            val yCoordIfFullComboPp = ((ifFullComboPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
            drawPoint(xCoord, -yCoordIfFullComboPp, paint.apply {
                color = ifFcPpDotColor
                strokeWidth = 6f
            })
            drawTextLineWithShadow(ifFullComboPpValueText, -5f + xCoord - ifFullComboPpValueText.width - ppText.width, -5f - yCoordIfFullComboPp, paint.apply {
                color = Color.makeRGB(255, 102, 171)
                strokeWidth = 2f
            }, 2f)
            drawTextLineWithShadow(ppText, -5f + xCoord - ppText.width, -5f - yCoordIfFullComboPp, paint.apply {
                color = Color.makeRGB(209, 148, 175)
                strokeWidth = 2f
            }, 2f)
        }
    }
}
