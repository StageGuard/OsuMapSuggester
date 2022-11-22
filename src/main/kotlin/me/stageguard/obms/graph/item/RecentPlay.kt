package me.stageguard.obms.graph.item

import me.stageguard.obms.ImageReadException
import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.graph.*
import me.stageguard.obms.graph.common.drawDifficultyRatingCard
import me.stageguard.obms.graph.common.drawPPPlusGraph
import me.stageguard.obms.graph.common.drawPpCurveGraph
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.api.dto.BeatmapUserScoreDTO
import me.stageguard.obms.osu.api.dto.BeatmapsetDTO
import me.stageguard.obms.osu.api.dto.GetUserDTO
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.replay.ReplayFrameAnalyzer
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.isRight
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import me.stageguard.obms.utils.InferredOptionalValue
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import java.lang.Exception
import java.util.*
import kotlin.math.*

object RecentPlay {
    private val scale = 3.0f
    private val cardWidth get() = 1305 * scale
    private val cardHeight get() = 780 * scale
    private val songInfoHeight get() = 345 * scale
    private val songInfoPadding get() = 40 * scale
    private val songHeaderImageWidth get() = 370f * scale
    private val mapperAvatarEdgeLength get() = 60f * scale
    private val playerAvatarEdgeLength get() = 68f * scale
    private val difficultyPanelHeight get() = 70f * scale
    private val rankStatusHeight get() = 55f  * scale
    private val beatmapAttributePanelWidth get() = 360 * scale
    private val beatmapAttributePanelHeight get() = 205f * scale
    private val replayDataHeight get() = 55f * scale
    private val replayDetailWidth get() = 300f * scale
    private val scoreInfoHeight get() =  cardHeight - songInfoHeight

    val songInfoShadowColor = Color.makeARGB(153, 34, 40, 42)
    val scoreInfoBackgroundColor = Color.makeRGB(42, 34, 38)
    val colorWhite = Color.makeRGB(255, 255, 255)
    val colorGray = Color.makeARGB(100, 255, 255, 255)
    val colorGray2 = Color.makeARGB(45, 255, 255, 255)
    val colorYellow = Color.makeRGB(255, 204, 34)
    val colorPink = Color.makeRGB(255, 102, 171)
    val colorGreen = Color.makeRGB(179, 255, 102)
    val colorRed = Color.makeRGB(255, 98, 98)
    val transparent40PercentBlack = Color.makeARGB(100, 14, 16, 17)
    val grayLine = Color.makeRGB(70, 57, 63)
    val replayItemTitleIconColor = Color.makeRGB(2, 247, 165)
    private fun accuracyHeatmapDotColor(a: Double) = Color.makeARGB((a * 255).toInt(), 114, 224, 193)

    private val defaultAvatarImage: OptionalValue<Image>
        get() = try {
            InferredOptionalValue(image("image/avatar_guest.png"))
        } catch (ex: Exception) {
            Either(ImageReadException("image/avatar_guest.png").suppress(ex))
        }

    private suspend fun getAvatarFromUrlOrDefault(url: String) =
        ImageCache.getImageAsSkijaImage(url).rightOrNull ?: defaultAvatarImage.rightOrNull ?:
            throw IllegalStateException("Cannot get avatar fom server and local: $url")


    suspend fun drawRecentPlayCard(
        scoreDTO: ScoreDTO, beatmapSet: BeatmapsetDTO,
        mapperInfo: GetUserDTO?, mods: ModCombination,
        attribute: OptionalValue<DifficultyAttributes>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        skillAttributes: OptionalValue<SkillAttributes>,
        userBestScore: OptionalValue<BeatmapUserScoreDTO>,
        replayAnalyzer: OptionalValue<ReplayFrameAnalyzer>
    ) : Surface {
        val playerAvatar = getAvatarFromUrlOrDefault(scoreDTO.user!!.avatarUrl)
        val songCover = ImageCache.getImageAsSkijaImage(beatmapSet.covers.cover2x)
        val mapperAvatar = mapperInfo?.avatarUrl?.let { getAvatarFromUrlOrDefault(it) }

        return drawRecentPlayCardImpl(
            scoreDTO, beatmapSet, mods, attribute, ppCurvePoints, skillAttributes,
            userBestScore, replayAnalyzer, playerAvatar, mapperAvatar, songCover
        )
    }

    @Suppress("DuplicatedCode")
    private fun drawRecentPlayCardImpl(
        scoreDTO: ScoreDTO, beatmapSet: BeatmapsetDTO, mods: ModCombination,
        attribute: OptionalValue<DifficultyAttributes>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        skillAttributes: OptionalValue<SkillAttributes>,
        userBestScore: OptionalValue<BeatmapUserScoreDTO>,
        replayAnalyzer: OptionalValue<ReplayFrameAnalyzer>,
        playerAvatar: Image, mapperAvatar: Image?, songCover: OptionalValue<Image>,
    ) : Surface {
        val surface = Surface.makeRasterN32Premul(
            (replayAnalyzer.ifRight { (cardWidth + replayDetailWidth).toInt() } ?: cardWidth).toInt(),
            cardHeight.toInt()
        )

        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = PaintStrokeCap.ROUND
        }

