package me.stageguard.obms.graph.item

import me.stageguard.obms.bot.route.AnalyzeDetailType
import me.stageguard.obms.bot.route.OrderResult
import me.stageguard.obms.graph.*
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import me.stageguard.obms.graph.common.drawModIcon
import me.stageguard.obms.osu.processor.beatmap.Mod
import kotlin.math.*

@Suppress("DuplicatedCode")
object BestPerformanceDetail {
    // Draw normal card (bpvs or bp)
    private const val cardWidth = 1125f
    private const val cardHeight = 60f
    private const val backgroundPadding = 60f
    private const val intervalBetweenCards = 10f
    private const val ppBackgroundWidth = 125f
    // Draw detailed card
    private const val detailedCardWidth = 1215f + cardHeight
    private const val detailedPpBackgroundWidth = 235f
    // Colors
    private val backgroundBaseColor = Color.makeRGB(42, 34, 38)
    private val backgroundColor = Color.makeRGB(70, 57, 63)
    private val accTextColor = Color.makeRGB(255, 204, 34)
    private val mapVersionTextColor = Color.makeRGB(238, 170, 0)
    private val timeTextColor = Color.makeRGB(163, 143, 152)
    private val ppBackgroundColor = Color.makeRGB(56, 46, 50)
    private val ppColor = Color.makeRGB(255, 102, 171)
    private val ppTextColor = Color.makeRGB(209, 148, 175)
    private val rankDownColor = Color.makeRGB(183, 71, 71)
    private val rankUpColor = Color.makeRGB(140, 220, 62)
    private val beforeRecalculatePpColor = Color.makeRGB(109, 196, 255)
    private val beforeRecalculatePpTextColor = Color.makeRGB(79, 148, 183)

    fun drawBestPerformancesImage(
        result: OrderResult
    ) : Surface {
        val isSingleColumn = result.scores.all { it !is OrderResult.Entry.Versus }
        val isDetailedBp = result.scores.any { it is OrderResult.Entry.DetailAnalyze }
        val theLastLine = result.scores.last().drawLine

        val surfaceWidth = if(isDetailedBp) { //draw detailed bpa
            backgroundPadding * 2 + detailedCardWidth
        } else { // draw bpvs or bpa
            backgroundPadding * 2 + cardWidth + (if(isSingleColumn) 0f else intervalBetweenCards + cardWidth)
        }
        val surfaceHeight = backgroundPadding * 2 + intervalBetweenCards * theLastLine + (theLastLine + 1) * cardHeight

        val surface = Surface.makeRasterN32Premul(surfaceWidth.toInt(), surfaceHeight.toInt())

        surface.canvas.apply {
            clear(backgroundBaseColor)
            val backgroundImage = image("image/background.png")
            drawImage(backgroundImage, 0f, surfaceHeight - backgroundImage.height.toFloat())

            if(isDetailedBp) {
                result.scores.forEach {
                    val card = drawDetailedSingleCard(it as OrderResult.Entry.DetailAnalyze)
                    drawImage(card,
                        backgroundPadding, backgroundPadding + it.drawLine * (card.height + intervalBetweenCards))
                }
            } else {
                if(isSingleColumn) {
                    result.scores.forEach {
                        val card = drawNormalSingleCard(it)
                        drawImage(card,
                            backgroundPadding, backgroundPadding + it.drawLine * (card.height + intervalBetweenCards))
                    }
                } else {
                    result.scores.forEach {
                        val card = drawNormalSingleCard(it)
                        drawImage(card,
                            backgroundPadding + (if(it is OrderResult.Entry.Versus && it.isLeft) 0f else card.width + intervalBetweenCards),
                            backgroundPadding + it.drawLine * (card.height + intervalBetweenCards)
                        )
                    }
                }
            }
        }
        return surface
    }

