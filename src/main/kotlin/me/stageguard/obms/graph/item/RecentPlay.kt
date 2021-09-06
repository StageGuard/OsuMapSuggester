package me.stageguard.obms.graph.item

import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.graph.*
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.api.dto.BeatmapUserScoreDTO
import me.stageguard.obms.osu.api.dto.BeatmapsetDTO
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.replay.ReplayFrameAnalyzer
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.ValueOrISE
import me.stageguard.obms.utils.lerp
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.isRight
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import me.stageguard.obms.utils.InferredEitherOrISE
import org.jetbrains.skija.*
import java.lang.Exception
import java.util.*
import kotlin.math.*

object RecentPlay {
    private const val cardWidth = 1305f
    private const val cardHeight = 780f
    private const val songInfoHeight = 345f
    private const val scoreInfoHeight = cardHeight - songInfoHeight
    private const val songInfoPadding = 40f
    private const val songHeaderImageWidth = 370f
    private const val mapperAvatarEdgeLength = 60f
    private const val playerAvatarEdgeLength = 68f
    private const val difficultyPanelHeight = 70f
    private const val rankStatusHeight = 55f
    private const val beatmapAttributePanelWidth = 360f
    private const val beatmapAttributePanelHeight = 205f
    private const val replayDataHeight = 55f
    private const val replayDetailWidth = 300f

    private val songInfoShadowColor = Color.makeARGB(153, 34, 40, 42)
    private val scoreInfoBackgroundColor = Color.makeRGB(42, 34, 38)
    private val colorWhite = Color.makeRGB(255, 255, 255)
    private val colorGray = Color.makeARGB(100, 255, 255, 255)
    private val colorGray2 = Color.makeARGB(45, 255, 255, 255)
    private val colorBlack = Color.makeRGB(0, 0, 0)
    private val colorYellow = Color.makeRGB(255, 204, 34)
    private val colorPink = Color.makeRGB(255, 102, 171)
    private val colorGreen = Color.makeRGB(179, 255, 102)
    private val colorRed = Color.makeRGB(255, 98, 98)
    private val transparent40PercentBlack = Color.makeARGB(100, 14, 16, 17)
    private val grayLine = Color.makeRGB(70, 57, 63)
    private val ppColor = Color.makeRGB(255, 102, 171)
    private val ppTextColor = Color.makeRGB(209, 148, 175)
    private val replayItemTitleIconColor = Color.makeRGB(2, 247, 165)
    private fun accuracyHeatmapDotColor(a: Double) = Color.makeARGB((a * 255).toInt(), 114, 224, 193)

    private val defaultAvatarImage: ValueOrISE<Image>
        get() = try {
            InferredEitherOrISE(image("image/avatar_guest.png"))
        } catch (ex: Exception) {
            Either(IllegalStateException(ex))
        }

    private suspend fun getAvatarFromUrlOrDefault(url: String) =
        ImageCache.getImageAsSkijaImage(url).rightOrNull ?: defaultAvatarImage.rightOrNull ?:
            throw IllegalStateException("Cannot get avatar fom server and local: $url")


    suspend fun drawRecentPlayCard(
        scoreDTO: ScoreDTO, beatmapSet: BeatmapsetDTO, mods: ModCombination,
        attribute: ValueOrISE<DifficultyAttributes>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        skillAttributes: ValueOrISE<SkillAttributes>,
        userBestScore: ValueOrISE<BeatmapUserScoreDTO>,
        replayAnalyzer: ValueOrISE<ReplayFrameAnalyzer>
    ) : Surface {
        val playerAvatar = getAvatarFromUrlOrDefault(scoreDTO.user!!.avatarUrl)
        val songCover = ImageCache.getImageAsSkijaImage(beatmapSet.covers.cover2x)

        return drawRecentPlayCardImpl(
            scoreDTO, beatmapSet, mods, attribute, ppCurvePoints, skillAttributes,
            userBestScore, replayAnalyzer, playerAvatar, songCover
        )
    }

    @Suppress("DuplicatedCode")
    private fun drawRecentPlayCardImpl(
        scoreDTO: ScoreDTO, beatmapSet: BeatmapsetDTO, mods: ModCombination,
        attribute: ValueOrISE<DifficultyAttributes>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        skillAttributes: ValueOrISE<SkillAttributes>,
        userBestScore: ValueOrISE<BeatmapUserScoreDTO>,
        replayAnalyzer: ValueOrISE<ReplayFrameAnalyzer>,
        playerAvatar: Image, songCover: ValueOrISE<Image>
    ) : Surface {
        val surface = Surface.makeRasterN32Premul(
            (replayAnalyzer.ifRight { (cardWidth + replayDetailWidth).toInt() } ?: cardWidth).toInt(),
            cardHeight.toInt()
        )

        val paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.HIGH
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

            val songBid = TextLine.make("BID: ${scoreDTO.beatmap!!.id}", Font(semiBoldFont, 16f))
            drawRRect(RRect.makeXYWH(
                cardWidth - songBid.width - 20f, 0f, songBid.width + 20f, songBid.capHeight + 20f, 8f
            ), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })
            drawTextLine(songBid, cardWidth - songBid.width - 10f, songBid.capHeight + 10f, paint.apply {
                color = colorGray
                mode = PaintMode.FILL
                strokeWidth = 1f
            })

            //song basic info
            val songTitle = TextLine.make(kotlin.run {
                val title = beatmapSet.title
                if(title.length > 30) title.take(27).plus("...") else title
            }, Font(semiBoldFont, 42f))

            translate(songInfoPadding /*+ songHeaderImageWidth*/ + 20f, songInfoPadding + songTitle.capHeight + 14f)

