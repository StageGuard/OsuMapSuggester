package me.stageguard.obms.graph.item

import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.graph.*
import me.stageguard.obms.graph.item.RecentPlay.drawTextLineWithShadow
import me.stageguard.obms.osu.algorithm.`pp+`.PPPlusResult
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.BeatmapUserScoreDTO
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either
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

    private val songInfoShadowColor = Color.makeARGB(153, 34, 40, 42)
    private val scoreInfoBackgroundColor = Color.makeRGB(42, 34, 38)
    private val colorWhite = Color.makeRGB(255, 255, 255)
    private val colorGray = Color.makeARGB(100, 255, 255, 255)
    private val colorBlack = Color.makeRGB(0, 0, 0)
    private val colorYellow = Color.makeRGB(255, 204, 34)
    private val colorPink = Color.makeRGB(255, 102, 171)
    private val colorGreen = Color.makeRGB(179, 255, 102)
    private val colorRed = Color.makeRGB(255, 98, 98)
    private val transparent40PercentBlack = Color.makeARGB(100, 14, 16, 17)
    private val grayLine = Color.makeRGB(70, 57, 63)

    private val defaultAvatarImage: Result<Image>
        get() = try {
            Result.success(image("image/avatar_guest.png"))
        } catch (ex: Exception) {
            Result.failure(ex)
        }

    private suspend fun getAvatarFromUrlOrDefault(url: String) = ImageCache.getImageAsSkijaImage(url).getOrElse { oEx ->
        defaultAvatarImage.getOrElse {
            throw IllegalStateException("Cannot get avatar fom server and local: $oEx")
        }
    }


    suspend fun drawRecentPlayCard(
        scoreDTO: ScoreDTO, mods: ModCombination,
        attribute: Either<DifficultyAttributes, Exception>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        ppPlusData: Either<PPPlusResult, Exception>,
        userBestScore: Either<BeatmapUserScoreDTO, Exception>
    ) : Surface {
        val playerAvatar = getAvatarFromUrlOrDefault(scoreDTO.user!!.avatarUrl)
        val songCover = ImageCache.getImageAsSkijaImage(scoreDTO.beatmapset!!.covers.cover2x)
        val songHeadImage = ImageCache.getImageAsSkijaImage(scoreDTO.beatmapset.covers.list2x)

        return drawRecentPlayCardImpl(
            scoreDTO, mods, attribute, ppCurvePoints, ppPlusData, userBestScore,
            playerAvatar, songCover, songHeadImage,
        )
    }

    @Suppress("DuplicatedCode")
    private fun drawRecentPlayCardImpl(
        scoreDTO: ScoreDTO, mods: ModCombination,
        attribute: Either<DifficultyAttributes, Exception>,
        ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
        ppPlusData: Either<PPPlusResult, Exception>,
        userBestScore: Either<BeatmapUserScoreDTO, Exception>,
        playerAvatar: Image, songCover: Result<Image>, songHeadImage: Result<Image>
    ) : Surface {
        val surface = Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt())

        val paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.HIGH
        }

        surface.canvas.apply {
            //background
            songCover.onSuccess {
                drawImage(
                    it.scale(cardWidth / it.width.toFloat(), songInfoHeight / it.height.toFloat()),
                    0F, 0F
                )
            }.onFailure {
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
            songHeadImage.onSuccess {
                val scaledSongHeaderBase = it.scale(songHeaderImageWidth / it.width)
                val scaledSongHeader = scaledSongHeaderBase.cutCenter(
                    songHeaderImageWidth / scaledSongHeaderBase.width,
                    (songInfoHeight - 2 * songInfoPadding) / scaledSongHeaderBase.height
                )
                val songHeaderRRect = RRect.makeXYWH(songInfoPadding, songInfoPadding,
                    scaledSongHeader.width.toFloat(), scaledSongHeader.height.toFloat(), 16f
                )
                drawImage(scaledSongHeader, songInfoPadding, songInfoPadding)
                drawRRect(songHeaderRRect, paint.apply {
                    color = Color.makeARGB(80, 0, 0, 0)
                    mode = PaintMode.STROKE
                    strokeWidth = 5f
                })
            }

            val songInfoSavePoint = save()

            //song basic info
            val songTitle = TextLine.make(kotlin.run {
                val title = scoreDTO.beatmapset!!.title
                if(title.length > 30) title.take(27).plus("...") else title
            }, Font(semiBoldFont, 42f))

            translate(songInfoPadding + songHeaderImageWidth + 20f, songInfoPadding + songTitle.capHeight + 14f)

            drawTextLineWithShadow(songTitle, 0f, 0f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            })
            val songArtist = TextLine.make(scoreDTO.beatmapset!!.artist, Font(semiBoldFont, 28f))
            drawTextLineWithShadow(songArtist, 3f, songTitle.capHeight + 15f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            })

            translate(0f, songTitle.capHeight + songArtist.capHeight + 27f)

            //mapper info
            val mapperAvatar = defaultAvatarImage
            mapperAvatar.onSuccess {
                val scaledMapperAvatar = it.scale(mapperAvatarEdgeLength / it.width, mapperAvatarEdgeLength / it.height)
                drawImage(scaledMapperAvatar, 0f, 0f)
            }

            val mapperName = TextLine.make("mapped by ${scoreDTO.beatmapset.creator}", Font(regularFont, 20f))
            drawTextLineWithShadow(mapperName, mapperAvatarEdgeLength + 15f, mapperName.capHeight + 10f, paint.apply {
                color = colorWhite
                mode = PaintMode.FILL
                strokeWidth = 1f
            }, 2f)

            val beatmapsetCreateTime = TextLine.make("created at ${scoreDTO.beatmap!!.lastUpdated}", Font(regularFont, 20f))
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
            val actualDifficultyRating = when(attribute) {
                is Either.Left -> format2DFix.format(attribute.value.stars).toDouble()
                is Either.Right -> scoreDTO.beatmap.difficultyRating
            }
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
            val rankStatus = TextLine.make(scoreDTO.beatmapset.status.uppercase(Locale.getDefault()), Font(boldFont, 28f))
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


            val circleSize = TextLine.make(when(attribute) {
                is Either.Left -> format1DFix.format(attribute.value.circleSize)
                is Either.Right -> scoreDTO.beatmap.cs
            }.toString(), Font(boldFont, 18f))
            val hpDrain = TextLine.make(when(attribute) {
                is Either.Left -> format1DFix.format(attribute.value.hpDrain)
                is Either.Right -> scoreDTO.beatmap.drain
            }.toString(), Font(boldFont, 18f))
            val approachRate = TextLine.make(when(attribute) {
                is Either.Left -> format1DFix.format(attribute.value.approachRate)
                is Either.Right -> scoreDTO.beatmap.ar
            }.toString(), Font(boldFont, 18f))
            val overallDifficulty = TextLine.make(when(attribute) {
                is Either.Left -> format1DFix.format(attribute.value.overallDifficulty)
                is Either.Right -> scoreDTO.beatmap.accuracy
            }.toString(), Font(boldFont, 18f))

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
                attributeBarLength * ((if(attribute is Either.Left) attribute.value.circleSize else scoreDTO.beatmap.cs) / 11.0).toFloat(), circleSize.capHeight / 2,
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
                attributeBarLength * ((if(attribute is Either.Left) attribute.value.hpDrain else scoreDTO.beatmap.drain) / 11.0).toFloat(), circleSize.capHeight + hpDrain.capHeight / 2 + 13f,
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
                attributeBarLength * ((if(attribute is Either.Left) attribute.value.approachRate else scoreDTO.beatmap.ar) / 11.0).toFloat(),
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
                attributeBarLength * ((if(attribute is Either.Left) attribute.value.overallDifficulty else scoreDTO.beatmap.accuracy) / 11.0).toFloat(),
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
                modXOffset += (icon.width + 3f)
            }

            restore()
            translate(0f, songInfoHeight)

            //player info
            val scaledPlayerAvatarImage = playerAvatar.scale(playerAvatarEdgeLength / playerAvatar.width)
            val playerName = TextLine.make(scoreDTO.user!!.username, Font(semiBoldFont, 22f))
            val playTime = TextLine.make("played at ${scoreDTO.createdAt}", Font(semiBoldFont, 20f))

            drawImage(scaledPlayerAvatarImage, 40f, 20f)
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
            drawCircle(xWidth * 0.4f, xHeight / 2, rankBGRadius, paint.apply {
                color = transparent40PercentBlack
                mode = PaintMode.FILL
            })

            val scaledRankIcon = svgDom("svg/grade_${scoreDTO.rank.lowercase(Locale.getDefault())}.svg").toScaledImage(3.2f)
            drawImage(scaledRankIcon, xWidth * 0.4f - scaledRankIcon.width / 2, xHeight / 2 - scaledRankIcon.height / 2)

            //hit result
            save()
            translate(xWidth * 0.4f, xHeight / 2)

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
            val perfectCombo = TextLine.make(when(attribute) {
                is Either.Left -> " / ${attribute.value.maxCombo}"
                else -> " / -"
            }, Font(boldFont, 28f))

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

            if(userBestScore is Either.Left && userBestScore.value.score.createdAt != scoreDTO.createdAt) {
                val unwrapped = userBestScore.value.score

                val scoreDiff = scoreDTO.score - unwrapped.score
                val bestScore = TextLine.make(" ($scoreDiff)", Font(semiBoldFont, 18f))
                val accuracyDiff = (scoreDTO.accuracy - unwrapped.accuracy) * 100.0
                val bestAccuracy = TextLine.make(" (${format2DFix.format(accuracyDiff)}%)", Font(semiBoldFont, 18f))
                val maxComboDiff = scoreDTO.maxCombo - unwrapped.maxCombo
                val bestMaxCombo = TextLine.make(" ($maxComboDiff)", Font(semiBoldFont, 18f))

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
                drawTextLineWithShadow(maxCombo,
                    (otherScoreInfoWidth - maxCombo.width) / 2,
                    maxComboText.capHeight + 15f + maxCombo.capHeight,
                    paint.setColor(when(attribute) {
                        is Either.Left -> if(scoreDTO.maxCombo == attribute.value.maxCombo) colorGreen else colorWhite
                        else -> colorWhite
                    })
                )
            }

            restoreToCount(scoreInfoSavePoint)

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
                        color = Color.makeLerp(
                            mapping[it].second, mapping[it + 1].second,
                            ((value - mapping[it].first) / (mapping[it + 1].first - mapping[it].first)).toFloat()
                        )
                    }
                }
                color
            }
        }
    }
}