    private fun drawNormalSingleCard(
        entry: OrderResult.Entry
    ) : Image {
        require(entry.score.pp != null)

        val surface = Surface.makeRasterN32Premul(
            cardWidth.toInt(),
            cardHeight.toInt()
        )

        val paint = Paint().apply {
            isAntiAlias = true
        }

        surface.canvas.apply {
            drawRRect(RRect.makeXYWH(0f, 0f, cardWidth, cardHeight, 15f), paint.apply {
                color = backgroundColor
                mode = PaintMode.FILL
            })
            //rank icon
            val rankSvgImage = svgDom("svg/grade_${entry.score.rank.lowercase()}.svg").run {
                toScaledImage((cardHeight - root!!.height.value) / cardHeight / 0.42f)
            }.also {
                drawImage(it, it.width / 2f, (cardHeight - it.height) / 2f)
            }
            //beatmap info
            val songText = TextLine.make("${entry.score.beatmapset!!.title} ", Font(semiBoldFont, 18f))
            val authorText = TextLine.make("by ${entry.score.beatmapset!!.artist}", Font(semiBoldFont, 16f))
            val variant = TextLine.make("${entry.score.beatmap!!.difficultyRating} | ${entry.score.beatmap!!.version}", Font(regularFont, 16f))
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
            val timePlayed = TextLine.make(entry.score.createdAt.toString(), Font(regularFont, 16f))
            drawTextLine(timePlayed,
                rankSvgImage.width * 2f + variant.width + 20f,
                (cardHeight + variant.height * 2) / 2f - 2f,
                paint.apply {
                    color = timeTextColor
                })
            //draw pp background
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
            drawRRect(RRect.makeXYWH(cardWidth - cardHeight, 0f, cardHeight, cardHeight, 15f), paint)
            //draw pp text
            val ppValueText = TextLine.make(round(entry.score.pp!!).toInt().toString(), Font(boldFont, 20f))
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
                cardWidth - (ppBackgroundWidth - cardHeight / 4 + totalWidth) / 2f + ppValueText.width,
                cardHeight / 2f + ppValueText.height / 4f,
                paint.apply {
                    color = ppTextColor
                }
            )
            //pp info
            val accInfoStart = cardWidth - ppBackgroundWidth - 180f
            val accText = TextLine.make("${format2DFix.format(entry.score.accuracy * 100.0f)}%    ", Font(semiBoldFont, 18f))
            val weightedPPText = TextLine.make("${format2DFix.format(entry.score.weight!!.pp)}pp", Font(semiBoldFont, 18f))
            val weightedPercentage = TextLine.make("weighted ${format2DFix.format(entry.score.weight!!.percentage)}%", Font(regularFont, 16f))
            drawTextLine(accText,
                accInfoStart,
                (cardHeight - accText.height / 2) / 2f + 2f,
                paint.apply {
                    color = accTextColor
                }
            )
            drawTextLine(weightedPPText,
                accInfoStart + 80f,
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
            val modIconWidth = 45f
            val modIconHeight = 32f
            entry.score.mods.map { m -> enumValues<Mod>().find { it.toString() == m } }.forEach { mod ->
                if (mod != null) {
                    negativeOffset += modIconWidth + 1f
                    drawModIcon(mod,
                        modIconWidth, modIconHeight,
                        marginToPPInfo - negativeOffset, (cardHeight - modIconHeight) / 2f + 2f,
                        backgroundColor = Color.makeRGB(255, 204, 33),
                        foregroundColor = Color.makeRGB(84, 84, 84)
                    )
                }
            }
        }

        return surface.makeImageSnapshot()
    }

