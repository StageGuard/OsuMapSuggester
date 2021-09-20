package me.stageguard.obms.graph.item

import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.database.model.BeatmapType
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.graph.*
import me.stageguard.obms.graph.common.drawDifficultyRatingCard
import me.stageguard.obms.graph.common.drawPPPlusGraph
import me.stageguard.obms.osu.api.dto.BeatmapDTO
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.ValueOrISE
import org.jetbrains.skija.*

object MapSuggester {
    private const val cardHeight = 400f
    private const val cardWidth = 900f
    private const val ppPlusGraphHeight = cardHeight - 20f * 2
    private const val ppPlusGraphWidth = 280f
    private const val difficultyPanelHeight = 70f

    private val songInfoShadowColor = Color.makeARGB(153, 34, 40, 42)
    private val transparent40PercentBlack = Color.makeARGB(100, 14, 16, 17)
    private val transparent20PercentBlack = Color.makeARGB(50, 14, 16, 17)
    private val colorWhite = Color.makeRGB(255, 255, 255)
    private val colorGray = Color.makeARGB(100, 255, 255, 255)
    private val colorYellow = Color.makeRGB(255, 204, 34)
    private val colorGreen = Color.makeRGB(179, 255, 102)

    suspend fun drawRecommendBeatmapCard(
        beatmapInfo: ValueOrISE<BeatmapDTO>, beatmapType: BeatmapType,
        beatmapSkill: BeatmapSkill, additionalTip: String
    ) : Surface {
        val songCover = beatmapInfo.mapRight { ImageCache.getImageAsSkijaImage(it.beatmapset!!.covers.cover2x) }
        val suggester = OsuUserInfo.getOsuIdAndName(beatmapType.author).run {
            if(this != null) {
                Either.invoke<Long, Pair<Int, String>>(this)
            } else {
                Either.invoke(beatmapType.author)
            }
        }
        return drawRecommendBeatmapCardImpl(
            beatmapInfo, beatmapType, beatmapSkill, additionalTip, songCover, suggester
        )
    }

    private fun drawRecommendBeatmapCardImpl(
        beatmapInfo: ValueOrISE<BeatmapDTO>, beatmapType: BeatmapType,
        beatmapSkill: BeatmapSkill, additionalTip: String,
        songCover: ValueOrISE<ValueOrISE<Image>>, suggester: Either<Long, Pair<Int, String>>
    ) : Surface {
        val surface = Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt())