            drawTextLineWithShadow(songTitle, 0f, 0f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            })
            val songArtist = TextLine.make(beatmapSet.artist, Font(semiBoldFont, 28f))
            drawTextLineWithShadow(songArtist, 3f, songTitle.capHeight + 15f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            })

            translate(0f, songTitle.capHeight + songArtist.capHeight + 27f)

            //mapper info
            val mapperAvatar = defaultAvatarImage
            mapperAvatar.onRight {
                val scaledMapperAvatar = it.scale(mapperAvatarEdgeLength / it.width, mapperAvatarEdgeLength / it.height)
                drawRoundCorneredImage(scaledMapperAvatar, 0f, 0f, 12f)
            }

            val mapperName = TextLine.make("mapped by ${beatmapSet.creator}", Font(regularFont, 20f))
            drawTextLineWithShadow(mapperName, mapperAvatarEdgeLength + 15f, mapperName.capHeight + 10f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            val beatmapsetCreateTime = TextLine.make("created at ${scoreDTO.beatmap.lastUpdated}", Font(regularFont, 20f))
            drawTextLineWithShadow(beatmapsetCreateTime, mapperAvatarEdgeLength + 15f,
                mapperName.capHeight + beatmapsetCreateTime.capHeight + 22f,
                paint.apply {
                    color = colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                }, 2f
            )

            translate(0f, mapperAvatarEdgeLength + 23f)

            //difficulty rating
            val actualDifficultyRating = attribute.ifRight {
                format2DFix.format(it.stars).toDouble()
            } ?: scoreDTO.beatmap.difficultyRating

            val versionText = TextLine.make(kotlin.run {
                val version = scoreDTO.beatmap.version
                if(version.length > 25) version.take(22).plus("...") else version
            }, Font(semiBoldFont, 22f))
            val starText = TextLine.make("Star Difficulty: $actualDifficultyRating", Font(
                semiBoldFont, 18f))

            drawRRect(
                RRect.makeXYWH(0f, 0f, 60f + max(versionText.width, starText.width) + 45f, difficultyPanelHeight, 90f),
                paint.apply {
                    mode = PaintMode.FILL
                    color = transparent40PercentBlack
                }
            )

            drawCircle(10f + 25f, 10f + 25f, 20f, paint.apply {
                mode = PaintMode.FILL
                color = colorWhite
            })
            drawCircle(10f + 25f, 10f + 25f, 14f, paint.apply {
                mode = PaintMode.STROKE
                color = difficultyColor(actualDifficultyRating)
                strokeWidth = 5f
            })

            drawTextLineWithShadow(versionText, difficultyPanelHeight, 17f + versionText.capHeight, paint.apply {
                mode = PaintMode.FILL
                color = colorWhite
            }, 1f)

            drawTextLineWithShadow(starText, difficultyPanelHeight,
                10f + versionText.capHeight + starText.capHeight + 17f
                , paint.apply {
                mode = PaintMode.FILL
                color = colorYellow
            }, 1f)

            restoreToCount(songInfoSavePoint)

            //rank status
            val rankStatus = TextLine.make(beatmapSet.status.uppercase(Locale.getDefault()), Font(boldFont, 28f))
            drawRRect(RRect.makeXYWH(
                cardWidth - songInfoPadding - rankStatus.width - 40f * 2, songInfoPadding,
                rankStatus.width + 40f * 2, rankStatusHeight, 90f
            ), paint.apply {
                mode = PaintMode.FILL
                color = transparent40PercentBlack
            })
            drawTextLine(rankStatus,
                cardWidth - songInfoPadding - rankStatus.width - 40f, songInfoPadding + rankStatusHeight / 2 + rankStatus.capHeight / 2,
                paint.apply {
                    color = colorGray
                }
            )

            save()
            translate(cardWidth - songInfoPadding - beatmapAttributePanelWidth, songInfoPadding + rankStatusHeight + 15f)

            //difficulty attributes
            drawRRect(RRect.makeXYWH(0f, 0f, beatmapAttributePanelWidth, beatmapAttributePanelHeight, 16f), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            val totalLengthIcon = svgDom("svg/total_length.svg").toScaledImage(0.07f)
            val totalLengthText = TextLine.make(parseTime(when {
                mods.dt() -> round(scoreDTO.beatmap.totalLength / 1.5).toInt()
                mods.ht() -> round(scoreDTO.beatmap.totalLength * 1.33).toInt()
                else -> scoreDTO.beatmap.totalLength
            }), Font(semiBoldFont, 18f))
            val bpmIcon = svgDom("svg/bpm.svg").toScaledImage(0.07f)
            val bpmText = TextLine.make(when {
                mods.dt() -> format1DFix.format(scoreDTO.beatmap.bpm * 1.5)
                mods.ht() -> format1DFix.format(scoreDTO.beatmap.bpm / 1.33)
                else -> format1DFix.format(scoreDTO.beatmap.bpm)
            }, Font(semiBoldFont, 18f))

            val totalLengthWidth = totalLengthIcon.width + 20f + totalLengthText.width
            val bpmWidth = bpmIcon.width + 20f + bpmText.width

            drawImage(totalLengthIcon, (beatmapAttributePanelWidth / 2 - totalLengthWidth) / 2, 12f)
            drawTextLineWithShadow(totalLengthText,
                (beatmapAttributePanelWidth / 2 - totalLengthWidth) / 2 + 20f + totalLengthIcon.width - 8f,
                13f + totalLengthIcon.height / 2 + totalLengthText.capHeight / 2, paint.apply {
                    color = if(mods.dt() || mods.ht()) colorYellow else colorWhite
                }, 1f
            )
            paint.color = colorWhite

            drawImage(bpmIcon, beatmapAttributePanelWidth / 2 + (beatmapAttributePanelWidth / 2 - bpmWidth) / 2 - 10f, 12f)
            drawTextLineWithShadow(bpmText,
                beatmapAttributePanelWidth / 2 + (beatmapAttributePanelWidth / 2 - bpmWidth) / 2 + 20f + totalLengthIcon.width - 10f,
                13f + bpmIcon.height / 2 + bpmText.capHeight / 2, paint.apply {
                    color = if(mods.dt() || mods.ht()) colorYellow else colorWhite
                }, 1f
            )
            paint.color = colorWhite

            translate(20f, totalLengthIcon.height + 12f * 2)

            val circleSizeText = TextLine.make("Circle Size", Font(regularFont, 18f))
            val hpDrainText = TextLine.make("HP Drain", Font(regularFont, 18f))
            val approachRateText = TextLine.make("Approach Rate", Font(regularFont, 18f))
            val overallDifficultyText = TextLine.make("Overall Difficulty", Font(regularFont, 18f))

            drawTextLineWithShadow(circleSizeText, 0f, circleSizeText.capHeight, paint, 1f)
            drawTextLineWithShadow(hpDrainText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + 13f, paint, 1f)
            drawTextLineWithShadow(approachRateText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + hpDrainText.capHeight + 26f, paint, 1f)
            drawTextLineWithShadow(overallDifficultyText, 0f, circleSizeText.capHeight + circleSizeText.capHeight + hpDrainText.capHeight + approachRateText.capHeight + 39f, paint, 1f)


            val circleSize = TextLine.make(attribute.ifRight {
                format1DFix.format(it.circleSize)
            } ?: scoreDTO.beatmap.cs.toString(), Font(boldFont, 18f))
            val hpDrain = TextLine.make(attribute.ifRight {
                format1DFix.format(it.hpDrain)
            } ?: scoreDTO.beatmap.drain.toString(), Font(boldFont, 18f))
            val approachRate = TextLine.make(attribute.ifRight {
                format1DFix.format(it.approachRate)
            } ?: scoreDTO.beatmap.ar.toString(), Font(boldFont, 18f))
            val overallDifficulty = TextLine.make(attribute.ifRight {
                format1DFix.format(it.overallDifficulty)
            } ?: scoreDTO.beatmap.accuracy.toString(), Font(boldFont, 18f))

            val maxAttributeWidth = max(max(circleSize.width, hpDrain.width), max(approachRate.width, overallDifficulty.width))

            drawTextLineWithShadow(circleSize, beatmapAttributePanelWidth - 20f * 2 - maxAttributeWidth, circleSize.capHeight, paint.apply {
                color = if(mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f)
            paint.color = colorWhite
            drawTextLineWithShadow(hpDrain, beatmapAttributePanelWidth - 20f * 2 - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + 13f, paint.apply {
                color = if(mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f)
            paint.color = colorWhite
            drawTextLineWithShadow(approachRate, beatmapAttributePanelWidth - 20f * 2 - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + 26f, paint.apply {
                color = if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f)
            paint.color = colorWhite
            drawTextLineWithShadow(overallDifficulty, beatmapAttributePanelWidth - 20f * 2 - maxAttributeWidth, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight + 39f, paint.apply {
                color = if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite
            }, 1f)
            paint.color = colorWhite

            val maxTextWidth = max(max(circleSizeText.width, hpDrainText.width), max(approachRateText.width, overallDifficultyText.width))

            val attributeBarLength = beatmapAttributePanelWidth - 40f - maxAttributeWidth - maxTextWidth - 10f - 10f //5f and 10f is translation offset

            translate(maxTextWidth + 10f, 0f)

            //circleSize bar
            drawLine(
                0f, circleSize.capHeight / 2,
                attributeBarLength, circleSize.capHeight / 2,
                paint.apply {
                    color = transparent40PercentBlack
                    strokeWidth = 8f
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
                0f, circleSize.capHeight + hpDrain.capHeight / 2 + 13f,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight / 2 + 13f,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight / 2 + 13f,
                attributeBarLength * ((attribute.ifRight { it.hpDrain } ?: scoreDTO.beatmap.drain) / 11.0).toFloat(), circleSize.capHeight + hpDrain.capHeight / 2 + 13f,
                paint.setColor(if(mods.hr() || mods.ez()) colorYellow else colorWhite)
            )
            //approachRate
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f,
                attributeBarLength * ((attribute.ifRight { it.approachRate } ?: scoreDTO.beatmap.ar) / 11.0).toFloat(),
                circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight / 2 + 26f,
                paint.setColor(if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite)
            )
            //overallDifficulty
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f,
                attributeBarLength, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f,
                paint.setColor(transparent40PercentBlack)
            )
            drawLine(
                0f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f,
                attributeBarLength * ((attribute.ifRight { it.overallDifficulty } ?: scoreDTO.beatmap.accuracy) / 11.0).toFloat(),
                circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight / 2 + 39f,
                paint.setColor(if(mods.isDoubleTimeOrHalfTime() || mods.hr() || mods.ez()) colorYellow else colorWhite)
            )

            translate(- maxTextWidth - 10f, circleSize.capHeight + hpDrain.capHeight + approachRate.capHeight + overallDifficulty.capHeight + 42f + 10f)

            var modXOffset = 0f
            mods.toList().map { m ->
                m.toString().lowercase(Locale.getDefault())
            }.forEach {
                val icon = image("image/mod_$it.png")
                drawImage(icon, modXOffset, 0f)
                modXOffset += icon.width + 3f
            }

            restore()
            translate(0f, songInfoHeight)

            //player info
            val scaledPlayerAvatarImage = playerAvatar.scale(playerAvatarEdgeLength / playerAvatar.width)
            val playerName = TextLine.make(scoreDTO.user!!.username, Font(semiBoldFont, 22f))
            val playTime = TextLine.make("played at ${scoreDTO.createdAt}", Font(semiBoldFont, 20f))

            drawRoundCorneredImage(scaledPlayerAvatarImage, 40f, 20f, 12f)
            drawTextLineWithShadow(playerName,
                40f + scaledPlayerAvatarImage.width + 20f,
                20f + playerName.capHeight + 13f, paint.apply {
                color = colorWhite
            }, 2f)
            drawTextLineWithShadow(playTime,
                40f + scaledPlayerAvatarImage.width + 20f,
                20f + playerName.capHeight + playTime.capHeight + 27f, paint.apply {
                color = colorWhite
            }, 2f)

            val replayAvailable = if(scoreDTO.replay) "Replay is available." else "Replay is unavailable."
            val replay = TextLine.make(replayAvailable, Font(semiBoldFont, 22f))

            drawRRect(RRect.makeXYWH(
                cardWidth - 40f - replay.width - 80f,
                (40f + scaledPlayerAvatarImage.height - replayDataHeight) / 2,
                replay.width + 80f, replayDataHeight, 90f
            ), paint.apply {
                mode = PaintMode.FILL
                color = transparent40PercentBlack
            })
            drawTextLine(replay, cardWidth - 40f - replay.width - 40f,
                (40f + scaledPlayerAvatarImage.height) / 2 + replay.capHeight / 2,
                paint.setColor(colorGray)
            )

            drawLine(
                32f, 40f + scaledPlayerAvatarImage.height,
                cardWidth - 32f, 40f + scaledPlayerAvatarImage.height, paint.apply {
                    color = colorPink
                    strokeWidth = 3f
                    strokeCap = PaintStrokeCap.ROUND
                }
            )

            translate(0f, 40f + scaledPlayerAvatarImage.height)
            val scoreInfoSavePoint = save()

            //middle gray line
            drawLine(
                cardWidth / 2, 20f, cardWidth / 2,
                scoreInfoHeight - 40f - scaledPlayerAvatarImage.height - 20f,
                paint.setColor(grayLine)
            )

            translate(20f, 20f)
            val xHeight = scoreInfoHeight - 40f - scaledPlayerAvatarImage.height - 40f
            val xWidth = (cardWidth - 40f) / 2

            //rank icon
            val rankBGRadius = 85f
            drawCircle(xWidth * 0.48f, xHeight / 2, rankBGRadius, paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            val scaledRankIcon = svgDom("svg/grade_${scoreDTO.rank.lowercase(Locale.getDefault())}.svg").toScaledImage(3.2f)
            drawImage(scaledRankIcon, xWidth * 0.48f - scaledRankIcon.width / 2, xHeight / 2 - scaledRankIcon.height / 2)

            //hit result
            save()
            translate(xWidth * 0.45f, xHeight / 2)

            val intervalBetweenHitIconAndText = 10f

            val hitGreatIcon = image("/image/hit_great.png")
            val hitGreatText = TextLine.make(scoreDTO.statistics.count300.toString(), Font(boldFont, 28f))
            val hitGreatHeight = hitGreatIcon.height + hitGreatText.capHeight + intervalBetweenHitIconAndText
            val hitGreatWidth = max(hitGreatIcon.width, hitGreatText.width.toInt())

            drawImage(hitGreatIcon,
                (cos(PI / 2.0 + PI / 5.0 * 1) * 180f - hitGreatWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 1) * 130f - hitGreatHeight / 2).toFloat()
            )
            drawTextLineWithShadow(hitGreatText,
                (cos(PI / 2.0 + PI / 5.0 * 1) * 180f - hitGreatWidth / 2 + (hitGreatWidth - hitGreatText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 1) * 130f - hitGreatHeight / 2 + hitGreatIcon.height + hitGreatText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f
            )

            val hitGoodIcon = image("/image/hit_good.png")
            val hitGoodText = TextLine.make(scoreDTO.statistics.count100.toString(), Font(boldFont, 28f))
            val hitGoodHeight = hitGoodIcon.height + hitGoodText.capHeight + intervalBetweenHitIconAndText
            val hitGoodWidth = max(hitGoodIcon.width, hitGoodText.width.toInt())

            drawImage(hitGoodIcon,
                (cos(PI / 2.0 + PI / 5.0 * 2.05) * 180f - hitGoodWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.05) * 130f - hitGoodHeight / 2).toFloat()
            )
            drawTextLineWithShadow(hitGoodText,
                (cos(PI / 2.0 + PI / 5.0 * 2.05) * 180f - hitGoodWidth / 2 + (hitGoodWidth - hitGoodText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.05) * 130f - hitGoodHeight / 2 + hitGoodIcon.height + hitGoodText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f
            )

            val hitMehIcon = image("/image/hit_meh.png")
            val hitMehText = TextLine.make(scoreDTO.statistics.count50.toString(), Font(boldFont, 28f))
            val hitMehHeight = hitMehIcon.height + hitMehText.capHeight + intervalBetweenHitIconAndText
            val hitMehWidth = max(hitMehIcon.width, hitMehText.width.toInt())

            drawImage(hitMehIcon,
                (cos(PI / 2.0 + PI / 5.0 * 2.95) * 180f - hitMehWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.95) * 130f - hitMehHeight / 2).toFloat()
            )
            drawTextLineWithShadow(hitMehText,
                (cos(PI / 2.0 + PI / 5.0 * 2.95) * 180f - hitMehWidth / 2 + (hitMehWidth - hitMehText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 2.95) * 130f - hitMehHeight / 2 + hitMehIcon.height + hitMehText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f
            )

            val hitMissIcon = image("/image/hit_miss.png")
            val hitMissText = TextLine.make(scoreDTO.statistics.countMiss.toString(), Font(boldFont, 28f))
            val hitMissHeight = hitMissIcon.height + hitMissText.capHeight + intervalBetweenHitIconAndText
            val hitMissWidth = max(hitMissIcon.width, hitMissText.width.toInt())

            drawImage(hitMissIcon,
                (cos(PI / 2.0 + PI / 5.0 * 4) * 180f - hitMissWidth / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 4) * 130f - hitMissHeight / 2).toFloat()
            )
            drawTextLineWithShadow(hitMissText,
                (cos(PI / 2.0 + PI / 5.0 * 4) * 180f - hitMissWidth / 2 + (hitMissWidth - hitMissText.width) / 2).toFloat(),
                (-sin(PI / 2.0 + PI / 5.0 * 4) * 130f - hitMissHeight / 2 + hitMissIcon.height + hitMissText.capHeight + intervalBetweenHitIconAndText).toFloat(),
                paint.setColor(colorWhite), 3f
            )

            restore()
            translate(xWidth * 0.4f + rankBGRadius + 20f, 0f)
            val otherScoreInfoWidth = xWidth - (xWidth * 0.4f + rankBGRadius + 20f) - 20f

            //accuracy, score and max combo...
            val scoreText = TextLine.make("Score", Font(semiBoldFont, 28f))
            val accuracyText = TextLine.make("Accuracy", Font(semiBoldFont, 28f))
            val maxComboText = TextLine.make("Max Combo", Font(semiBoldFont, 28f))

            val score = TextLine.make(scoreDTO.score.toString(), Font(boldFont, 28f))
            val accuracy = TextLine.make(format2DFix.format(scoreDTO.accuracy * 100.0) + "%", Font(boldFont, 28f))
            val maxCombo = TextLine.make(scoreDTO.maxCombo.toString(), Font(boldFont, 28f))
            val perfectCombo = TextLine.make(attribute.ifRight { " / ${it.maxCombo}" } ?: " / -", Font(boldFont, 28f))



            val totalHeight = scoreText.capHeight + accuracyText.capHeight + maxComboText.capHeight +
                    score.capHeight + accuracy.capHeight + maxCombo.capHeight + 45f + 70f // 45f = 3 * 15f, 90f = 2 * 45f

            translate(0f, (xHeight - totalHeight) / 2)

            val otherInfoTextSavePoint = save()
            drawTextLineWithShadow(scoreText,
                (otherScoreInfoWidth - scoreText.width) / 2,
                scoreText.capHeight,
                paint.setColor(colorYellow)
            )
            translate(0f, scoreText.capHeight + 15f + score.capHeight + 35f)
            drawTextLineWithShadow(accuracyText,
                (otherScoreInfoWidth - accuracyText.width) / 2,
                accuracyText.capHeight,
                paint.setColor(colorYellow)
            )
            translate(0f, accuracyText.capHeight + 15f + accuracy.capHeight + 35f)
            drawTextLineWithShadow(maxComboText,
                (otherScoreInfoWidth - maxComboText.width) / 2,
                maxComboText.capHeight,
                paint.setColor(colorYellow)
            )
            restoreToCount(otherInfoTextSavePoint)

            if(userBestScore.isRight && userBestScore.right.score.id != scoreDTO.id) {
                val unwrapped = userBestScore.right.score

                val scoreDiff = scoreDTO.score - unwrapped.score
                val bestScore = TextLine.make(" (${scoreDiff.run { if(this > 0) "+$this" else this.toString() }})", Font(semiBoldFont, 18f))
                val accuracyDiff = (scoreDTO.accuracy - unwrapped.accuracy) * 100.0
                val bestAccuracy = TextLine.make(" (${if(accuracyDiff > 0) "+" else ""}${format2DFix.format(accuracyDiff)}%)", Font(semiBoldFont, 18f))
                val maxComboDiff = scoreDTO.maxCombo - unwrapped.maxCombo
                val bestMaxCombo = TextLine.make(" (${maxComboDiff.run { if(this > 0) "+$this" else this.toString() }})", Font(semiBoldFont, 18f))

                val scoreWidth = score.width + bestScore.width
                val accuracyWidth = accuracy.width + bestAccuracy.width
                val maxComboWidth = maxCombo.width + bestMaxCombo.width + perfectCombo.width

                drawTextLineWithShadow(score,
                    (otherScoreInfoWidth - scoreWidth) / 2,
                    scoreText.capHeight + 15f + score.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestScore,
                    (otherScoreInfoWidth - scoreWidth) / 2 + score.width,
                    scoreText.capHeight + 15f + score.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                translate(0f, scoreText.capHeight + 15f + score.capHeight + 35f)
                drawTextLineWithShadow(accuracy,
                    (otherScoreInfoWidth - accuracyWidth) / 2,
                    accuracyText.capHeight + 15f + accuracy.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestAccuracy,
                    (otherScoreInfoWidth - accuracyWidth) / 2 + accuracy.width,
                    accuracyText.capHeight + 15f + accuracy.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                translate(0f, accuracyText.capHeight + 15f + accuracy.capHeight + 35f)
                drawTextLineWithShadow(maxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
                drawTextLineWithShadow(bestMaxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    if(scoreDiff > 0) paint.setColor(colorGreen) else paint.setColor(colorRed)
                )
                drawTextLineWithShadow(perfectCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width + bestMaxCombo.width,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
            } else {
                drawTextLineWithShadow(score,
                    (otherScoreInfoWidth - score.width) / 2,
                    scoreText.capHeight + 15f + score.capHeight,
                    paint.setColor(colorWhite)
                )
                translate(0f, scoreText.capHeight + 15f + score.capHeight + 35f)
                drawTextLineWithShadow(accuracy,
                    (otherScoreInfoWidth - accuracy.width) / 2,
                    accuracyText.capHeight + 15f + accuracy.capHeight,
                    paint.setColor(if(scoreDTO.accuracy == 1.0) colorGreen else colorWhite)
                )
                translate(0f, accuracyText.capHeight + 15f + accuracy.capHeight + 35f)
                val maxComboWidth = maxCombo.width + perfectCombo.width
                drawTextLineWithShadow(maxCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    paint.setColor(attribute.ifRight {
                        if(scoreDTO.maxCombo == it.maxCombo) colorGreen else colorWhite
                    } ?: colorWhite)
                )
                drawTextLineWithShadow(perfectCombo,
                    (otherScoreInfoWidth - maxComboWidth) / 2 + maxCombo.width,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    paint.setColor(colorWhite)
                )
            }

            restoreToCount(scoreInfoSavePoint)

            translate(cardWidth / 2, 0f)
            val graphCardWidth = (cardWidth / 2 - 25f * 2 - 20f - 15f) / 2
            val graphCardHeight = scoreInfoHeight - 25f * 2 - 20f * 2 - scaledPlayerAvatarImage.height

            //pp+ and pp graph background
            drawRRect(RRect.makeXYWH(25f, 25f, graphCardWidth, graphCardHeight, 16f), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })
            drawRRect(RRect.makeXYWH(25f + 20f + graphCardWidth, 25f, graphCardWidth, graphCardHeight, 16f), paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            translate(25f, 25f)

            if(skillAttributes.isRight) {
                val unwrapped = skillAttributes.right

                val ppPlusGraphText = TextLine.make("Strain skill of the beatmap", Font(semiBoldFont, 18f))
                drawTextLine(ppPlusGraphText,
                    (graphCardWidth - ppPlusGraphText.width) / 2, graphCardHeight - 15f,
                    paint.setColor(colorWhite)
                )
                val graphCenterY = (graphCardHeight - 20f - ppPlusGraphText.capHeight) / 2
                val radius = 70f

                val totalSkill = unwrapped.run { jumpAimStrain + flowAimStrain + accuracyStrain + precisionStrain + staminaStrain + speedStrain }
                val skills = unwrapped.run { listOf(
                    "Jump" to jumpAimStrain,
                    "Flow" to flowAimStrain,
                    "Speed" to speedStrain,
                    "Stamina" to staminaStrain,
                    "Precision" to precisionStrain,
                    "Complexity" to accuracyStrain
                ) }

                var lastAngle = -90f

                drawCircle(graphCardWidth / 2, graphCenterY, radius, paint.setColor(colorWhite))

                skills.forEachIndexed { idx, it ->
                    val scaledRadius = (radius * (1 + 0.5 * (1.0 * it.second / totalSkill))).toFloat()
                    drawArc(
                        graphCardWidth / 2 - scaledRadius, graphCenterY - scaledRadius,
                        graphCardWidth / 2 + scaledRadius, graphCenterY + scaledRadius,
                        lastAngle, 360f * (it.second / totalSkill).toFloat() + 1f,
                        true, paint.apply {
                            color = lerpColor(colorYellow, colorGray, 1.0 * idx / skills.size)
                        }
                    )

                    lastAngle += 360f * (it.second / totalSkill).toFloat()

                    save()
                    translate(graphCardWidth / 2, graphCenterY)

                    val skillName = TextLine.make(it.first, Font(semiBoldFont, 18f))
                    val skillValue = TextLine.make(format1DFix.format(it.second), Font(semiBoldFont, 18f))
                    val skillWidth = max(skillName.width, skillValue.width)
                    val skillHeight = skillName.capHeight + 5f + skillValue.capHeight

                    val relativeToCoord = 90 - (lastAngle - 360f * (it.second / totalSkill).toFloat() / 2)

                    drawTextLineWithShadow(skillName,
                        (sin(relativeToCoord / 180 * PI) * radius * 1.4 - skillWidth / 2).toFloat() + (skillWidth - skillName.width) / 2,
                        (cos(relativeToCoord / 180 * PI) * radius * 1.4 - skillHeight / 2).toFloat() + skillName.capHeight,
                        paint.setColor(colorWhite), 1f
                    )
                    drawTextLineWithShadow(skillValue,
                        (sin(relativeToCoord / 180 * PI) * radius * 1.4 - skillWidth / 2).toFloat() + (skillWidth - skillValue.width) / 2,
                        (cos(relativeToCoord / 180 * PI) * radius * 1.4 - skillHeight / 2).toFloat() + skillName.capHeight + 5f + skillValue.capHeight,
                        paint.setColor(colorWhite), 1f
                    )

                    restore()
                }
            } else {
                val unavailable1 = TextLine.make("Strain skill analysis", Font(semiBoldFont, 28f))
                val unavailable2 = TextLine.make("is unavailable", Font(semiBoldFont, 28f))
                val width = max(unavailable1.width, unavailable2.width)
                val height = unavailable1.capHeight + unavailable2.capHeight + 10f
                drawTextLineWithShadow(unavailable1,
                    (graphCardWidth - width) / 2 + (width - unavailable1.width) / 2,
                    graphCardHeight / 2 - (height - unavailable1.height) / 2,
                    paint.setColor(colorGray), 1f
                )
                drawTextLineWithShadow(unavailable2,
                    (graphCardWidth - width) / 2 + (width - unavailable2.width) / 2,
                    graphCardHeight / 2 - (height - unavailable2.height) / 2 + 10f + unavailable2.capHeight,
                    paint.setColor(colorGray), 1f
                )
            }

            translate(graphCardWidth + 20f, 0f)

            if(ppCurvePoints.first.isNotEmpty() && ppCurvePoints.second.isNotEmpty()) {
                val lineRow = 8
                val ppCurveText = TextLine.make("PP curve", Font(semiBoldFont, 18f))
                drawTextLine(ppCurveText,
                    (graphCardWidth - ppCurveText.width) / 2, graphCardHeight - 15f,
                    paint.setColor(colorWhite)
                )

                val maxValue = ppCurvePoints.second.last().second
                val minValue = ppCurvePoints.first[2].second
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
                            color = colorGray2
                            strokeWidth = 2f
                        }
                    )
                    drawTextLine(it,
                        maxValueTextWidth - it.width,
                        -5f - maxAccuracyTextHeight -10f - scaledChartHeight / (lineRow + 1) * idx + it.capHeight / 2,
                        paint.setColor(colorGray)
                    )
                }
                accuracyValues.forEachIndexed { idx, it ->
                    drawLine(
                        5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx, -maxAccuracyTextHeight - 5f,
                        5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx, -maxAccuracyTextHeight - 5f - scaledChartHeight + 5f,
                        paint.apply {
                            color = colorGray2
                            strokeWidth = 2f
                        }
                    )
                    drawTextLine(it,
                        5f + 5f + maxValueTextWidth + 5f + 5f + scaledChartWidth / 11 * idx - it.width / 2,
                        0f, paint.setColor(colorGray)
                    )
                }
                translate(maxValueTextWidth + 20f, -maxAccuracyTextHeight - 15f)
                val actualChatWidth = scaledChartWidth - 22f
                val actualCharHeight = scaledChartHeight - 23f
                (1 until ppCurvePoints.second.size - 1).forEach {
                    //the first element of ppCurePoints.first is the pp of current score
                    drawLine(
                        ((ppCurvePoints.first[it].first - 90) / 10 * actualChatWidth).toFloat(),
                        (-((ppCurvePoints.first[it].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
                        ((ppCurvePoints.first[it + 1].first - 90) / 10 * actualChatWidth).toFloat(),
                        (-((ppCurvePoints.first[it + 1].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
                        paint.apply {
                            color = colorWhite
                            strokeWidth = 3f
                        }
                    )
                    //the first element of ppCurePoints.second is the pp of current score if full combo
                    drawLine(
                        ((ppCurvePoints.second[it].first - 90) / 10 * actualChatWidth).toFloat(),
                        (-((ppCurvePoints.second[it].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
                        ((ppCurvePoints.second[it + 1].first - 90) / 10 * actualChatWidth).toFloat(),
                        (-((ppCurvePoints.second[it + 1].second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat(),
                        paint.apply {
                            color = colorYellow
                            strokeWidth = 3f
                        }
                    )
                }

                val ifFullComboText = TextLine.make("if fc", Font(boldFont, 18f))
                drawTextLineWithShadow(ifFullComboText,
                    ((ppCurvePoints.second.last().first - 90) / 10 * actualChatWidth).toFloat() + 8f,
                    (-((ppCurvePoints.second.last().second - intervalValues.first()) / (intervalValues.last() - intervalValues.first())) * actualCharHeight).toFloat() + ifFullComboText.capHeight / 2,
                    paint.apply {
                        color = colorGreen
                    }, 2f
                )

                val actualPp = scoreDTO.pp ?: ppCurvePoints.first.first().second
                val ifFullComboPp = ppCurvePoints.second.first().second
                val ppText = TextLine.make("pp", Font(semiBoldFont, 18f))
                val actualPpValueText = TextLine.make(actualPp.toInt().toString(), Font(boldFont, 22f))
                val ifFullComboPpValueText = TextLine.make(ifFullComboPp.toInt().toString(), Font(boldFont, 22f))

                if(scoreDTO.accuracy * 100.0 < 91.0) {
                    //actual pp text
                    drawPoint(-5f, 0f, paint.apply {
                        color = colorYellow
                        strokeWidth = 5f
                    })
                    drawTextLineWithShadow(actualPpValueText, -5f + 5f, actualPpValueText.capHeight / 2, paint.apply {
                        color = ppColor
                        strokeWidth = 2f
                    }, 2f)
                    drawTextLineWithShadow(ppText, -5f + 5f + actualPpValueText.width, actualPpValueText.capHeight / 2, paint.apply {
                        color = ppTextColor
                        strokeWidth = 2f
                    }, 2f)
                    //if full combo pp text
                    val yCoord = ((ifFullComboPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
                    drawPoint(-5f, -yCoord, paint.apply {
                        color = colorYellow
                        strokeWidth = 7f
                    })
                    drawTextLineWithShadow(ifFullComboPpValueText, -5f + 5f, -5f - yCoord, paint.apply {
                        color = ppColor
                        strokeWidth = 2f
                    }, 2f)
                    drawTextLineWithShadow(ppText, -5f + 5f + ifFullComboPpValueText.width, -5f - yCoord, paint.apply {
                        color = ppTextColor
                        strokeWidth = 2f
                    }, 2f)
                } else {
                    //actual pp text
                    val xCoord = ((scoreDTO.accuracy * 100.0 - 90) / 10 * actualChatWidth).toFloat()
                    val yCoordActualPp = ((actualPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
                    val yCoordIfFullComboPp = ((ifFullComboPp - intervalValues.first()) / (intervalValues.last() - intervalValues.first()) * actualCharHeight).toFloat()
                    val textOffset = if(yCoordActualPp > ppText.capHeight) ppText.capHeight + 10f else 0f
                    // actual pp
                    drawPoint(xCoord, -yCoordActualPp, paint.apply {
                        color = colorYellow
                        strokeWidth = 5f
                    })
                    drawTextLineWithShadow(actualPpValueText, 5f + xCoord, -5f - yCoordActualPp + textOffset, paint.apply {
                        color = ppColor
                        strokeWidth = 2f
                    }, 2f)
                    drawTextLineWithShadow(ppText, 5f + xCoord + actualPpValueText.width, -5f - yCoordActualPp + textOffset, paint.apply {
                        color = ppTextColor
                        strokeWidth = 2f
                    }, 2f)
                    //if full combo pp text
                    drawPoint(xCoord, -yCoordIfFullComboPp, paint.apply {
                        color = colorGreen
                        strokeWidth = 7f
                    })
                    drawTextLineWithShadow(ifFullComboPpValueText, -5f + xCoord - ifFullComboPpValueText.width - ppText.width, -5f - yCoordIfFullComboPp, paint.apply {
                        color = ppColor
                        strokeWidth = 2f
                    }, 2f)
                    drawTextLineWithShadow(ppText, -5f + xCoord - ppText.width, -5f - yCoordIfFullComboPp, paint.apply {
                        color = ppTextColor
                        strokeWidth = 2f
                    }, 2f)
                }
            } else {
                val unavailable1 = TextLine.make("PP curve analysis", Font(semiBoldFont, 28f))
                val unavailable2 = TextLine.make("is unavailable", Font(semiBoldFont, 28f))
                val width = max(unavailable1.width, unavailable2.width)
                val height = unavailable1.capHeight + unavailable2.capHeight + 10f
                drawTextLineWithShadow(unavailable1,
                    (graphCardWidth - width) / 2 + (width - unavailable1.width) / 2,
                    graphCardHeight / 2 - (height - unavailable1.height) / 2,
                    paint.setColor(colorGray), 1f
                )
                drawTextLineWithShadow(unavailable2,
                    (graphCardWidth - width) / 2 + (width - unavailable2.width) / 2,
                    graphCardHeight / 2 - (height - unavailable2.height) / 2 + 10f + unavailable2.capHeight,
                    paint.setColor(colorGray), 1f
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
                val framePadding = 30f
                drawRRect(
                    RRect.makeXYWH(
                        framePadding, framePadding,
                        replayDetailWidth - 2 * framePadding,
                        cardHeight - 2 * framePadding, 16f
                    ), paint.apply {
                        color = colorGray
                        mode = PaintMode.STROKE
                        strokeWidth = 2f
                    }
                )
                val replayDetailText = TextLine.make("Replay Detail", Font(semiBoldFont, 18f))
                drawRect(
                    Rect.makeXYWH(
                        framePadding + 20f, framePadding - replayDetailText.capHeight / 2,
                        replayDetailText.width + 2 * 5f, replayDetailText.capHeight
                    ), paint.apply {
                        color = scoreInfoBackgroundColor
                        mode = PaintMode.FILL
                    }
                )
                //duplicate
                drawRect(
                    Rect.makeXYWH(
                        framePadding + 20f, framePadding - replayDetailText.capHeight / 2,
                        replayDetailText.width + 2 * 5f, replayDetailText.capHeight
                    ), paint.apply {
                        color = transparent40PercentBlack
                        mode = PaintMode.FILL
                    }
                )
                drawTextLine(replayDetailText,
                    framePadding + 20f + 5f, framePadding + replayDetailText.capHeight / 2,
                    paint.apply {
                        color = colorWhite
                        strokeWidth = 1f
                    }
                )
                val contentPadding = 15f
                translate(contentPadding + framePadding, contentPadding + framePadding + 10f)
                val contentWidth = replayDetailWidth - framePadding * 2 - contentPadding * 2

                // timing distribution column graph
                val timingDistributionText = TextLine.make("Timing Distribution", Font(semiBoldFont, 16f))
                drawLine(3f, 0f, 3f, timingDistributionText.capHeight, paint.apply {
                    color = replayItemTitleIconColor
                    strokeWidth = 6f
                })
                drawTextLine(timingDistributionText, 6f + 9f, timingDistributionText.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f
                })
                translate(0f, timingDistributionText.capHeight + 35f)

                val (barHeight, barWidth) = 120f to 3f
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
                        val text = TextLine.make(startTiming.toString(), Font(semiBoldFont, 10f))
                        drawTextLine(text, acc + barWidth / 2 - text.width / 2, barHeight + 10f + text.capHeight, paint.apply {
                            color = colorGray
                            strokeWidth = 1f
                        })
                        startTiming += 20
                    }
                    acc + barWidth + eachPaddingWidth
                }

                translate(0f, barHeight + 10f + 35f)

                val averageHitTimeOffset = TextLine.make("Average Offset: ${format2DFix.format(rep.averageHitTimeOffset)} ms", Font(semiBoldFont, 14f))
                drawTextLine(averageHitTimeOffset, 9f, averageHitTimeOffset.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f
                })
                translate(0f, averageHitTimeOffset.capHeight + 15f)

                val unstableRate = TextLine.make("Unstable Rate: ${format2DFix.format(rep.unstableRate)}", Font(semiBoldFont, 14f))
                drawTextLine(unstableRate, 9f, unstableRate.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f
                })

                translate(0f, 45f)

                // accuracy heatmap round graph
                val accuracyHeatmapText = TextLine.make("Accuracy Heatmap", Font(semiBoldFont, 16f))
                drawLine(3f, 0f, 3f, accuracyHeatmapText.capHeight, paint.apply {
                    color = replayItemTitleIconColor
                    strokeWidth = 6f
                })
                drawTextLine(accuracyHeatmapText, 6f + 9f, accuracyHeatmapText.capHeight, paint.apply {
                    color = colorWhite
                    strokeWidth = 1f
                })
                translate(0f, accuracyHeatmapText.capHeight + 35f)

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

                val heatmapCircleRadius = 90f
                drawCircle(contentWidth / 2, heatmapCircleRadius, heatmapCircleRadius, paint.apply {
                    color = colorGray
                    mode = PaintMode.STROKE
                    strokeWidth = 2f
                })
                save()
                val dotRadius = 3f
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
                translate(0f, heatmapCircleRadius * 2 + 35f)

                val averagePrecision = TextLine.make("Precision: ${format2DFix.format(rep.averagePrecision * 100)}%", Font(semiBoldFont, 14f))
                drawTextLine(averagePrecision, 9f, averagePrecision.capHeight, paint.apply {
                    color = colorWhite
                    mode = PaintMode.FILL
                    strokeWidth = 1f
                })
            }
        }

        return surface
    }

    private fun parseTime(second: Int) : String {
        val minute = (second / 60).run { if(this < 10) "0$this" else this.toString() }
        val remainSec = (second % 60).run { if(this < 10) "0$this" else this.toString() }
        return "${minute}:$remainSec"
    }

    private fun Canvas.drawTextLineWithShadow(
        textLine: TextLine, x: Float, y: Float, paint: Paint,
        dropShadowX: Float = 3f, dropShadowY: Float = dropShadowX, shadowColor: Int = colorBlack
    ) {
        val currentPaintColor = paint.color
        val currentPaintStrokeWidth = paint.strokeWidth
        val currentPaintMode = paint.mode
        drawTextLine(textLine, x + dropShadowX, y + dropShadowY, paint.apply {
            color = shadowColor
            mode = PaintMode.FILL
            strokeWidth = 1f
        })
        drawTextLine(textLine, x, y, paint.apply {
            color = currentPaintColor
            mode = PaintMode.FILL
            strokeWidth = 1f
        })
        paint.apply {
            strokeWidth = currentPaintStrokeWidth
            mode = currentPaintMode
        }
    }

    @Suppress("SameParameterValue")
    private fun lerpColor(src: Int, dst: Int, percentage: Double) =
        Color.makeRGB(
            lerp(Color.getR(src).toDouble(), Color.getR(dst).toDouble(), percentage).toInt(),
            lerp(Color.getG(src).toDouble(), Color.getG(dst).toDouble(), percentage).toInt(),
            lerp(Color.getB(src).toDouble(), Color.getB(dst).toDouble(), percentage).toInt()
        )

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
            else -> kotlin.run {
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
}