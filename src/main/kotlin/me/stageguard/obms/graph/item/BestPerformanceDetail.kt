package me.stageguard.obms.graph.item

import me.stageguard.obms.api.osu.dto.ScoreDTO
import me.stageguard.obms.graph.*
import me.stageguard.obms.utils.image
import me.stageguard.obms.utils.svgDom
import me.stageguard.obms.utils.toScaledImage
import org.jetbrains.skija.*
import java.text.DecimalFormat
import kotlin.math.round

const val cardWidth = 1100f
const val cardHeight = 60f
const val backgroundPadding = 60f
const val intervalBetweenCards = 10f
val format = DecimalFormat("#######0.00")

fun drawBestPerformancesImage(
    scores: List<ScoreDTO>
) : Surface {
    val surfaceWidth = backgroundPadding * 2 + cardWidth
    val surfaceHeight = backgroundPadding * 2 + intervalBetweenCards * scores.size - 1 + scores.size * cardHeight

    val surface = Surface.makeRasterN32Premul(surfaceWidth.toInt(), surfaceHeight.toInt())

    surface.canvas.apply {
        clear(backgroundBaseColor)
        val backgroundImage = image("image/background.png")
        drawImage(backgroundImage, 0f, surfaceHeight - backgroundImage.height.toFloat())

        var yOffset = backgroundPadding

        scores.forEach {
            val card = drawSingleCard(it)
            drawImage(card, backgroundPadding, yOffset)
            yOffset += (card.height + intervalBetweenCards)
        }
    }
    return surface
}

fun drawSingleCard(score: ScoreDTO) : Image {
    val surface = Surface.makeRasterN32Premul(
        cardWidth.toInt(),
        cardHeight.toInt()
    )

    val paint = Paint().apply {
        isAntiAlias = true
        filterQuality = FilterQuality.HIGH
    }

    surface.canvas.apply {
        drawRRect(RRect.makeXYWH(0f, 0f, cardWidth, cardHeight, 15f), paint.apply {
            color = backgroundColor
            mode = PaintMode.FILL
        })
        //rank icon
        val rankSvgImage = svgDom("svg/grade_${score.rank.lowercase()}.svg").run {
            toScaledImage(((cardHeight - root!!.height.value) / cardHeight) / 0.42f)
        }.also {
            drawImage(it, it.width / 2f, ((cardHeight - it.height) / 2f))
        }
        //beatmap info
        val songText = TextLine.make("${score.beatmapset!!.title} ", Font(semiBoldFont, 18f))
        val authorText = TextLine.make("by ${score.beatmapset.artist}", Font(semiBoldFont, 16f))
        val variant = TextLine.make(score.beatmap!!.version, Font(regularFont, 16f))
        drawTextLine(songText,
            rankSvgImage.width * 2f,
            (cardHeight - songText.height / 2) / 2f + 2f,
            paint.apply {
                color = Color.makeRGB(255, 255, 255)
            })
        drawTextLine(authorText,
            rankSvgImage.width * 2f + songText.width,
            (cardHeight - authorText.height / 2) / 2f + 2f,
            paint.apply {
                color = timeTextColor
            })
        drawTextLine(variant,
            rankSvgImage.width * 2f,
            (cardHeight + variant.height * 2) / 2f - 2f,
            paint.apply {
                color = mapVersionTextColor
            })
        val timePlayed = TextLine.make(score.createdAt, Font(regularFont, 16f))
        drawTextLine(timePlayed,
            rankSvgImage.width * 2f + variant.width + 20f,
            (cardHeight + variant.height * 2) / 2f - 2f,
            paint.apply {
                color = timeTextColor
            })
        //draw pp background
        val ppBackgroundWidth = 125f
        drawPath(
            Path()
                .lineTo(cardWidth - cardHeight / 2f, 0f)
                .lineTo(cardWidth - cardHeight / 2f, cardHeight)
                .lineTo(cardWidth - ppBackgroundWidth + 0f, cardHeight)
                .lineTo(cardWidth - ppBackgroundWidth + cardHeight / 4f, cardHeight / 2f)
                .lineTo(cardWidth - ppBackgroundWidth + 0f, 0f)
            , paint.apply {
                color = ppBackgroundColor
                mode = PaintMode.FILL
            })
        drawRRect(
            RRect.makeXYWH(
                (cardWidth - cardHeight),
                0f,
                cardHeight,
                cardHeight,
                15f
            ), paint
        )
        //draw pp text
        val ppValueText = TextLine.make(round(score.pp).toInt().toString(), Font(boldFont, 20f))
        val ppText = TextLine.make("pp", Font(semiBoldFont, 16f))
        val totalWidth = ppValueText.width + ppText.width
        drawTextLine(ppValueText,
            cardWidth - ((ppBackgroundWidth - cardHeight / 4) + totalWidth) / 2f,
            cardHeight / 2f + ppValueText.height / 4f,
            paint.apply {
                color = ppColor
            }
        )
        drawTextLine(ppText,
            cardWidth - ((ppBackgroundWidth - cardHeight / 4) + totalWidth) / 2f + ppValueText.width,
            cardHeight / 2f + ppValueText.height / 4f,
            paint.apply {
                color = ppTextColor
            }
        )
        //pp info
        val accInfoStart = cardWidth - ppBackgroundWidth * (1 + 1.25f)
        val accText = TextLine.make("${format.format(score.accuracy * 100.0f)}%    ", Font(semiBoldFont, 18f))
        val weightedPPText = TextLine.make("${format.format(score.weight!!.pp)}pp", Font(semiBoldFont, 18f))
        val weightedPercentage = TextLine.make("weighted ${format.format(score.weight.percentage)}%", Font(regularFont, 16f))
        drawTextLine(accText,
            accInfoStart,
            (cardHeight - accText.height / 2) / 2f + 2f,
            paint.apply {
                color = accTextColor
            }
        )
        drawTextLine(weightedPPText,
            accInfoStart + accText.width,
            (cardHeight - weightedPPText.height / 2) / 2f + 2f,
            paint.apply {
                color = Color.makeRGB(255, 255, 255)
            }
        )
        drawTextLine(weightedPercentage,
            accInfoStart,
            (cardHeight + weightedPercentage.height * 2) / 2f - 2f,
            paint.apply {
                color = Color.makeRGB(255, 255, 255)
            })
        //mod info
        val marginToPPInfo = accInfoStart - 25f //margin
        var negativeOffset = 0f
        score.mods.ifEmpty {
            listOf("NM")
        }.forEach {
            val icon = image("image/mod_${it.lowercase()}.png")
            negativeOffset += icon.width + 1f
            drawImage(icon, marginToPPInfo - negativeOffset, (cardHeight - icon.height) / 2f + 2f)
        }
    }

    return surface.makeImageSnapshot()
}