        val paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.HIGH
        }

        surface.canvas.apply {
            //blurred song cover
            songCover.onRight { it.onRight { c ->
                Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt()).run imgSurface@ {
                    this.canvas.drawImage(
                        c.cutCenter(cardWidth / c.width, cardHeight / c.height),
                        0f, 0f
                    )
                    this.canvas.drawRect(
                        Rect.makeXYWH(0f, 0f, cardWidth, cardHeight),
                        Paint().setColor(songInfoShadowColor).setMode(PaintMode.FILL)
                    )
                    makeImageSnapshot()
                }.also { blurred ->
                    drawImage(blurred, 0f, 0f,
                        Paint().setImageFilter(ImageFilter.makeBlur(8f, 8f, FilterTileMode.CLAMP))
                    )
                }
            } }

            //PP Plus graph
            save()
            translate(cardWidth - 20f - ppPlusGraphWidth, 20f)
            drawPPPlusGraph(
                ppPlusGraphWidth, ppPlusGraphHeight,
                beatmapSkill.jumpAimStrain, beatmapSkill.flowAimStrain, beatmapSkill.speedStrain,
                beatmapSkill.staminaStrain, beatmapSkill.precisionStrain, beatmapSkill.rhythmComplexity,
                transparent40PercentBlack, colorWhite, colorYellow, colorGray, paint
            )
            restore()

            //song basic info
            translate(35f, 40f)

            val songTitle = TextLine.make(kotlin.run {
                val title = beatmapInfo.ifRight { it.beatmapset!!.title } ?: "<Unknown>"
                if(title.length > 22) title.take(27).plus("...") else title
            }, Font(semiBoldFont, 40f))
            val songArtist = TextLine.make(
                beatmapInfo.ifRight { it.beatmapset!!.artist } ?: "<Unknown>",
                Font(semiBoldFont, 28f)
            )
            drawTextLineWithShadow(songTitle, 0f, songTitle.capHeight, paint.apply {
                color = beatmapInfo.ifRight { colorWhite } ?: colorGray
                mode = PaintMode.FILL
                strokeWidth = 1f
            })
            drawTextLineWithShadow(songArtist,
                3f, songTitle.capHeight + 15f + songArtist.capHeight,
                paint.apply {
                    color = beatmapInfo.ifRight { colorWhite } ?: colorGray
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }
            )

            translate(0f, songTitle.capHeight + 15f + songArtist.capHeight)

            //mapper info
            val mapperName = TextLine.make(
                "mapped by ${beatmapInfo.ifRight { it.beatmapset!!.creator } ?: "<Unknown>"}",
                Font(regularFont, 20f)
            )
            drawTextLineWithShadow(mapperName, 3f, 18f + mapperName.capHeight, paint.apply {
                color = beatmapInfo.ifRight { colorWhite } ?: colorGray
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            translate(0f, 18f + mapperName.capHeight + 22f)

            drawDifficultyRatingCard(
                difficultyPanelHeight,
                beatmapInfo.ifRight { it.difficultyRating } ?: beatmapSkill.stars,
                beatmapInfo.ifRight { it.version } ?: "<Unknown>",
                25, transparent40PercentBlack, colorWhite, colorWhite, paint
            )

            translate(0f, difficultyPanelHeight + 22f)

            //song attributes
            val lenT = TextLine.make("Length: ", Font(regularFont, 20f))
            val lenV = TextLine.make(parseTime(beatmapSkill.length), Font(regularFont, 20f))
            val bpmT = TextLine.make(" / BPM: ", Font(regularFont, 20f))
            val bpmV = TextLine.make(beatmapSkill.bpm.toString(), Font(regularFont, 20f))
            listOf(lenT, lenV, bpmT, bpmV).foldIndexed(0f) { idx, acc, text ->
                drawTextLineWithShadow(text, acc, text.capHeight, paint.apply {
                    color = if((idx + 1) % 2 == 0) colorYellow else colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f)
                acc + text.width
            }

            translate(0f, lenT.capHeight + 15f)

            val attrText = TextLine.make("Attribute: ", Font(regularFont, 20f))

            translate(0f, attrText.capHeight)
            drawTextLineWithShadow(attrText, 0f, 0f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)
            listOf(
                TextLine.make("CS: ", Font(regularFont, 20f)),
                TextLine.make(beatmapSkill.circleSize.toString(), Font(regularFont, 20f)),
                TextLine.make(" / HP: ", Font(regularFont, 20f)),
                TextLine.make(beatmapSkill.hpDrain.toString(), Font(regularFont, 20f)),
                TextLine.make(" / AR: ", Font(regularFont, 20f)),
                TextLine.make(beatmapSkill.approachingRate.toString(), Font(regularFont, 20f)),
                TextLine.make(" / OD: ", Font(regularFont, 20f)),
                TextLine.make(beatmapSkill.overallDifficulty.toString(), Font(regularFont, 20f))
            ).foldIndexed(attrText.width) { idx, acc, text ->
                drawTextLineWithShadow(text, acc, 0f, paint.apply {
                    color = if((idx + 1) % 2 == 0) colorYellow else colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f)
                acc + text.width
            }

            translate(0f, 20f)

            //suggester
            drawRRect(
                RRect.makeXYWH(0f, 0f,
                cardWidth - (20f + ppPlusGraphWidth + 20f + 35f), cardHeight - (40f +
                    songTitle.capHeight + 15f + songArtist.capHeight +
                    18f + mapperName.capHeight + 22f +
                    difficultyPanelHeight + 22f +
                    lenT.capHeight + 15f +
                    attrText.capHeight + 20f + 20f),
                12f),
                paint.apply {
                    color = transparent20PercentBlack
                    mode = PaintMode.FILL
                }
            )

            translate(20f, 20f)

            val suggestedByText = TextLine.make("suggested by ", Font(regularFont, 16f))
            drawTextLineWithShadow(suggestedByText, 0f, suggestedByText.capHeight, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            (suggester.ifRight { s ->
                listOf(
                    colorGreen to TextLine.make(s.second, Font(regularFont, 16f)),
                    colorWhite to TextLine.make(" (qq: ", Font(regularFont, 16f)),
                    colorYellow to TextLine.make(beatmapType.author.toString(), Font(regularFont, 16f)),
                    colorWhite to TextLine.make(" / osu!Id: ", Font(regularFont, 16f)),
                    colorYellow to TextLine.make(s.first.toString(), Font(regularFont, 16f)),
                    colorWhite to TextLine.make(")", Font(regularFont, 16f))
                )
            } ?: listOf(
                colorYellow to TextLine.make(beatmapType.author.toString(), Font(regularFont, 16f))
            )).fold(suggestedByText.width) { acc, pair ->
                drawTextLineWithShadow(pair.second, acc, pair.second.capHeight, paint.apply {
                    color = pair.first
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f)
                acc + pair.second.width
            }


            val addonTipText = TextLine.make(additionalTip, Font(regularFont, 16f))
            translate(0f, 15f + suggestedByText.capHeight + addonTipText.capHeight)
            drawTextLineWithShadow(addonTipText, 0f, 0f, paint.apply {
                color = colorYellow
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

        }

        return surface
    }
}