    private fun drawDetailedSingleCard(
        entry: OrderResult.Entry.DetailAnalyze
    ): Image {
        require(entry.score.pp != null)

        val surface = Surface.makeRasterN32Premul(
            detailedCardWidth.toInt(),
            cardHeight.toInt()
        )

        val paint = Paint().apply {
            isAntiAlias = true
        }

        surface.canvas.apply {
            drawRRect(RRect.makeXYWH(0f, 0f, detailedCardWidth, cardHeight, 15f), paint.apply {
                color = backgroundColor
                mode = PaintMode.FILL
            })
            //rank status background
            drawRRect(
                RRect.makeXYWH(
                    0f,
                    0f,
                    cardHeight * 2,
                    cardHeight,
                    15f
                ), paint.apply {
                    color = ppBackgroundColor
                    mode = PaintMode.FILL
                }
            )
            drawRRect(RRect.makeXYWH(cardHeight, 0f, cardHeight, cardHeight, 15f), paint.apply {
                color = backgroundColor
                mode = PaintMode.FILL
            })
            //rank change text
            val rankChangeText = TextLine.make(
                if (entry.rankChange > 0) "+${entry.rankChange}" else if (entry.rankChange < 0) "${entry.rankChange}" else "-",
                Font(boldFont, 20f)
            )
            drawTextLine(
                rankChangeText,
                ((cardHeight - rankChangeText.width) / 2.0).toFloat(),
                cardHeight / 2f + rankChangeText.height / 4f,
                paint.apply {
                    paint.color = if (entry.rankChange > 0) rankUpColor else if (entry.rankChange < 0) rankDownColor else timeTextColor
                }
            )
            //rank icon
            val rankSvgImage = svgDom("svg/grade_${entry.score.rank.lowercase()}.svg").run {
                toScaledImage((cardHeight - root!!.height.value) / cardHeight / 0.42f)
            }.also {
                drawImage(it, cardHeight + it.width / 2f, (cardHeight - it.height) / 2f)
            }
            //beatmap info
            val songText = TextLine.make("${entry.score.beatmapset!!.title} ", Font(semiBoldFont, 18f))
            val authorText = TextLine.make("by ${entry.score.beatmapset.artist}", Font(semiBoldFont, 16f))
            val variant = TextLine.make("${entry.score.beatmap!!.difficultyRating} | ${entry.score.beatmap.version}", Font(regularFont, 16f))
            drawTextLine(songText,
                cardHeight + rankSvgImage.width * 2f,
                (cardHeight - songText.height / 2) / 2f + 2f,
                paint.apply {
                    color = Color.makeRGB(255, 255, 255)
                })
            drawTextLine(authorText,
                cardHeight + rankSvgImage.width * 2f + songText.width,
                (cardHeight - authorText.height / 2) / 2f + 2f,
                paint.apply {
                    color = timeTextColor
                })
            drawTextLine(variant,
                cardHeight + rankSvgImage.width * 2f,
                (cardHeight + variant.height * 2) / 2f - 2f,
                paint.apply {
                    color = mapVersionTextColor
                })
            val timePlayed = TextLine.make(entry.score.createdAt.toString(), Font(regularFont, 16f))
            drawTextLine(timePlayed,
                cardHeight + rankSvgImage.width * 2f + variant.width + 20f,
                (cardHeight + variant.height * 2) / 2f - 2f,
                paint.apply {
                    color = timeTextColor
                })
            //draw pp background
            drawPath(
                Path()
                    .lineTo(detailedCardWidth - cardHeight / 2f, 0f)
                    .lineTo(detailedCardWidth - cardHeight / 2f, cardHeight)
                    .lineTo(detailedCardWidth - detailedPpBackgroundWidth + 0f, cardHeight)
                    .lineTo(detailedCardWidth - detailedPpBackgroundWidth + cardHeight / 4f, cardHeight / 2f)
                    .lineTo(detailedCardWidth - detailedPpBackgroundWidth + 0f, 0f)
                , paint.apply {
                    color = ppBackgroundColor
                    mode = PaintMode.FILL
                })
            drawRRect(RRect.makeXYWH(detailedCardWidth - cardHeight, 0f, cardHeight, cardHeight, 15f), paint)
            //draw pp text
            val ppText = TextLine.make("pp", Font(semiBoldFont, 16f))
            if(abs(round(entry.score.pp) - round(entry.recalculatedPp)) <= 1) {
                val ppValueText = TextLine.make(round(entry.score.pp).toInt().toString(), Font(boldFont, 20f))
                val totalWidth = ppValueText.width + ppText.width
                drawTextLine(ppValueText,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f,
                    cardHeight / 2f + ppValueText.height / 4f,
                    paint.apply {
                        color = ppColor
                    }
                )
                drawTextLine(ppText,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f + ppValueText.width,
                    cardHeight / 2f + ppValueText.height / 4f,
                    paint.apply {
                        color = ppTextColor
                    }
                )
            } else {
                val beforeRecalculatePPValue = TextLine.make(round(
                    if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) entry.recalculatedPp else entry.score.pp
                ).toInt().toString(), Font(boldFont, 20f))
                val afterRecalculatePPValue = TextLine.make(round(
                    if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) entry.score.pp else entry.recalculatedPp
                ).toInt().toString(), Font(boldFont, 20f))
                val rightArrow = svgDom("svg/arrow_right.svg").run {
                    toScaledImage(beforeRecalculatePPValue.height * 0.8.toFloat() / root!!.height.value)
                }
                val totalWidth = beforeRecalculatePPValue.width + ppText.width + rightArrow.width + beforeRecalculatePPValue.width + ppText.width + 20
                drawTextLine(beforeRecalculatePPValue,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f,
                    cardHeight / 2f + beforeRecalculatePPValue.height / 4f,
                    paint.apply {
                        color = if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) {
                            if(entry.recalculatedPp > entry.score.pp) ppColor else beforeRecalculatePpColor
                        } else { ppColor }
                    }
                )
                drawTextLine(ppText,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f
                            + beforeRecalculatePPValue.width,
                    cardHeight / 2f + beforeRecalculatePPValue.height / 4f,
                    paint.apply {
                        color = if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) {
                            if(entry.recalculatedPp > entry.score.pp) ppTextColor else beforeRecalculatePpTextColor
                        } else { ppTextColor }
                    }
                )
                drawImage(rightArrow,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f +
                            beforeRecalculatePPValue.width + ppText.width + 10.toFloat(),
                    (cardHeight - rightArrow.height) / 2,
                    paint)
                drawTextLine(afterRecalculatePPValue,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f +
                            beforeRecalculatePPValue.width + ppText.width + rightArrow.width + 20.toFloat(),
                    cardHeight / 2f + afterRecalculatePPValue.height / 4f,
                    paint.apply {
                        color = if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) {
                            if(entry.recalculatedPp > entry.score.pp) beforeRecalculatePpColor else ppColor
                        } else { beforeRecalculatePpColor }
                    }
                )
                drawTextLine(ppText,
                    detailedCardWidth - (detailedPpBackgroundWidth - cardHeight / 4 + totalWidth) / 2f +
                            beforeRecalculatePPValue.width + ppText.width + rightArrow.width + afterRecalculatePPValue.width + 20.toFloat(),
                    cardHeight / 2f + afterRecalculatePPValue.height / 4f,
                    paint.apply {
                        color = if(entry.analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) {
                            if(entry.recalculatedPp > entry.score.pp) beforeRecalculatePpTextColor else ppTextColor
                        } else { beforeRecalculatePpTextColor }
                    }
                )
            }
            //pp info
            val accInfoStart = detailedCardWidth - detailedPpBackgroundWidth - 180f
            val accText = TextLine.make("${format2DFix.format(entry.score.accuracy * 100.0f)}%", Font(semiBoldFont, 18f))
            val weightedPPText = TextLine.make("${format2DFix.format(entry.recalculatedWeightedPp)}pp", Font(semiBoldFont, 18f))
            val weightedPercentage = TextLine.make("weighted ${format2DFix.format(entry.recalculatedWeightedPp / entry.recalculatedPp * 100.0f)}%", Font(regularFont, 16f))
            drawTextLine(accText,
                accInfoStart,
                (cardHeight - accText.height / 2) / 2f + 2f,
                paint.apply {
                    color = accTextColor
                }
            )
            drawTextLine(weightedPPText,
                accInfoStart + 80f,
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
            val modIconWidth = 45f
            val modIconHeight = 32f
            entry.score.mods.map { m -> enumValues<Mod>().find { it.toString() == m } }.forEach { mod ->
                if (mod != null) {
                    negativeOffset += modIconWidth + 1f
                    drawModIcon(mod,
                        modIconWidth, modIconHeight,
                        marginToPPInfo - negativeOffset, (cardHeight - modIconHeight) / 2f + 2f,
                        backgroundColor = Color.makeRGB(255, 204, 33),
                        foregroundColor = Color.makeRGB(84, 84, 84)
                    )
                }
            }
        }

        return surface.makeImageSnapshot()
    }
}
