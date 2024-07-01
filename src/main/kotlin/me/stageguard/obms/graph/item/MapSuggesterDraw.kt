package me.stageguard.obms.graph.item

import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.database.model.Ruleset
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.graph.*
import me.stageguard.obms.graph.common.drawDifficultyRatingCard
import me.stageguard.obms.graph.common.drawPPPlusGraph
import me.stageguard.obms.osu.api.dto.BeatmapDTO
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.OptionalValue
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import jakarta.annotation.Resource
import me.stageguard.obms.database.model.OsuUserInfoEx
import org.springframework.stereotype.Component

@Component
class MapSuggesterDraw {
    @Resource
    private lateinit var imageCache: ImageCache
    @Resource
    private lateinit var osuUserInfoEx: OsuUserInfoEx

    //suggester
    private val cardHeight = 430f
    private val cardWidth = 900f
    private val ppPlusGraphHeight = cardHeight - 20f * 2
    private val ppPlusGraphWidth = 280f
    private val difficultyPanelHeight = 70f

    private val songInfoShadowColor = Color.makeARGB(153, 34, 40, 42)
    private val transparent40PercentBlack = Color.makeARGB(100, 14, 16, 17)
    private val transparent20PercentBlack = Color.makeARGB(50, 14, 16, 17)
    private val colorWhite = Color.makeRGB(255, 255, 255)
    private val colorGray = Color.makeARGB(100, 255, 255, 255)
    private val colorYellow = Color.makeRGB(255, 204, 34)
    private val colorGreen = Color.makeRGB(179, 255, 102)

    //ruleset list
    private val rulesetCardHeight = 40f
    private val rulesetCardWidth = 1400f
    private val rulesetHeadHeight = 60f
    private val rulesetGlobalPadding = 60f
    private val rulesetIntervalBetweenCards = 10f

    private val rulesetHeadColor = Color.makeRGB(32, 46, 31)
    private val rulesetListBackgroundColor = Color.makeRGB(35, 42, 34)
    private val rulesetBackgroundColor = Color.makeRGB(23, 28, 23)
    private val rulesetItemBackgroundColor = Color.makeRGB(47, 56, 46)
    private val rulesetPrimaryTextColor = Color.makeRGB(170, 217, 166)
    private val rulesetSecondlyTextColor = Color.makeRGB(145, 163, 143)

    suspend fun drawRecommendBeatmapCard(
        beatmapInfo: OptionalValue<BeatmapDTO>, beatmapType: Ruleset,
        beatmapSkill: BeatmapSkill, additionalTip: String
    ): Surface {
        val songCover = beatmapInfo.mapRight { imageCache.getImageAsSkijaImage(it.beatmapset!!.covers.cover2x) }
        val suggester = osuUserInfoEx.getOsuIdAndName(beatmapType.author).run {
            if (this != null) {
                Either.invoke<Long, Pair<Int, String>>(this)
            } else {
                Either.invoke(beatmapType.author)
            }
        }
        return drawRecommendBeatmapCardImpl(
            beatmapInfo, beatmapType, beatmapSkill, additionalTip, songCover, suggester
        )
    }