        surface.canvas.apply {
            val baseSavePoint = save()
            //background
            songCover.onRight {
                drawImage(
                    it.scale(cardWidth / it.width.toFloat(), songInfoHeight / it.height.toFloat()),
                    0F, 0F
                )
            }.onLeft {
                drawRect(Rect.makeXYWH(0F, 0F, cardWidth, songInfoHeight), paint.apply {
                    color = scoreInfoBackgroundColor
                    mode = PaintMode.FILL
                })
            }
            drawRect(Rect.makeXYWH(0F, 0F, cardWidth, songInfoHeight), paint.apply {
                color = songInfoShadowColor
                mode = PaintMode.FILL
            })
            drawRect(Rect.makeXYWH(0F, songInfoHeight, cardWidth, scoreInfoHeight), paint.apply {
                color = scoreInfoBackgroundColor
                mode = PaintMode.FILL
            })

            //song header image
            /*songCover.onRight {
                val scaledSongHeaderBase = it.scale(songHeaderImageWidth / it.width)
                val scaledSongHeader = scaledSongHeaderBase.cutCenter(
                    songHeaderImageWidth / scaledSongHeaderBase.width,
                    (songInfoHeight - 2 * songInfoPadding) / scaledSongHeaderBase.height
                )
                val songHeaderRRect = RRect.makeXYWH(songInfoPadding, songInfoPadding,
                    scaledSongHeader.width.toFloat(), scaledSongHeader.height.toFloat(), 16f
                )
                drawRoundCorneredImage(scaledSongHeader, songInfoPadding, songInfoPadding, 16f)
                drawRRect(songHeaderRRect, paint.apply {
                    color = Color.makeARGB(80, 0, 0, 0)
                    mode = PaintMode.STROKE
                    strokeWidth = 5f
                })
            }*/

            val songInfoSavePoint = save()

            val songBid = TextLine.make("BID: ${scoreDTO.beatmap!!.id}", Font(semiBoldFont, 16f * scale))
            drawRRect(RRect.makeXYWH(
                cardWidth - songBid.width - 20f * scale, 0f, songBid.width + 20f * scale, songBid.capHeight + 20f * scale, 8f * scale
            ), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })
            drawTextLine(songBid, cardWidth - songBid.width - 10f * scale, songBid.capHeight + 10f * scale, paint.apply {
                color = colorGray
                mode = PaintMode.FILL
                strokeWidth = 1f * scale
            })

            //song basic info
            val songTitle = TextLine.make(kotlin.run {
                val title = beatmapSet.title
                if(title.length > 30) title.take(27).plus("...") else title
            }, Font(semiBoldFont, 42f * scale))

            translate(songInfoPadding /*+ songHeaderImageWidth*/ + 20f * scale, songInfoPadding + songTitle.capHeight + 14f * scale)