    fun drawRulesetList(ruleset: List<Ruleset>, creatorsInfo: List<Pair<Long, Pair<Int, String>?>>): Surface {

        val columnNames = listOf(
            30f to TextLine.make("ID", Font(semiBoldFont, 16f)),
            60f to TextLine.make("Name", Font(semiBoldFont, 16f)),
            250f to TextLine.make("Author", Font(semiBoldFont, 16f)),
            400f to TextLine.make("QQ", Font(semiBoldFont, 16f)),
            495f to TextLine.make("osu!ID", Font(semiBoldFont, 16f)),
            595f to TextLine.make("Triggers", Font(semiBoldFont, 16f)),
            1048f to TextLine.make("Priority", Font(semiBoldFont, 16f)),
            1185f to TextLine.make("Added", Font(semiBoldFont, 16f)),
            1285f to TextLine.make("Last edited", Font(semiBoldFont, 16f))
        )
        val maxColumnHeight = columnNames.maxOf { it.second.capHeight }

        val surfaceWidth = rulesetCardWidth + 2 * rulesetGlobalPadding
        val surfaceHeight = rulesetHeadHeight +
                2 * rulesetGlobalPadding +
                maxColumnHeight +
                rulesetCardHeight * ruleset.size +
                rulesetIntervalBetweenCards * ruleset.size

        val surface = Surface.makeRasterN32Premul(surfaceWidth.toInt(), surfaceHeight.toInt())

        val paint = Paint().apply {
            isAntiAlias = true
        }

        surface.canvas.apply {
            clear(rulesetBackgroundColor)

            drawRect(Rect.makeXYWH(
                rulesetGlobalPadding / 2f, rulesetHeadHeight + rulesetGlobalPadding / 2f,
                surfaceWidth - rulesetGlobalPadding,
                maxColumnHeight + rulesetGlobalPadding +
                        rulesetCardHeight * ruleset.size +
                        rulesetIntervalBetweenCards * ruleset.size
            ), paint.apply {
                color = rulesetListBackgroundColor
            })

            val backgroundImage = image("image/background.png")
            drawImage(backgroundImage, 0f, surfaceHeight - backgroundImage.height.toFloat())
            drawRect(Rect.makeXYWH(0f, 0f, surfaceWidth, rulesetHeadHeight), paint.apply {
                color = rulesetHeadColor
                mode = PaintMode.FILL
            })

            val rulesetText = TextLine.make("Ruleset", Font(semiBoldFont, 30f))
            drawTextLineWithShadow(rulesetText,
                rulesetGlobalPadding,
                rulesetHeadHeight / 2f + rulesetText.capHeight / 2f + 3f,
                paint.apply {
                    color = colorWhite
                }, 3f
            )

            translate(0f, rulesetHeadHeight)
            translate(rulesetGlobalPadding, rulesetGlobalPadding)

            columnNames.forEach {
                drawTextLineWithShadow(it.second, it.first, maxColumnHeight, paint.apply {
                    color = rulesetSecondlyTextColor
                }, 1f)
            }

            translate(0f, rulesetIntervalBetweenCards + maxColumnHeight)

            ruleset.forEach { r ->
                drawImage(
                    drawSingleRulesetItem(r, creatorsInfo.firstOrNull { it.first == r.author }?.second),
                    0f, 0f
                )
                translate(0f, rulesetCardHeight + rulesetIntervalBetweenCards)
            }
        }

        return surface
    }

    private fun drawSingleRulesetItem(skill: Ruleset, creatorInfo: Pair<Int, String>?): Image {
        val surface = Surface.makeRasterN32Premul(rulesetCardWidth.toInt(), rulesetCardHeight.toInt())

        val paint = Paint().apply {
            isAntiAlias = true
        }

        surface.canvas.apply {
            drawRRect(RRect.makeXYWH(0f, 0f, rulesetCardWidth, rulesetCardHeight, 8f), paint.apply {
                color = rulesetItemBackgroundColor
                mode = PaintMode.FILL
            })

            listOf(
                30f to (TextLine.make(skill.id.toString(), Font(regularFont, 16f)) to colorWhite),
                60f to (TextLine.make(skill.name.run {
                    if (this.length > 12) this.take(12).plus("...") else this
                }, Font(semiBoldFont, 16f)) to rulesetPrimaryTextColor),
                250f to (TextLine.make(
                    creatorInfo?.second ?: "<unknown>", Font(semiBoldFont, 16f)
                ) to rulesetPrimaryTextColor),
                380f to (TextLine.make(skill.author.toString(), Font(semiBoldFont, 16f)) to rulesetSecondlyTextColor),
                495f to (TextLine.make(
                    creatorInfo?.run { first.toString() } ?: "<unknown>", Font(semiBoldFont, 16f)
                ) to rulesetSecondlyTextColor),
                595f to (TextLine.make(
                    skill.triggers.replace(";", "; "), Font(semiBoldFont, 16f)
                ) to rulesetSecondlyTextColor),
                1070f to (TextLine.make(
                    skill.priority.toString(),
                    Font(semiBoldFont, 16f)
                ) to rulesetSecondlyTextColor),
                1170f to (TextLine.make(skill.addDate.toString(), Font(semiBoldFont, 16f)) to rulesetSecondlyTextColor),
                1285f to (TextLine.make(
                    skill.lastEdited.toString(),
                    Font(semiBoldFont, 16f)
                ) to rulesetSecondlyTextColor)
            ).forEach {
                drawTextLine(it.second.first,
                    it.first, rulesetCardHeight / 2f + it.second.first.capHeight / 2, paint.apply {
                        color = it.second.second
                    }
                )
            }
        }

        return surface.makeImageSnapshot()
    }

    private fun drawRecommendBeatmapCardImpl(
        beatmapInfo: OptionalValue<BeatmapDTO>, beatmapType: Ruleset,
        beatmapSkill: BeatmapSkill, additionalTip: String,
        songCover: OptionalValue<OptionalValue<Image>>, suggester: Either<Long, Pair<Int, String>>
    ): Surface {
        val surface = Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt())