            drawTextLineWithShadow(songTitle, 0f, 0f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            })
            val songArtist = TextLine.make(beatmapSet.artist, Font(semiBoldFont, 28f * scale))
            drawTextLineWithShadow(songArtist, 3f * scale, songTitle.capHeight + 15f * scale, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f * scale
            })

            translate(0f, songTitle.capHeight + songArtist.capHeight + 27f * scale)

            //mapper info
            mapperAvatar ?.run a@ {
                val scaledMapperAvatar = this@a.scale(mapperAvatarEdgeLength / this@a.width, mapperAvatarEdgeLength / this@a.height)
                drawRoundCorneredImage(scaledMapperAvatar, 0f, 0f, 12f * scale)
            }

            val mapperName = TextLine.make("mapped by ${beatmapSet.creator}", Font(regularFont, 20f * scale))
            drawTextLineWithShadow(mapperName, mapperAvatarEdgeLength + 15f * scale, mapperName.capHeight + 10f * scale, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f * scale
            }, 2f * scale)

            val beatmapsetCreateTime = TextLine.make("created at ${scoreDTO.beatmap.lastUpdated}", Font(regularFont, 20f * scale))
            drawTextLineWithShadow(beatmapsetCreateTime, mapperAvatarEdgeLength + 15f * scale,
                mapperName.capHeight + beatmapsetCreateTime.capHeight + 22f * scale,
                paint.apply {
                    color = colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f * scale
                }, 2f * scale
            )

            translate(0f, mapperAvatarEdgeLength + 23f * scale)

            //difficulty rating
            drawDifficultyRatingCard(
                difficultyPanelHeight,
                attribute.ifRight { format2DFix.format(it.stars).toDouble() } ?: scoreDTO.beatmap.difficultyRating,
                scoreDTO.beatmap.version, 25,
                transparent40PercentBlack, colorWhite, colorYellow, paint, scale
            )

            restoreToCount(songInfoSavePoint)

            //rank status
            val rankStatus = TextLine.make(beatmapSet.status.uppercase(Locale.getDefault()), Font(boldFont, 28f * scale))
            drawRRect(
                RRect.makeXYWH(
                cardWidth - songInfoPadding - rankStatus.width - 40f * 2 * scale, songInfoPadding,
                rankStatus.width + 40f * 2 * scale, rankStatusHeight, 90f * scale
            ), paint.apply {
                mode = PaintMode.FILL
                color = transparent40PercentBlack
            })
            drawTextLine(rankStatus,
                cardWidth - songInfoPadding - rankStatus.width - 40f * scale, songInfoPadding + rankStatusHeight / 2 + rankStatus.capHeight / 2,
                paint.apply {
                    color = colorGray
                }
            )

            save()
            translate(cardWidth - songInfoPadding - beatmapAttributePanelWidth, songInfoPadding + rankStatusHeight + 15f * scale)

            //difficulty attributes
            drawRRect(RRect.makeXYWH(0f, 0f, beatmapAttributePanelWidth, beatmapAttributePanelHeight, 16f * scale), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            val totalLengthIcon = svgDom("svg/total_length.svg").toScaledImage(0.07f * scale)
            val totalLengthText = TextLine.make(parseTime(when {
                mods.dt() -> round(scoreDTO.beatmap.totalLength / 1.5).toInt()
                mods.ht() -> round(scoreDTO.beatmap.totalLength * 1.33).toInt()
                else -> scoreDTO.beatmap.totalLength
            }), Font(semiBoldFont, 18f * scale))
            val bpmIcon = svgDom("svg/bpm.svg").toScaledImage(0.07f * scale)
            val bpmText = TextLine.make(when {
                mods.dt() -> format1DFix.format(scoreDTO.beatmap.bpm * 1.5)
                mods.ht() -> format1DFix.format(scoreDTO.beatmap.bpm / 1.33)
                else -> format1DFix.format(scoreDTO.beatmap.bpm)
            }, Font(semiBoldFont, 18f * scale))

            val totalLengthWidth = totalLengthIcon.width + 20f * scale + totalLengthText.width
            val bpmWidth = bpmIcon.width + 20f * scale + bpmText.width

            drawImage(totalLengthIcon, (beatmapAttributePanelWidth / 2 - totalLengthWidth) / 2, 12f * scale)
            drawTextLineWithShadow(totalLengthText,
                (beatmapAttributePanelWidth / 2 - totalLengthWidth) / 2 + 20f * scale + totalLengthIcon.width - 8f * scale,
                13f * scale + totalLengthIcon.height / 2 + totalLengthText.capHeight / 2, paint.apply {
                    color = if(mods.dt() || mods.ht()) colorYellow else colorWhite
                }, 1f
            )
            paint.color = colorWhite

            drawImage(bpmIcon, beatmapAttributePanelWidth / 2 + (beatmapAttributePanelWidth / 2 - bpmWidth) / 2 - 10f * scale, 12f * scale)
            drawTextLineWithShadow(bpmText,
                beatmapAttributePanelWidth / 2 + (beatmapAttributePanelWidth / 2 - bpmWidth) / 2 + 20f * scale + totalLengthIcon.width - 10f * scale,
                13f * scale + bpmIcon.height / 2 + bpmText.capHeight / 2, paint.apply {
                    color = if(mods.dt() || mods.ht()) colorYellow else colorWhite
                }, 1f
            )
            paint.color = colorWhite

            translate(20f * scale, totalLengthIcon.height + 12f * 2 * scale)

            val circleSizeText = TextLine.make("Circle Size", Font(regularFont, 18f * scale))
            val hpDrainText = TextLine.make("HP Drain", Font(regularFont, 18f * scale))
            val approachRateText = TextLine.make("Approach Rate", Font(regularFont, 18f * scale))
            val overallDifficultyText = TextLine.make("Overall Difficulty", Font(regularFont, 18f * scale))

            drawTextLineWithShadow(circleSizeText, 0f, circleSizeText.capHeight, paint, 1f * scale)
            drawTextLineWithShadow(hpDrainText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + 13f * scale, paint, 1f * scale)
            drawTextLineWithShadow(approachRateText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + hpDrainText.capHeight + 26f * scale, paint, 1f * scale)
            drawTextLineWithShadow(overallDifficultyText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + hpDrainText.capHeight + approachRateText.capHeight + 39f * scale, paint, 1f * scale)


            val circleSize = TextLine.make(attribute.ifRight {
                format1DFix.format(it.circleSize)
            } ?: scoreDTO.beatmap.cs.toString(), Font(boldFont, 18f * scale))
            val hpDrain = TextLine.make(attribute.ifRight {
                format1DFix.format(it.hpDrain)
            } ?: scoreDTO.beatmap.drain.toString(), Font(boldFont, 18f * scale))
            val approachRate = TextLine.make(attribute.ifRight {
                format1DFix.format(it.approachRate)
            } ?: scoreDTO.beatmap.ar.toString(), Font(boldFont, 18f * scale))
            val overallDifficulty = TextLine.make(attribute.ifRight {
                format1DFix.format(it.overallDifficulty)
            } ?: scoreDTO.beatmap.accuracy.toString(), Font(boldFont, 18f * scale))

            val maxAttributeWidth = max(max(circleSize.width, hpDrain.width), max(approachRate.width, overallDifficulty.width))

            drawTextLineWithShadow(circleSize, beatmapAttributePanelWidth - 20f * 2 * scale - maxAttributeWidth, circleSize.capHeight, paint.apply {
                color = if(mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f * scale)
            paint.color = colorWhite
            drawTextLineWithShadow(hpDrain, beatmapAttributePanelWidth - 20f * 2 * scale - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + 13f * scale, paint.apply {
                color = if(mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f * scale)
            paint.color = colorWhite
            drawTextLineWithShadow(approachRate, beatmapAttributePanelWidth - 20f * 2 * scale - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + 26f * scale, paint.apply {
                color = if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f * scale)
            paint.color = colorWhite
            drawTextLineWithShadow(overallDifficulty, beatmapAttributePanelWidth - 20f * 2 * scale - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight + 39f * scale, paint.apply {
                color = if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f * scale)
            paint.color = colorWhite

            val maxTextWidth = max(max(circleSizeText.width, hpDrainText.width), max(approachRateText.width, overallDifficultyText.width))

            val attributeBarLength = beatmapAttributePanelWidth - 40f * scale - maxAttributeWidth - maxTextWidth - (10f + 10f) * scale //5f and 10f is translation offset

            translate(maxTextWidth + 10f * scale, 0f)

            //circleSize bar
            drawLine(
                0f, circleSize.capHeight / 2,
                attributeBarLength, circleSize.capHeight / 2,
                paint.apply {
                    color = transparent40PercentBlack
                    strokeWidth = 8f * scale
                    strokeCap = PaintStrokeCap.ROUND
                }
            )
            drawLine(
                0f, circleSize.capHeight / 2,
                attributeBarLength * ((attribute.ifRight { it.circleSize } ?: scoreDTO.beatmap.cs) / 11.0).toFloat(), circleSize.capHeight / 2,
                paint.setColor(if(mods.hr() || mods.ez()) colorYellow else colorWhite)
            )
            //hpDrain
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight / 2 + 13f * scale,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight / 2 + 13f * scale,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight / 2 + 13f * scale,
                attributeBarLength * ((attribute.ifRight { it.hpDrain } ?: scoreDTO.beatmap.drain) / 11.0).toFloat(), circleSize.capHeight + hpDrain.capHeight / 2 + 13f * scale,
                paint.setColor(if(mods.hr() || mods.ez()) colorYellow else colorWhite)
            )
            //approachRate
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f * scale,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f * scale,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f * scale,
                attributeBarLength * ((attribute.ifRight { it.approachRate } ?: scoreDTO.beatmap.ar) / 11.0).toFloat(),
                circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f * scale,
                paint.setColor(if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite)
            )
            //overallDifficulty
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f * scale,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f * scale,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f * scale,
                attributeBarLength * ((attribute.ifRight { it.overallDifficulty } ?: scoreDTO.beatmap.accuracy) / 11.0).toFloat(),
                circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f * scale,
                paint.setColor(if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite)
            )

            translate(- maxTextWidth - 10f * scale, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight + (42f + 10f) * scale)

            var modXOffset = 0f
            mods.toList().map { m ->
                m.toString().lowercase(Locale.getDefault())
            }.forEach {
                val icon = image("image/mod_$it.png").scale(scale)
                drawImage(icon, modXOffset, 0f)
                modXOffset += icon.width + 3f * scale
            }

            restore()
            translate(0f, songInfoHeight)

            //player info
            val scaledPlayerAvatarImage = playerAvatar.scale(playerAvatarEdgeLength / playerAvatar.width)
            val playerName = TextLine.make(scoreDTO.user!!.username, Font(semiBoldFont, 22f * scale))
            val playTime = TextLine.make("played at ${scoreDTO.createdAt}", Font(semiBoldFont, 20f * scale))

            drawRoundCorneredImage(scaledPlayerAvatarImage, 40f * scale, 20f * scale, 12f * scale)
            drawTextLineWithShadow(playerName,
                40f * scale + scaledPlayerAvatarImage.width + 20f * scale,
                20f * scale + playerName.capHeight + 13f * scale, paint.apply {
                color = colorWhite
            }, 2f * scale)
            drawTextLineWithShadow(playTime,
                40f * scale + scaledPlayerAvatarImage.width + 20f * scale,
                20f * scale + playerName.capHeight + playTime.capHeight + 27f * scale, paint.apply {
                color = colorWhite
            }, 2f * scale)

            val replayAvailable = if(scoreDTO.replay == true) "Replay is available." else "Replay is unavailable."
            val replay = TextLine.make(replayAvailable, Font(semiBoldFont, 22f * scale))

            drawRRect(RRect.makeXYWH(
                cardWidth - 40f * scale - replay.width - 80f * scale,
                (40f * scale + scaledPlayerAvatarImage.height - replayDataHeight) / 2,
                replay.width + 80f * scale, replayDataHeight, 90f * scale
            ), paint.apply {
                mode = PaintMode.FILL
                color = transparent40PercentBlack
            })
            drawTextLine(replay, cardWidth - 40f * scale - replay.width - 40f * scale,
                (40f * scale + scaledPlayerAvatarImage.height) / 2 + replay.capHeight / 2,
                paint.setColor(colorGray)
            )

            drawLine(
                32f * scale, 40f * scale + scaledPlayerAvatarImage.height,
                cardWidth - 32f * scale, 40f * scale + scaledPlayerAvatarImage.height, paint.apply {
                    color = colorPink
                    strokeWidth = 3f * scale
                    strokeCap = PaintStrokeCap.ROUND
                }
            )

            translate(0f, 40f * scale + scaledPlayerAvatarImage.height)
            val scoreInfoSavePoint = save()

            //middle gray line
            drawLine(
                cardWidth / 2, 20f * scale, cardWidth / 2,
                scoreInfoHeight - 40f * scale - scaledPlayerAvatarImage.height - 20f * scale,
                paint.setColor(grayLine)
            )

            translate(20f * scale, 20f * scale)
            val xHeight = scoreInfoHeight - 40f * scale - scaledPlayerAvatarImage.height - 40f * scale
            val xWidth = (cardWidth - 40f * scale) / 2

            //rank icon
            val rankBGRadius = 85f * scale
            drawCircle(xWidth * 0.48f, xHeight / 2, rankBGRadius, paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            val scaledRankIcon = svgDom("svg/grade_${scoreDTO.rank.lowercase(Locale.getDefault())}.svg").toScaledImage(3.2f * scale)
            drawImage(scaledRankIcon, xWidth * 0.48f - scaledRankIcon.width / 2, xHeight / 2 - scaledRankIcon.height / 2)

            //hit result
            save()
            translate(xWidth * 0.45f, xHeight / 2)

            val intervalBetweenHitIconAndText = 10f * scale

            val hitGreatIcon = TextLine.make("GREAT", Font(boldFont, 32f * scale))
            val hitGreatText = TextLine.make(usNumber.format(scoreDTO.statistics.count300), Font(semiBoldFont, 28f * scale))
            val hitGreatHeight = hitGreatIcon.capHeight + hitGreatText.capHeight + intervalBetweenHitIconAndText
            val hitGreatWidth = max(hitGreatIcon.width, hitGreatText.width)

            drawTextLineWithShadow(hitGreatIcon,
                (cos(PI / 2.0 + PI / 5.0 * 1) * 180f * scale - hitGreatWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 1) * 130f * scale - hitGreatHeight / 2 + hitGreatIcon.capHeight).toFloat(),
                paint.apply { color = Color.makeRGB(110, 183, 214) },
                2f * scale, shadowColor = Color.makeRGB(70, 117, 137)
            )
            drawTextLineWithShadow(hitGreatText,
                (cos(PI / 2.0 + PI / 5.0 * 1) * 180f * scale - hitGreatWidth / 2 + (hitGreatWidth - hitGreatText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 1) * 130f * scale - hitGreatHeight / 2 + hitGreatIcon.capHeight + hitGreatText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f * scale
            ) // 2.05, 2.95, 4

            val hitGoodIcon = TextLine.make("GOOD", Font(boldFont, 32f * scale))
            val hitGoodText = TextLine.make(usNumber.format(scoreDTO.statistics.count100), Font(semiBoldFont, 28f * scale))
            val hitGoodHeight = hitGoodIcon.capHeight + hitGoodText.capHeight + intervalBetweenHitIconAndText
            val hitGoodWidth = max(hitGoodIcon.width, hitGoodText.width)

            drawTextLineWithShadow(hitGoodIcon,
                (cos(PI / 2.0 + PI / 5.0 * 2.05) * 180f * scale - hitGoodWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.05) * 130f * scale - hitGoodHeight / 2 + hitGoodIcon.capHeight).toFloat(),
                paint.apply { color = Color.makeRGB(187, 254, 35) },
                2f * scale, shadowColor = Color.makeRGB(130, 177, 24)
            )
            drawTextLineWithShadow(hitGoodText,
                (cos(PI / 2.0 + PI / 5.0 * 2.05) * 180f * scale - hitGoodWidth / 2 + (hitGoodWidth - hitGoodText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.05) * 130f * scale - hitGoodHeight / 2 + hitGoodIcon.capHeight + hitGoodText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f * scale
            )

            val hitMehIcon = TextLine.make("MEH", Font(boldFont, 32f * scale))
            val hitMehText = TextLine.make(usNumber.format(scoreDTO.statistics.count50), Font(semiBoldFont, 28f * scale))
            val hitMehHeight = hitMehIcon.capHeight + hitMehText.capHeight + intervalBetweenHitIconAndText
            val hitMehWidth = max(hitMehIcon.width, hitMehText.width)

            drawTextLineWithShadow(hitMehIcon,
                (cos(PI / 2.0 + PI / 5.0 * 2.95) * 180f * scale - hitMehWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.95) * 130f * scale - hitMehHeight / 2 + hitMehIcon.capHeight).toFloat(),
                paint.apply { color = Color.makeRGB(244, 196, 40) },
                2f * scale, shadowColor = Color.makeRGB(167, 134, 27)
            )
            drawTextLineWithShadow(hitMehText,
                (cos(PI / 2.0 + PI / 5.0 * 2.95) * 180f * scale - hitMehWidth / 2 + (hitMehWidth - hitMehText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.95) * 130f * scale - hitMehHeight / 2 + hitMehIcon.capHeight + hitMehText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f * scale
            )

            val hitMissIcon = TextLine.make("MISS", Font(boldFont, 32f * scale))
            val hitMissText = TextLine.make(usNumber.format(scoreDTO.statistics.countMiss), Font(semiBoldFont, 28f * scale))
            val hitMissHeight = hitMissIcon.capHeight + hitMissText.capHeight + intervalBetweenHitIconAndText
            val hitMissWidth = max(hitMissIcon.width, hitMissText.width)

            drawTextLineWithShadow(hitMissIcon,
                (cos(PI / 2.0 + PI / 5.0 * 4) * 180f * scale - hitMissWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 4) * 130f * scale - hitMissHeight / 2 + hitMissIcon.capHeight).toFloat(),
                paint.apply { color = Color.makeRGB(207, 69, 71) },
                2f * scale, shadowColor = Color.makeRGB(130, 43, 44)
            )
            drawTextLineWithShadow(hitMissText,
                (cos(PI / 2.0 + PI / 5.0 * 4) * 180f * scale - hitMissWidth / 2 + (hitMissWidth - hitMissText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 4) * 130f * scale - hitMissHeight / 2 + hitMissIcon.capHeight + hitMissText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f * scale
            )

            restore()
            translate(xWidth * 0.4f + rankBGRadius + 20f * scale, 0f)
            val otherScoreInfoWidth = xWidth - (xWidth * 0.4f + rankBGRadius + 20f * scale) - 20f * scale

            //accuracy, score and max combo...
            val scoreText = TextLine.make("Score", Font(semiBoldFont, 28f * scale))
            val accuracyText = TextLine.make("Accuracy", Font(semiBoldFont, 28f * scale))
            val maxComboText = TextLine.make("Max Combo", Font(semiBoldFont, 28f * scale))

            val score = TextLine.make(usNumber.format(scoreDTO.score), Font(boldFont, 28f * scale))
            val accuracy = TextLine.make(format2DFix.format(scoreDTO.accuracy * 100.0) + "%", Font(boldFont, 28f * scale))
            val maxCombo = TextLine.make(usNumber.format(scoreDTO.maxCombo), Font(boldFont, 28f * scale))
            val perfectCombo = TextLine.make(" / " + (attribute.ifRight { usNumber.format(it.maxCombo) } ?: "-"), Font(boldFont, 28f * scale))


            val totalHeight = scoreText.capHeight + accuracyText.capHeight + maxComboText.capHeight +
                    score.capHeight + accuracy.capHeight + maxCombo.capHeight + (45f + 70f) * scale // 45f = 3 * 15f, 90f = 2 * 45f

            translate(0f, (xHeight - totalHeight) / 2)

            val otherInfoTextSavePoint = save()
            drawTextLineWithShadow(scoreText,
                (otherScoreInfoWidth - scoreText.width) / 2,
                scoreText.capHeight,
                paint.setColor(colorYellow)
            )
            translate(0f, scoreText.capHeight + 15f * scale + score.capHeight + 35f * scale)
            drawTextLineWithShadow(accuracyText,
                (otherScoreInfoWidth - accuracyText.width) / 2,
                accuracyText.capHeight,
                paint.setColor(colorYellow)
            )
            translate(0f, accuracyText.capHeight + 15f * scale + accuracy.capHeight + 35f * scale)
            drawTextLineWithShadow(maxComboText,
                (otherScoreInfoWidth - maxComboText.width) / 2,
                maxComboText.capHeight,
                paint.setColor(colorYellow)
            )
            restoreToCount(otherInfoTextSavePoint)

            if(userBestScore.isRight
                && userBestScore.right.score.id != scoreDTO.id
                && userBestScore.right.score.run {
                    scoreDTO.score != this.score
                        && scoreDTO.accuracy != this.accuracy
                        && scoreDTO.maxCombo != this.maxCombo
                }){
                val unwrapped = userBestScore.right.score

                val scoreDiff = scoreDTO.score - unwrapped.score
                val bestScore = TextLine.make(" (${scoreDiff.run { if(this > 0) "+${usNumber.format(this)}" else usNumber.format(this) }})", Font(semiBoldFont, 18f * scale))
                val accuracyDiff = (scoreDTO.accuracy - unwrapped.accuracy) * 100.0
                val bestAccuracy = TextLine.make(" (${if(accuracyDiff > 0) "+" else ""}${format2DFix.format(accuracyDiff)}%)", Font(semiBoldFont, 18f * scale))
                val maxComboDiff = scoreDTO.maxCombo - unwrapped.maxCombo
                val bestMaxCombo = TextLine.make(" (${maxComboDiff.run { if(this > 0) "+${usNumber.format(this)}" else usNumber.format(this) }})", Font(semiBoldFont, 18f * scale))

                val scoreWidth = score.width + bestScore.width
                val accuracyWidth = accuracy.width + bestAccuracy.width
                val maxComboWidth = maxCombo.width + bestMaxCombo.width + perfectCombo.width

                drawTextLineWithShadow(score,
                    (otherScoreInfoWidth - scoreWidth) / 2,
                    scoreText.capHeight + 15f * scale + score.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestScore,
                    (otherScoreInfoWidth - scoreWidth) / 2 + score.width,
                    scoreText.capHeight + 15f * scale + score.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                translate(0f, scoreText.capHeight + 15f * scale + score.capHeight + 35f * scale)
                drawTextLineWithShadow(accuracy,
                    (otherScoreInfoWidth - accuracyWidth) / 2,
                    accuracyText.capHeight + 15f * scale + accuracy.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestAccuracy,
                    (otherScoreInfoWidth - accuracyWidth) / 2 + accuracy.width,
                    accuracyText.capHeight + 15f * scale + accuracy.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                translate(0f, accuracyText.capHeight + 15f * scale + accuracy.capHeight + 35f * scale)
                drawTextLineWithShadow(maxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2,
                    maxComboText.capHeight + 15f * scale + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestMaxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width,
                    maxComboText.capHeight + 15f * scale + maxCombo.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                drawTextLineWithShadow(perfectCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width + bestMaxCombo.width,
                    maxComboText.capHeight + 15f * scale + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
            } else {
                drawTextLineWithShadow(score,
                    (otherScoreInfoWidth - score.width) / 2,
                    scoreText.capHeight + 15f * scale + score.capHeight,
                    paint.setColor(colorWhite)
                )
                translate(0f, scoreText.capHeight + 15f * scale + score.capHeight + 35f * scale)
                drawTextLineWithShadow(accuracy,
                    (otherScoreInfoWidth - accuracy.width) / 2,
                    accuracyText.capHeight + 15f * scale + accuracy.capHeight,
                    paint.setColor(if(scoreDTO.accuracy == 1.0) colorGreen else colorWhite)
                )
                translate(0f, accuracyText.capHeight + 15f * scale + accuracy.capHeight + 35f * scale)
                val maxComboWidth = maxCombo.width + perfectCombo.width
                drawTextLineWithShadow(maxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2,
                    maxComboText.capHeight + 15f * scale + maxCombo.capHeight,
                    paint.setColor(attribute.ifRight {
                        if(scoreDTO.maxCombo == it.maxCombo) colorGreen else colorWhite
                    } ?: colorWhite)
                )
                drawTextLineWithShadow(perfectCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width,
                    maxComboText.capHeight + 15f * scale + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
            }

            restoreToCount(scoreInfoSavePoint)

            translate(cardWidth / 2, 0f)
            val graphCardWidth = (cardWidth / 2 - 25f * 2 * scale - (20f + 15f) * scale) / 2
            val graphCardHeight = scoreInfoHeight - 25f * 2 * scale - 20f * 2 * scale - scaledPlayerAvatarImage.height

            //pp+ and pp graph background
            translate(25f * scale, 25f * scale)

            if(skillAttributes.isRight) {
                val unwrapped = skillAttributes.right
                drawPPPlusGraph(
                    graphCardWidth, graphCardHeight,
                    unwrapped.jumpAimStrain, unwrapped.flowAimStrain, unwrapped.speedStrain,
                    unwrapped.staminaStrain, unwrapped.precisionStrain, unwrapped.accuracyStrain,
                    transparent40PercentBlack, colorWhite, colorYellow, colorGray, paint, scale
                )
            } else {
                val unavailable1 = TextLine.make("Strain skill analysis", Font(semiBoldFont, 28f * scale))
                val unavailable2 = TextLine.make("is unavailable", Font(semiBoldFont, 28f * scale))
                val width = max(unavailable1.width, unavailable2.width)
                val height = unavailable1.capHeight + unavailable2.capHeight + 10f * scale
                drawTextLineWithShadow(unavailable1,
                    (graphCardWidth - width) / 2 + (width - unavailable1.width) / 2,
                    graphCardHeight / 2 - (height - unavailable1.height) / 2,
                    paint.setColor(colorGray), 1f * scale
                )
                drawTextLineWithShadow(unavailable2,
                    (graphCardWidth - width) / 2 + (width - unavailable2.width) / 2,
                    graphCardHeight / 2 - (height - unavailable2.height) / 2 + 10f * scale + unavailable2.capHeight,
                    paint.setColor(colorGray), 1f * scale
                )
            }

            translate(graphCardWidth + 20f * scale, 0f)

            if(ppCurvePoints.first.isNotEmpty() && ppCurvePoints.second.isNotEmpty()) {
                drawPpCurveGraph(
                    graphCardWidth, graphCardHeight,
                    ppCurvePoints.first, ppCurvePoints.second,
                    scoreDTO.pp ?: ppCurvePoints.first.first().second, scoreDTO.accuracy,
                    transparent40PercentBlack, colorWhite, colorGray2, colorGray,
                    colorWhite, colorYellow, colorYellow, colorGreen, paint, scale
                )
            } else {
                val unavailable1 = TextLine.make("PP curve analysis", Font(semiBoldFont, 28f * scale))
                val unavailable2 = TextLine.make("is unavailable", Font(semiBoldFont, 28f * scale))
                val width = max(unavailable1.width, unavailable2.width)
                val height = unavailable1.capHeight + unavailable2.capHeight + 10f * scale
                drawTextLineWithShadow(unavailable1,
                    (graphCardWidth - width) / 2 + (width - unavailable1.width) / 2,
                    graphCardHeight / 2 - (height - unavailable1.height) / 2,
                    paint.setColor(colorGray), 1f * scale
                )
                drawTextLineWithShadow(unavailable2,
                    (graphCardWidth - width) / 2 + (width - unavailable2.width) / 2,
                    graphCardHeight / 2 - (height - unavailable2.height) / 2 + 10f * scale + unavailable2.capHeight,
                    paint.setColor(colorGray), 1f * scale
                )
            }

            restoreToCount(baseSavePoint)

            replayAnalyzer.ifRight { rep ->
                translate(cardWidth, 0f)
                //background color
                drawRect(Rect.makeXYWH(0f, 0f, replayDetailWidth, cardHeight), paint.apply {
                    color = scoreInfoBackgroundColor
                    mode = PaintMode.FILL
                })
                //duplicate
                drawRect(Rect.makeXYWH(0f, 0f, replayDetailWidth, cardHeight), paint.apply {
                    color = transparent40PercentBlack
                    mode = PaintMode.FILL
                })
                val framePadding = 30f * scale
                drawRRect(
                    RRect.makeXYWH(
                        framePadding, framePadding,
                        replayDetailWidth - 2 * framePadding,
                        cardHeight - 2 * framePadding, 16f * scale
                    ), paint.apply {
                        color = colorGray
                        mode = PaintMode.STROKE
                        strokeWidth = 2f * scale
                    }
                )
                val replayDetailText = TextLine.make("Replay Detail", Font(semiBoldFont, 18f * scale))
                drawRect(
                    Rect.makeXYWH(
                        framePadding + 20f * scale, framePadding - replayDetailText.capHeight / 2,
                        replayDetailText.width + 2 * 5f, replayDetailText.capHeight
                    ), paint.apply {
                        color = scoreInfoBackgroundColor
                        mode = PaintMode.FILL
                    }
                )
                //duplicate
                drawRect(
                    Rect.makeXYWH(
                        framePadding + 20f * scale, framePadding - replayDetailText.capHeight / 2,
                        replayDetailText.width + 2 * 5f, replayDetailText.capHeight
                    ), paint.apply {
                        color = transparent40PercentBlack
                        mode = PaintMode.FILL
                    }
                )
                drawTextLine(replayDetailText,
                    framePadding + (20f + 5f) * scale, framePadding + replayDetailText.capHeight / 2,
                    paint.apply {
                        color = colorWhite
                        strokeWidth = 1f * scale
                    }
                )
                val contentPadding = 15f * scale
                translate(contentPadding + framePadding, contentPadding + framePadding + 10f * scale)
                val contentWidth = replayDetailWidth - framePadding * 2 - contentPadding * 2

                // timing distribution column graph
                val timingDistributionText = TextLine.make("Timing Distribution", Font(semiBoldFont, 16f * scale))
                drawLine(3f * scale, 0f, 3f * scale, timingDistributionText.capHeight, paint.apply {
                    color = replayItemTitleIconColor
                    strokeWidth = 6f * scale
                })
                drawTextLine(timingDistributionText, (6f + 9f) * scale, timingDistributionText.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f * scale
                })
                translate(0f, timingDistributionText.capHeight + 35f * scale)

                val (barHeight, barWidth) = 120f * scale to 3f * scale
                val groupedTiming = Array(200 / 5 + 1) { 0 }.also { arr ->
                    rep.timingDistributions.forEach { time ->
                        if(time < -100.0) {
                            arr[0] = arr[0].plus(1)
                        } else if(time > 100.0) {
                            arr[arr.lastIndex] = arr[arr.lastIndex].plus(1)
                        } else {
                            val idx = round((time + 100) / 5.0).toInt()
                            arr[idx] = arr[idx].plus(1)
                        }
                    }
                }.reversed()
                val eachPaddingWidth = (contentWidth - barWidth * groupedTiming.size) / (groupedTiming.size - 1)
                val maxTimingHitCount = groupedTiming.maxOf { it }
                var startTiming = -100
                groupedTiming.foldRightIndexed(0f) { idx, cnt, acc ->
                    drawLine(
                        acc + barWidth / 2, barHeight,
                        acc + barWidth / 2, (1.0 - 1.0 * cnt / maxTimingHitCount).toFloat() * barHeight,
                        paint.apply {
                            color = colorWhite
                            strokeWidth = barWidth
                        })
                    if(idx % 4 == 0) {
                        val text = TextLine.make(startTiming.toString(), Font(semiBoldFont, 10f * scale))
                        drawTextLine(text, acc + barWidth / 2 - text.width / 2, barHeight + 10f * scale + text.capHeight, paint.apply {
                            color = colorGray
                            strokeWidth = 1f * scale
                        })
                        startTiming += 20
                    }
                    acc + barWidth + eachPaddingWidth
                }

                translate(0f, barHeight + (10f + 35f) * scale)

                val averageHitTimeOffset = TextLine.make("Average Offset: ${format2DFix.format(rep.averageHitTimeOffset)} ms", Font(semiBoldFont, 14f * scale))
                drawTextLine(averageHitTimeOffset, 9f * scale, averageHitTimeOffset.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f * scale
                })
                translate(0f, averageHitTimeOffset.capHeight + 15f * scale)

                val unstableRate = TextLine.make("Unstable Rate: ${format2DFix.format(rep.unstableRate)}", Font(semiBoldFont, 14f * scale))
                drawTextLine(unstableRate, 9f * scale, unstableRate.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f * scale
                })

                translate(0f, 45f * scale)

                // accuracy heatmap round graph
                val accuracyHeatmapText = TextLine.make("Accuracy Heatmap", Font(semiBoldFont, 16f * scale))
                drawLine(3f * scale, 0f, 3f * scale, accuracyHeatmapText.capHeight, paint.apply {
                    color = replayItemTitleIconColor
                    strokeWidth = 6f * scale
                })
                drawTextLine(accuracyHeatmapText, (6f + 9f) * scale, accuracyHeatmapText.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f * scale
                })
                translate(0f, accuracyHeatmapText.capHeight + 35f * scale)

                val dotDensity = 25
                val heatmapMatrix = Array(dotDensity) { Array(dotDensity) { 0 } }
                rep.hits.map { h -> h.hitPointPercentage }.forEach {
                    heatmapMatrix[
                            max(0, min(round(it.first * dotDensity).toInt(), dotDensity - 1))
                    ][
                            max(0, min(round(it.second * dotDensity).toInt(), dotDensity - 1))
                    ] ++
                }
                val maxHeatmapDotHitCount = heatmapMatrix.maxOf { w -> w.maxOf { h -> h } }

                val heatmapCircleRadius = 90f * scale
                drawCircle(contentWidth / 2, heatmapCircleRadius, heatmapCircleRadius, paint.apply {
                    color = colorGray
                    mode = PaintMode.STROKE
                    strokeWidth = 2f * scale
                })
                save()
                val dotRadius = 3f * scale
                translate(contentWidth / 2 - heatmapCircleRadius, 0f)
                val dotPaddingWidth = (2 * heatmapCircleRadius - 2 * dotRadius * dotDensity) / (dotDensity - 1)
                heatmapMatrix.foldRight(0f) { wCnt, wAcc ->
                    wCnt.foldRight(0f) { hCnt, hAcc ->
                        drawPoint(wAcc + dotRadius, hAcc + dotRadius, paint.apply {
                            color = accuracyHeatmapDotColor((1.0 * hCnt / maxHeatmapDotHitCount).run {
                                if (this > 0.7) 1.0 else max(0.0, min(this / 0.7, 1.0))
                            })
                            strokeWidth = dotRadius * 2
                        })
                        hAcc + dotRadius * 2 + dotPaddingWidth
                    }
                    wAcc + dotRadius * 2 + dotPaddingWidth
                }
                restore()
                translate(0f, heatmapCircleRadius * 2 + 35f * scale)

                val averagePrecision = TextLine.make("Precision: ${format2DFix.format(rep.averagePrecision * 100)}%", Font(semiBoldFont, 14f * scale))
                drawTextLine(averagePrecision, 9f, averagePrecision.capHeight, paint.apply {
                    color = colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f * scale
                })
            }
        }

        return surface
    }
}