        val paint = Paint().apply {
            isAntiAlias = true
        }

        surface.canvas.apply {
            //blurred song cover
            songCover.onRight {
                it.onRight { c ->
                    Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt()).run imgSurface@{
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
                        drawImage(
                            blurred, 0f, 0f,
                            Paint().setImageFilter(ImageFilter.makeBlur(8f, 8f, FilterTileMode.CLAMP))
                        )
                    }
                }
            }

            //PP Plus graph
            save()
            translate(cardWidth - 20f - ppPlusGraphWidth, 20f)

            drawPPPlusGraph(
                ppPlusGraphWidth, ppPlusGraphHeight, "Strain skill",
                beatmapSkill.run { mapOf(
                    "Jump" to jumpAimStrain, "Flow" to flowAimStrain, "Speed" to speedStrain,
                    "Stamina" to staminaStrain, "Precision" to precisionStrain, "Complexity" to rhythmComplexity
                ) },
                transparent40PercentBlack, colorWhite, colorYellow, colorGray, paint
            )
            restore()

            //song basic info
            translate(35f, 40f)

            val songTitle = TextLine.make(kotlin.run {
                val title = beatmapInfo.ifRight { it.beatmapset!!.title } ?: "<Unknown>"
                if (title.length > 22) title.take(27).plus("...") else title
            }, Font(semiBoldFont, 40f))
            val songArtist = TextLine.make(
                beatmapInfo.ifRight { it.beatmapset!!.artist } ?: "<Unknown>",
                Font(semiBoldFont, 28f)
            )
            drawTextLineWithShadow(songTitle, 0f, songTitle.capHeight, paint.apply {
                color = beatmapInfo.ifRight { colorWhite } ?: colorGray
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 3f)
            drawTextLineWithShadow(songArtist,
                3f, songTitle.capHeight + 15f + songArtist.capHeight,
                paint.apply {
                    color = beatmapInfo.ifRight { colorWhite } ?: colorGray
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 3f
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
                25, transparent40PercentBlack, colorWhite, colorYellow, paint
            )

            translate(3f, difficultyPanelHeight + 22f)

            //song attributes
            val lenT = TextLine.make("Length: ", Font(regularFont, 20f))
            val lenV = TextLine.make(parseTime(beatmapSkill.length), Font(regularFont, 20f))
            val bpmT = TextLine.make(" / BPM: ", Font(regularFont, 20f))
            val bpmV = TextLine.make(beatmapSkill.bpm.toString(), Font(regularFont, 20f))
            listOf(lenT, lenV, bpmT, bpmV).foldIndexed(0f) { idx, acc, text ->
                drawTextLineWithShadow(text, acc, text.capHeight, paint.apply {
                    color = if ((idx + 1) % 2 == 0) colorYellow else colorWhite
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
                    color = if ((idx + 1) % 2 == 0) colorYellow else colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f)
                acc + text.width
            }

            translate(-3f, 20f)

            //suggester
            drawRRect(
                RRect.makeXYWH(
                    0f, 0f,
                    cardWidth - (20f + ppPlusGraphWidth + 20f + 35f), cardHeight - (40f +
                            songTitle.capHeight + 15f + songArtist.capHeight +
                            18f + mapperName.capHeight + 22f +
                            difficultyPanelHeight + 22f +
                            lenT.capHeight + 15f +
                            attrText.capHeight + 20f + 20f),
                    12f
                ),
                paint.apply {
                    color = transparent20PercentBlack
                    mode = PaintMode.FILL
                }
            )

            translate(20f, 20f)

            val rulesetByText = TextLine.make("Ruleset: ", Font(regularFont, 16f))
            drawTextLineWithShadow(rulesetByText, 0f, rulesetByText.capHeight, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            val rulesetText = TextLine.make(beatmapType.name, Font(regularFont, 16f))
            drawTextLineWithShadow(rulesetText, rulesetByText.width, rulesetByText.capHeight, paint.apply {
                color = colorGreen
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            translate(0f, 15f + rulesetByText.capHeight)

            val suggestedByText = TextLine.make("suggested by ", Font(regularFont, 16f))
            drawTextLineWithShadow(suggestedByText, 0f, suggestedByText.capHeight,
                paint.apply {
                    color = colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f
            )

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

            translate(0f, 15f + suggestedByText.capHeight)

            val addonTipText = TextLine.make(additionalTip.ifEmpty { "No comment" }, Font(regularFont, 16f))
            drawTextLineWithShadow(addonTipText, 0f, addonTipText.capHeight, paint.apply {
                color = if (additionalTip.isEmpty()) colorGray else colorYellow
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

        }

        return surface
    }
}
