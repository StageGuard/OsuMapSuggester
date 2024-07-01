package me.stageguard.obms.graph.item

import io.github.humbleui.skija.*
import io.github.humbleui.skija.svg.SVGDOM
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import jakarta.annotation.Resource
import me.stageguard.obms.ImageReadException
import me.stageguard.obms.RefactoredException
import me.stageguard.obms.bot.route.PanelStyle
import me.stageguard.obms.bot.route.PanelType
import me.stageguard.obms.bot.route.PerformanceStructure
import me.stageguard.obms.bot.route.parseMods
import me.stageguard.obms.cache.ImageCache
import me.stageguard.obms.graph.*
import me.stageguard.obms.graph.common.drawModIcon
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.beatmap.ModType
import me.stageguard.obms.utils.CustomLocalDateTime
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

@Component
class ProfileDraw {
    @Resource
    private lateinit var imageCache: ImageCache

    val scale = 2.0f
    val cardWidth = 1920 * scale
    val cardHeight = 1080 * scale
    val outerCardPadding = 100 * scale
    val innerCardHorizontalPadding = 90 * scale
    val innerCardVerticalPadding = 60 * scale

    val transparent30PercentBlack = Color.makeARGB(77, 14, 16, 17)
    val colorWhite = Color.makeRGB(255, 255, 255)
    val colorPink = Color.makeRGB(255, 102, 171)
    val colorYellow = Color.makeRGB(255, 204, 34)
    val colorGreen = Color.makeRGB(179, 255, 102)
    val colorRed = Color.makeRGB(255, 98, 98)

    val defaultBannerUrlPattern = Regex("images\\/headers\\/profile-covers\\/(.+)", RegexOption.IGNORE_CASE)
    val gradeColor = mapOf(
        "xh" to Color.makeRGB(214, 225, 239),
        "x" to Color.makeRGB(255, 213, 94),
        "sh" to Color.makeRGB(214, 225, 239),
        "s" to Color.makeRGB(255, 213, 94),
        "a" to Color.makeRGB(133, 215, 28),
        "b" to Color.makeRGB(2, 216, 233),
        "c" to Color.makeRGB(241, 130, 82),
        "d" to Color.makeRGB(240, 82, 82)
    )

    private val defaultAvatarImage: OptionalValue<Image>
        get() = try {
            InferredOptionalValue(image("image/avatar_guest.png"))
        } catch (ex: Exception) {
            Either(ImageReadException("image/avatar_guest.png").suppress(ex))
        }

    suspend fun drawProfilePanel(
        profile: GetOwnDTO, style: PanelStyle,
        perfs: PerformanceStructure,
        firstBpScore: ScoreDTO, lastBpScore: ScoreDTO,
        newlyGainPp: Double, currentAverageBp: Double, lastAverageBp: Double,
        maxTweenDiff: Double, maxTweenDiffRank: Int, topMods: List<Pair<Mod, Int>>
    ): Surface {
        val playerAvatar = imageCache.getImageAsSkijaImage(profile.avatarUrl).rightOrNull
            ?: defaultAvatarImage.rightOrNull
            ?: throw IllegalStateException("Cannot get avatar fom server and local: ${profile.avatarUrl}")

        suspend fun getBannerImage(): Either<RefactoredException, Image> {
            val defaultAvatarMatchResult = defaultBannerUrlPattern.find(profile.coverUrl)
            return imageCache.getImageAsSkijaImage(
                profile.coverUrl,
                if (defaultAvatarMatchResult != null) {
                    "default_banner_" + defaultAvatarMatchResult.groupValues.last()
                } else null
            )
        }

        val playerBanner = if (style.customBgFile != null && style.customBgFile.run { exists() && isFile }) {
            try {
                Either<RefactoredException, Image>(style.customBgFile.inputStream().use { stream ->
                    Image.makeFromEncoded(stream.readAllBytes())
                })
            } catch (ex: Exception) { getBannerImage() }
        } else getBannerImage()
        val countryCharCode = profile.country.code.toCharArray()
            .joinToString("-") { (it.code + 127397).toString(16) }
        val countrySVG = imageCache.getSVGAsSkiaSVGDOM(
            "https://osu.ppy.sh/assets/images/flags/$countryCharCode.svg", countryCharCode
        )

        return drawProfilePanelImpl(
            profile, style, perfs, firstBpScore, lastBpScore,
            newlyGainPp, currentAverageBp, lastAverageBp,
            maxTweenDiff, maxTweenDiffRank, topMods,
            playerAvatar, playerBanner, countrySVG
        )
    }

    private fun drawProfilePanelImpl(
        profile: GetOwnDTO, style: PanelStyle,
        perfs: PerformanceStructure,
        firstBpScore: ScoreDTO, lastBpScore: ScoreDTO,
        newlyGainPp: Double, currentAverageBp: Double, lastAverageBp: Double,
        maxTweenDiff: Double, maxTweenDiffRank: Int, topMods: List<Pair<Mod, Int>>,
        playerAvatar: Image, playerBanner: OptionalValue<Image>, countrySVG: OptionalValue<SVGDOM>
    ): Surface {
        val surface = Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt())
        val paint = Paint().apply { isAntiAlias = true }

        surface.canvas.apply {
            val originalImage = playerBanner.ifRight { image ->
                val sizeRatio = cardHeight / cardWidth
                val imageRatio = image.height / image.width.toFloat()
                val scale = if (imageRatio >= sizeRatio) {
                    cardWidth / image.width
                } else {
                    cardHeight / image.height
                }

                val scaled = image.scale(if (imageRatio > sizeRatio) cardWidth / image.width else cardHeight / image.height)
                scaled.cutCenter(cardWidth / scaled.width, cardHeight / scaled.height)
            }

            val blurredImage = if (style.type != PanelType.NO_BLUR && originalImage != null) {
                val surf = Surface.makeRasterN32Premul(cardWidth.toInt(), cardHeight.toInt())
                surf.canvas.drawImage(originalImage, 0f, 0f, Paint().apply {
                    imageFilter = ImageFilter.makeBlur(
                        (style.blurRadius * scale).toFloat(),
                        (style.blurRadius * scale).toFloat(),
                        FilterTileMode.CLAMP
                    )
                })
                surf.makeImageSnapshot()
            } else null
            if (originalImage != null) {
                drawImage(
                    if (blurredImage != null && style.type == PanelType.BLUR_BACKGROUND) blurredImage else originalImage,
                    0f,
                    0f
                )
            }

            drawRect(Rect(0f, 0f, cardWidth, cardHeight), paint.apply {
                color = Color.makeARGB(min(255, max(round(style.backgroundAlpha * 255).toInt(), 0)), 0, 0, 0)
                mode = PaintMode.FILL
            })

            // time and help
            val timeStamp = TextLine.make(
                "${CustomLocalDateTime.of(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))}",
                Font(semiBoldFont, 24f * scale)
            )
            val helpText = TextLine.make(
                "For custom panel settings, send \".info help\".",
                Font(semiBoldFont, 14f * scale)
            )
            drawTextLineWithShadow(
                timeStamp,
                15f * scale,
                timeStamp.capHeight + 15f * scale,
                paint.setColor(colorWhite),
                4 * scale,
                shadowBlurRadius = 2f * scale
            )
            drawTextLine(
                helpText,
                cardWidth - helpText.width - 15 * scale,
                cardHeight - 15 * scale,
                paint.setColor(Color.withA(colorWhite, 100))
            )


            //general info card
            val generalInfoCardHeight = cardHeight - outerCardPadding * 2.0f
            val generalInfoCardWidth = 370 * scale
            val generalInfoCardSavePoint = save()

            if (style.type == PanelType.BLUR_ITEM && blurredImage != null) {
                val rect = RRect.makeXYWH(
                    outerCardPadding,
                    outerCardPadding,
                    generalInfoCardWidth, generalInfoCardHeight,
                    (style.blurRadius * scale).toFloat()
                )
                drawImageRect(blurredImage, rect, rect)
            }
            translate(outerCardPadding, outerCardPadding)
            drawGeneralInfoCard(
                generalInfoCardWidth, generalInfoCardHeight,
                playerAvatar, profile.username, countrySVG, profile.country.name,
                profile.statistics.level.current, profile.statistics.level.progress,
                profile.statistics.globalRank, profile.statistics.countryRank,
                profile.statistics.pp!!, newlyGainPp,
                profile.discord, profile.twitter, profile.website,
                paint, style,
            )
            restoreToCount(generalInfoCardSavePoint)

            //detail info card
            val detailInfoCardWidth = cardWidth - generalInfoCardWidth - outerCardPadding * 2 - innerCardHorizontalPadding
            val detailInfoCardHeight = 340f * scale
            val detailInfoCardSavePoint = save()

            if (style.type == PanelType.BLUR_ITEM && blurredImage != null) {
                val rect = RRect.makeXYWH(
                    outerCardPadding + generalInfoCardWidth + innerCardHorizontalPadding,
                    outerCardPadding,
                    detailInfoCardWidth, detailInfoCardHeight,
                    (style.blurRadius * scale).toFloat()
                )
                drawImageRect(blurredImage, rect, rect)
            }
            translate(outerCardPadding + generalInfoCardWidth + innerCardHorizontalPadding, outerCardPadding)
            drawDetailInfoCard(
                detailInfoCardWidth, detailInfoCardHeight,
                profile.statistics, profile.rankHistory, profile.statistics.gradeCounts,
                paint, style
            )
            restoreToCount(detailInfoCardSavePoint)

            // perf structure card
            val perfCardWidth = (detailInfoCardWidth - innerCardHorizontalPadding) / 2
            val perfCardHeight = generalInfoCardHeight - detailInfoCardHeight - innerCardVerticalPadding
            val perfCardSavePoint = save()

            if (style.type == PanelType.BLUR_ITEM && blurredImage != null) {
                val rect = RRect.makeXYWH(
                    outerCardPadding + generalInfoCardWidth + innerCardHorizontalPadding,
                    outerCardPadding + detailInfoCardHeight + innerCardVerticalPadding,
                    perfCardWidth, perfCardHeight,
                    (style.blurRadius * scale).toFloat()
                )
                drawImageRect(blurredImage, rect, rect)
            }
            translate(
                outerCardPadding + generalInfoCardWidth + innerCardHorizontalPadding,
                outerCardPadding + detailInfoCardHeight + innerCardVerticalPadding
            )
            drawPerformanceCard(perfCardWidth, perfCardHeight, profile.statistics.pp, perfs, paint, style)
            restoreToCount(perfCardSavePoint)

            // best perf card
            val bestPerfCardSavePoint = save()
            if (style.type == PanelType.BLUR_ITEM && blurredImage != null) {
                val rect = RRect.makeXYWH(
                    outerCardPadding + generalInfoCardWidth + perfCardWidth + innerCardHorizontalPadding * 2,
                    outerCardPadding + detailInfoCardHeight + innerCardVerticalPadding,
                    perfCardWidth, perfCardHeight,
                    (style.blurRadius * scale).toFloat()
                )
                drawImageRect(blurredImage, rect, rect)
            }
            translate(
                outerCardPadding + generalInfoCardWidth + perfCardWidth + innerCardHorizontalPadding * 2,
                outerCardPadding + detailInfoCardHeight + innerCardVerticalPadding
            )
            drawBestPerformanceCard(
                perfCardWidth, perfCardHeight,
                firstBpScore, lastBpScore, currentAverageBp, lastAverageBp,
                maxTweenDiff, maxTweenDiffRank, topMods,
                paint, style
            )
            restoreToCount(bestPerfCardSavePoint)
        }

        return surface
    }

    private fun Canvas.drawBestPerformanceCard(
        width: Float, height: Float,
        firstBpScore: ScoreDTO, lastBpScore: ScoreDTO,
        currentAverageBp: Double, lastAverageBp: Double,
        maxTweenDiff: Double, maxTweenDiffRank: Int,
        topMods: List<Pair<Mod, Int>>,
        paint: Paint, style: PanelStyle,
    ) {
        val contentPadding = 40f * scale
        val contentWidth = width - contentPadding * scale
        drawRRect(
            RRect.makeXYWH(0f, 0f, width, height, if (style.type == PanelType.BLUR_ITEM) 0f else 20f * scale),
            paint.apply {
                color = Color.makeARGB(min(255, max(round(style.cardBackgroundAlpha * 255).toInt(), 0)), 0, 0, 0)
                mode = PaintMode.FILL
            }
        )
        translate(contentPadding, contentPadding)

        val bestPerfTitleText = TextLine.make("Best Performance", Font(semiBoldFont, 32f * scale))
        drawTextLineWithShadow(
            bestPerfTitleText,
            0f,
            bestPerfTitleText.capHeight,
            paint.setColor(colorWhite),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        translate(0f, bestPerfTitleText.capHeight + contentPadding)

        val bestPerfItemHeight = 48f * scale
        val universalModIconOffset = if (firstBpScore.pp!! > 1000 || lastBpScore.pp!! > 1000) 75 * scale else 60 * scale
        drawBestPerformanceItem(
            contentWidth,
            bestPerfItemHeight,
            firstBpScore.rank,
            firstBpScore.beatmapset!!.title,
            firstBpScore.beatmapset.artist,
            firstBpScore.beatmap!!.version,
            ModCombination.of(firstBpScore.mods.parseMods()),
            firstBpScore.pp,
            universalModIconOffset,
            paint
        )
        translate(0f, bestPerfItemHeight)
        val bestPerfItemVerticalInterval = 28f * scale

        val destToText = TextLine.make("...", Font(boldFont, 32f * scale))
        drawTextLineWithShadow(
            destToText,
            (contentWidth - destToText.width) / 2,
            (bestPerfItemVerticalInterval + 5f * scale) / 2,
            paint.setColor(colorWhite),
            2 * scale,
            shadowBlurRadius = 2f * scale
        )

        translate(0f, bestPerfItemVerticalInterval)
        drawBestPerformanceItem(
            contentWidth,
            bestPerfItemHeight,
            lastBpScore.rank,
            lastBpScore.beatmapset!!.title,
            lastBpScore.beatmapset.artist,
            lastBpScore.beatmap!!.version,
            ModCombination.of(lastBpScore.mods.parseMods()),
            lastBpScore.pp!!,
            universalModIconOffset,
            paint
        )
        translate(0f, bestPerfItemHeight + bestPerfItemVerticalInterval)

        // best perf detail info
        val bestPerfDetailHorizontalPadding = 15f * scale
        translate(bestPerfDetailHorizontalPadding, 0f)
        var bestPerfDetailYOffset = 0f
        mapOf(
            "Total Difference" to round(firstBpScore.pp - lastBpScore.pp).toInt().toString(),
            "Max Tween Difference" to "${round(maxTweenDiff).toInt()}(BP ${maxTweenDiffRank + 1} → ${maxTweenDiffRank + 2})",
            "Average Performance" to round(currentAverageBp).toInt().toString(),
        ).forEach { (name, value) ->
            val nameText = TextLine.make(name, Font(semiBoldFont, 24f * scale))
            val valueText = TextLine.make(value, Font(semiBoldFont, 24f * scale))

            measureFixedTextLineSize(
                nameText,
                valueText,
                0f,
                bestPerfDetailYOffset,
                contentWidth - bestPerfDetailHorizontalPadding * 2,
                drawNameText = { x, y ->
                    drawTextLineWithShadow(
                        nameText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                },
                drawValueText = { x, y ->
                    drawTextLineWithShadow(
                        valueText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }
            )
            bestPerfDetailYOffset += nameText.capHeight + 38f * scale
        }
        translate(0f, bestPerfDetailYOffset)

        // top 3 mods item
        val top3ModsText = TextLine.make("Top 3 Mods", Font(semiBoldFont, 24f * scale))
        drawTextLineWithShadow(
            top3ModsText,
            0f,
            top3ModsText.capHeight,
            paint.setColor(colorWhite),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        translate(contentWidth - bestPerfDetailHorizontalPadding * 2, 0f)
        if (topMods.isEmpty()) {
            val nmPlayerText = TextLine.make("Completely a NoMod player", Font(semiBoldFont, 18f))
            drawTextLineWithShadow(
                top3ModsText,
                -top3ModsText.width,
                (top3ModsText.capHeight + nmPlayerText.capHeight) / 2,
                paint.setColor(colorWhite),
                4 * scale,
                shadowBlurRadius = 2f * scale
            )
        } else {
            val modIconWidth = 45f * scale
            val modIconHeight = 32f * scale
            topMods.sortedBy { it.second }.forEach { (mod, count) ->
                val modCountText = TextLine.make(count.toString(), Font(semiBoldFont, 24f * scale))
                drawModIcon(
                    mod,
                    modIconWidth,
                    modIconHeight,
                    -modCountText.width - 6f * scale - modIconWidth,
                    (top3ModsText.capHeight - modIconHeight) / 2
                )
                drawTextLineWithShadow(
                    modCountText,
                    -modCountText.width,
                    (top3ModsText.capHeight + modCountText.capHeight) / 2,
                    paint.setColor(colorWhite),
                    4 * scale,
                    shadowBlurRadius = 2f * scale
                )
                translate(-modCountText.width - 6f * scale - modIconWidth - 15f * scale, 0f)
            }
        }
    }

    private fun Canvas.drawBestPerformanceItem(
        width: Float, height: Float,
        grade: String, title: String, artist: String, difficultyName: String,
        mods: ModCombination, perf: Double,
        universalModIconOffset: Float,
        paint: Paint,
    ) {
        val contentHorizontalPadding = 20f * scale
        val gradeIconWidth = 64f * scale
        val gradeIconHeight = 32f * scale
        val globalSavePoint = save()

        drawRRect(
            RRect.makeXYWH(0f, 0f, width, height, 12f * scale),
            paint.apply {
                color = transparent30PercentBlack
                mode = PaintMode.FILL
            }
        )

        translate(contentHorizontalPadding, (height - gradeIconHeight) / 2)
        val paddingContainedSavePoint = save()
        // grade icon
        drawRRect(
            RRect.makeXYWH(
                4f * scale,
                4f * scale,
                gradeIconWidth, gradeIconHeight, 90f * scale
            ), paint.apply {
                color = transparent30PercentBlack
                mode = PaintMode.FILL
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 8f * scale)
            }
        )
        drawRRect(
            RRect.makeXYWH(
                0f,
                0f,
                gradeIconWidth, gradeIconHeight, 90f * scale
            ), paint.apply {
                color = transparent30PercentBlack
                mode = PaintMode.FILL
                maskFilter = null
            }
        )
        val gradeText = TextLine.make(grade.uppercase(), Font(boldFont, 26f * scale))
        drawTextLineWithShadow(
            gradeText,
            (gradeIconWidth - gradeText.width) / 2,
            (gradeIconHeight + gradeText.capHeight) / 2,
            paint.setColor(gradeColor[grade.lowercase()]!!),
            4 * scale,
            shadowColor = Color.withA(gradeColor[grade.lowercase()]!!, 60),
            shadowBlurRadius = 4f * scale,
        )

        // song title and artist
        val modList = mods.toList().filterNot { it == Mod.None }
        translate(contentHorizontalPadding / 2 + gradeIconWidth, 0f)
        val letterLimit = 38 - modList.size * 2
        val songTitleText = TextLine.make("$title by $artist".run t@ {
            if (this@t.length > letterLimit) this@t.take(letterLimit - 3).plus("...") else this@t
        }, Font(semiBoldFont, 14f * scale))
        val songDifficultyText = TextLine.make(
            if (difficultyName.length > letterLimit) difficultyName.take(letterLimit - 3).plus("...") else difficultyName,
            Font(semiBoldFont, 12f * scale)
        )
        val songInfoVerticalInterval = (gradeIconHeight - songTitleText.capHeight - songDifficultyText.capHeight) / 3
        var songInfoYOffset = songInfoVerticalInterval
        drawTextLineWithShadow(
            songTitleText,
            0f,
            songInfoYOffset + songTitleText.capHeight,
            paint.setColor(colorWhite),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        songInfoYOffset += songInfoVerticalInterval + songTitleText.capHeight
        drawTextLineWithShadow(
            songDifficultyText,
            0f,
            songInfoYOffset + songDifficultyText.capHeight,
            paint.setColor(colorYellow),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        restoreToCount(paddingContainedSavePoint)

        translate(width - contentHorizontalPadding * 2, 0f)
        // pp text
        val ppValueText = TextLine.make(usNumber.format(round(perf)), Font(semiBoldFont, 20f * scale))
        val ppText = TextLine.make("pp", Font(semiBoldFont, 15f * scale))
        val ppSavePoint = save()
        translate(-ppText.width - ppValueText.width, 0f)
        drawTextLineWithShadow(
            ppValueText,
            0f,
            (gradeIconHeight + ppValueText.capHeight) / 2,
            paint.setColor(colorPink),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        translate(ppValueText.width, 0f)
        drawTextLineWithShadow(
            ppText,
            0f,
            (gradeIconHeight + ppValueText.capHeight) / 2,
            paint.setColor(Color.makeRGB(209, 148, 175)),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        restoreToCount(ppSavePoint)
        translate(-universalModIconOffset, 0f)

        val modIconWidth = 35f * scale
        val modIconHeight = 26f * scale
        modList.forEachIndexed { index, mod ->
            drawModIcon(
                mod,
                modIconWidth,
                modIconHeight,
                -modIconWidth - index * modIconWidth / 2,
                (gradeIconHeight - modIconHeight) / 2,
                foregroundColor = when(mod.type) {
                    ModType.DifficultyIncrease -> Color.makeRGB(255, 102, 102)
                    ModType.DifficultyReduction -> Color.makeRGB(178, 255, 102)
                    ModType.Automation -> Color.makeRGB(102, 204, 255)
                    ModType.None -> Color.makeRGB(84, 84, 84)
                },
                backgroundColor = Color.withA(transparent30PercentBlack, 40)
            )
        }

        restoreToCount(globalSavePoint)
    }

    private fun Canvas.drawPerformanceCard(
        width: Float, height: Float,
        totalPerformance: Double,
        perf: PerformanceStructure,
        paint: Paint, style: PanelStyle,
    ) {
        val contentPadding = 40f * scale
        val contentWidth = width - contentPadding * scale
        drawRRect(
            RRect.makeXYWH(0f, 0f, width, height, if (style.type == PanelType.BLUR_ITEM) 0f else 20f * scale),
            paint.apply {
                color = Color.makeARGB(min(255, max(round(style.cardBackgroundAlpha * 255).toInt(), 0)), 0, 0, 0)
                mode = PaintMode.FILL
            }
        )
        translate(contentPadding, contentPadding)

        val perfTitleText = TextLine.make("Performance", Font(semiBoldFont, 32f * scale))
        drawTextLineWithShadow(
            perfTitleText,
            0f,
            perfTitleText.capHeight,
            paint.setColor(colorWhite),
            4 * scale,
            shadowBlurRadius = 2f * scale
        )
        translate(0f, perfTitleText.capHeight + contentPadding)

        val perfBarHeight = 15 * scale
        val perfInfoHorizontalPadding = 8f * scale
        // perf bar shadow
        drawRRect(
            RRect.makeXYWH(4f, 4f, contentWidth, perfBarHeight, 90f * scale),
            paint.apply {
                color = transparent30PercentBlack
                mode = PaintMode.FILL
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 2f * scale)
            }
        )
        data class PerformanceItemDetail(
            val name: String,
            val color: Int,
            val value: Double
        )

        var perfInfoTextOffset = 0f
        var perfBarOffset = 0f
        val perfItemDetailList = listOf(
            PerformanceItemDetail("Jump", Color.makeRGB(136, 204, 104), perf.jumpAim),
            PerformanceItemDetail("Flow", Color.makeRGB(104, 191, 229), perf.flowAim),
            PerformanceItemDetail("Speed", Color.makeRGB(160, 104, 204), perf.speed),
            PerformanceItemDetail("Accuracy", Color.makeRGB(192, 169, 85), perf.accuracy),
            PerformanceItemDetail("Flashlight", Color.makeRGB(104, 109, 228), perf.flashlight),
            PerformanceItemDetail("Bonus", Color.makeRGB(203, 94, 79), perf.bonus),
        )
        perfItemDetailList.forEachIndexed { index, info ->
            val currentPerformanceWidth = (info.value / totalPerformance * contentWidth).toFloat()
            drawRRect(
                RRect.makeXYWH(
                    perfBarOffset, 0f, currentPerformanceWidth, perfBarHeight,
                    if (index == 0) 90f * scale else 0f,
                    if (index == perfItemDetailList.size - 1) 90f * scale else 0f,
                    if (index == perfItemDetailList.size - 1) 90f * scale else 0f,
                    if (index == 0) 90f * scale else 0f,
                ),
                paint.apply {
                    color = info.color
                    mode = PaintMode.FILL
                    maskFilter = null
                }
            )

            val perfNameText = TextLine.make(info.name, Font(semiBoldFont, 24f * scale))
            val perfValueText = TextLine.make(
                "${format2DFix.format(info.value)}(${format2DFix.format(info.value / totalPerformance * 100)}%)",
                Font(semiBoldFont, 24f * scale)
            )

            measureFixedTextLineSize(
                perfNameText, perfValueText,
                perfInfoHorizontalPadding,
                perfBarHeight + contentPadding + perfInfoTextOffset,
                contentWidth - perfInfoHorizontalPadding * 2,
                drawNameText = { x, y ->
                    // circle shadow
                    drawCircle(
                        x + perfBarHeight / 2 + 4f * scale,
                        y - perfNameText.capHeight / 2 + 4f * scale,
                        perfBarHeight / 2,
                        paint.apply {
                            color = transparent30PercentBlack
                            mode = PaintMode.FILL
                            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 2f * scale)
                        }
                    )
                    drawCircle(
                        x + perfBarHeight / 2,
                        y - perfNameText.capHeight / 2,
                        perfBarHeight / 2,
                        paint.apply {
                            color = info.color
                            mode = PaintMode.FILL
                            maskFilter = null
                        }
                    )
                    drawTextLineWithShadow(
                        perfNameText,
                        x + perfBarHeight + perfBarHeight,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                },
                drawValueText = { x, y ->
                    drawTextLineWithShadow(
                        perfValueText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }
            )

            perfBarOffset += currentPerformanceWidth
            perfInfoTextOffset += perfNameText.capHeight + 36f * scale
        }
    }

    private fun Canvas.drawDetailInfoCard(
        width: Float, height: Float,
        statistics: UserStatisticsDTO,
        rankHistory: RankHistoryDTO,
        gradeCount: GradeCountsDTO,
        paint: Paint, style: PanelStyle,
    ) {
        val contentPadding = 40f * scale
        drawRRect(
            RRect.makeXYWH(0f, 0f, width, height, if (style.type == PanelType.BLUR_ITEM) 0f else 20f * scale),
            paint.apply {
                color = Color.makeARGB(min(255, max(round(style.cardBackgroundAlpha * 255).toInt(), 0)), 0, 0, 0)
                mode = PaintMode.FILL
            }
        )
        translate(contentPadding, contentPadding)
        val baseSavePoint = save()

        // statistics
        val statisticsWidth = 450f * scale
        translate(width - contentPadding * 2 - statisticsWidth, 0f)
        var yOffset = 0f
        mapOf(
            "Ranked Score" to usNumber.format(statistics.rankedScore),
            "Total Score" to usNumber.format(statistics.totalScore),
            "Play Count" to usNumber.format(statistics.playCount),
            "Total Hit" to usNumber.format(statistics.totalHits),
            "Hit per Play" to round(statistics.totalHits.toDouble() / statistics.playCount.toDouble()).toInt(),
            "Total Play Time" to (statistics.playTime / 60).run { "${(this / 60).toInt()}h, ${this % 60}m" },
            "Hit Accuracy" to format2DFix.format(statistics.hitAccuracy) + "%",
            "Maximum Combo" to usNumber.format(statistics.maximumCombo)
        ).forEach { (name, value) ->
            val nameText = TextLine.make(name, Font(semiBoldFont, 24f * scale))
            val valueText = TextLine.make(value.toString(), Font(semiBoldFont, 24f * scale))

            measureFixedTextLineSize(nameText, valueText, 0f, yOffset, statisticsWidth,
                drawNameText = { x, y ->
                    drawTextLineWithShadow(
                        nameText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }, drawValueText = { x, y ->
                    drawTextLineWithShadow(
                        valueText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }
            )
            yOffset += nameText.capHeight + 19f * scale
        }
        restoreToCount(baseSavePoint)

        val rankCurveWidth = width - contentPadding * 3 - statisticsWidth
        // grade counts
        val rankCurveSavePoint = save()
        translate(0f, height - contentPadding * 2)
        val gradeIconWidth = 100f * scale
        val gradeIconHeight = 50f * scale
        val intervalX = (rankCurveWidth - gradeIconWidth * 5) / 6f

        var xOffset = 0f
        var maxGradeHeight = 0f
        mapOf(
            "XH" to gradeCount.ssh,
            "X" to gradeCount.ss,
            "SH" to gradeCount.sh,
            "S" to gradeCount.s,
            "A" to gradeCount.a
        ).forEach { (grade, count) ->
            val gradeCountText = TextLine.make(count.toString(), Font(semiBoldFont, 24f * scale))
            xOffset += intervalX
            drawTextLineWithShadow(
                gradeCountText,
                xOffset + (gradeIconWidth - gradeCountText.width) / 2,
                0f,
                paint.setColor(colorWhite),
                4 * scale,
                shadowBlurRadius = 2f * scale
            )
            drawRRect(
                RRect.makeXYWH(
                    xOffset + 4f * scale,
                    -gradeCountText.capHeight - 5f * scale - gradeIconHeight + 4f * scale,
                    gradeIconWidth, gradeIconHeight, 90f * scale
                ), paint.apply {
                    color = transparent30PercentBlack
                    mode = PaintMode.FILL
                    maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 8f * scale)
                }
            )
            drawRRect(
                RRect.makeXYWH(
                    xOffset,
                    -gradeCountText.capHeight - 5f * scale - gradeIconHeight,
                    gradeIconWidth, gradeIconHeight, 90f * scale
                ), paint.apply {
                    color = transparent30PercentBlack
                    mode = PaintMode.FILL
                    maskFilter = null
                }
            )

            val gradeText = TextLine.make(grade.uppercase(), Font(boldFont, 40f * scale))
            drawTextLineWithShadow(
                gradeText,
                xOffset + (gradeIconWidth - gradeText.width) / 2,
                -gradeCountText.capHeight - 5f * scale - (gradeIconHeight - gradeText.capHeight) / 2,
                paint.setColor(gradeColor[grade.lowercase()]!!),
                4 * scale,
                shadowColor = Color.withA(gradeColor[grade.lowercase()]!!, 60),
                shadowBlurRadius = 4f * scale,
            )

            maxGradeHeight = max(maxGradeHeight, gradeCountText.capHeight + 5f * scale + gradeIconHeight)
            xOffset += gradeIconWidth
        }
        restoreToCount(rankCurveSavePoint)

        //rank history curve
        val rawRankCurveHeight = height - contentPadding * 2 - maxGradeHeight
        val rankCurveHeight = rawRankCurveHeight * 0.8f
        translate(0f, (rawRankCurveHeight - rankCurveHeight) / 2)
        if (rankHistory.mode == "osu") {
            val rankCurveData = rankHistory.data
            if (rankCurveData.size == 1) {
                drawLine(rankCurveHeight / 2, 0f, rankCurveHeight / 2, rankCurveWidth, paint.apply {
                    color = colorWhite
                    strokeWidth = 5f * scale
                    strokeCap = PaintStrokeCap.ROUND
                })
            } else {
                val xLength = rankCurveWidth / rankCurveData.size.toFloat()
                val (highestRank, lowestRank) = kotlin.run {
                    var max = rankCurveData.first()
                    var min = rankCurveData.first()
                    rankCurveData.forEach {
                        if (it < min) min = it
                        if (it > max) max = it
                    }
                    min to max
                }
                val maxRankDiff = lowestRank - highestRank
                var xRankOffset = 0f

                repeat(rankCurveData.size - 1) {
                    // rank line shadow
                    drawLine(
                        xRankOffset + 4f * scale,
                        rankCurveHeight * ((rankCurveData[it] - highestRank) / maxRankDiff.toFloat()) + 4f * scale,
                        xRankOffset + xLength + 4f * scale,
                        rankCurveHeight * ((rankCurveData[it + 1] - highestRank) / maxRankDiff.toFloat()) + 4f * scale,
                        paint.apply {
                            color = transparent30PercentBlack
                            strokeWidth = 2f * scale
                            strokeCap = PaintStrokeCap.SQUARE
                            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 4f * scale)
                        }
                    )
                    xRankOffset += xLength
                }
                xRankOffset = 0f
                repeat(rankCurveData.size - 1) {
                    // rank line
                    drawLine(
                        xRankOffset,
                        rankCurveHeight * ((rankCurveData[it] - highestRank) / maxRankDiff.toFloat()),
                        xRankOffset + xLength,
                        rankCurveHeight * ((rankCurveData[it + 1] - highestRank) / maxRankDiff.toFloat()),
                        paint.apply {
                            color = when {
                                rankCurveData[it + 1] - rankCurveData[it] > 0 -> colorRed
                                rankCurveData[it + 1] - rankCurveData[it] < 0 -> colorGreen
                                else -> colorWhite
                            }
                            strokeWidth = 4f * scale
                            strokeCap = PaintStrokeCap.ROUND
                            maskFilter = null
                        }
                    )
                    xRankOffset += xLength
                }
            }
        } else {
            val notAvailableText = TextLine.make("Rank history is not available.", Font(semiBoldFont, 40f * scale))
            drawTextLineWithShadow(
                notAvailableText,
                (rankCurveWidth - notAvailableText.width) / 2,
                (rankCurveHeight + notAvailableText.capHeight) / 2,
                paint.setColor(colorWhite),
                4 * scale,
                shadowBlurRadius = 2f * scale
            )
        }
    }

    private fun Canvas.drawGeneralInfoCard(
        width: Float, height: Float,
        playerAvatar: Image, playerName: String,
        countrySVG: OptionalValue<SVGDOM>, countryName: String,
        level: Int, levelProgress: Int,
        globalRank: Long?, countryRank: Long?,
        perfPoint: Double, newlyGainPp: Double,
        discord: String?, twitter: String?, website: String?,
        paint: Paint, style: PanelStyle,
    ) {
        val avatarPadding = 75 * scale
        val avatarSize = width - avatarPadding * 2
        drawRRect(
            RRect.makeXYWH(0f, 0f, width, height, if (style.type == PanelType.BLUR_ITEM) 0f else 20f * scale),
            paint.apply {
                color = Color.makeARGB(min(255, max(round(style.cardBackgroundAlpha * 255).toInt(), 0)), 0, 0, 0)
                mode = PaintMode.FILL
            }
        )

        // player avatar
        drawRoundCorneredImage(
            playerAvatar.scale(avatarSize / playerAvatar.width),
            avatarPadding, avatarPadding, 20 * scale
        )
        translate(0f, width - avatarPadding * 0.35f)

        // player name
        val playerNameText = TextLine.make(playerName, Font(semiBoldFont, 38f * scale))
        drawTextLineWithShadow(
            playerNameText,
            (width - playerNameText.width) / 2, playerNameText.capHeight, paint.setColor(colorWhite),
            4 * scale, shadowBlurRadius = 2f * scale
        )
        translate(0f, playerNameText.capHeight + 20f * scale)

        // player country
        val countryImageSize = 45f * scale
        val countryImage = countrySVG.mapRight { svg -> svg.toScaledImage(countryImageSize / 100f, 100f) }
        val countryNameText = TextLine.make(countryName, Font(semiBoldFont, 24f * scale))
        val countryNameTextOffset = (countryImage.rightOrNull?.width?.toFloat() ?: 0f) + 20f * scale
        val countryXOffset = (countryNameTextOffset + countryNameText.width).let { countryWidth ->
            if (countryWidth > avatarSize) (avatarSize - countryWidth) / 2f else 0f
        }
        countryImage.ifRight { image ->
            drawImage(image, avatarPadding + countryXOffset, 0f)
            /*drawRRect(
                RRect.makeXYWH(
                    avatarPadding + countryXOffset, 0f,
                    countryImageSize, countryImageSize,
                    12f * scale
                ), paint
            )*/
        }
        drawTextLineWithShadow(
            countryNameText,
            avatarPadding + countryXOffset + countryNameTextOffset,
            (countryImageSize + countryNameText.capHeight) / 2,
            paint.setColor(colorWhite), 4 * scale, shadowBlurRadius = 2f * scale
        )
        translate(0f, countryImageSize + 28f * scale)

        // content with universal horizontal padding
        val contentPadding = 30f * scale
        val contentWidth = width - contentPadding * 2
        translate(contentPadding, 0f)

        // level
        val levelPolygonImage = svgDom("svg/polygon_level.svg").toScaledImage(scale)
        val levelText = TextLine.make(level.toString(), Font(semiBoldFont, 24f * scale))
        drawImage(levelPolygonImage, 0f, 0f)
        drawTextLineWithShadow(
            levelText,
            (levelPolygonImage.width - levelText.width) / 2,
            (levelPolygonImage.height + levelText.capHeight) / 2,
            paint.setColor(colorWhite), 4 * scale, shadowBlurRadius = 2f * scale
        )
        val levelBarWidth = contentWidth - levelPolygonImage.width - 25f * scale
        drawLine( // level bar shadow
            levelPolygonImage.width + 25f * scale + 4f * scale,
            levelPolygonImage.height / 2f + 4f * scale,
            levelPolygonImage.width + 25f * scale + levelBarWidth + 4f * scale,
            levelPolygonImage.height / 2f + 4f * scale,
            paint.apply {
                color = transparent30PercentBlack
                mode = PaintMode.FILL
                strokeWidth = 8f * scale
                strokeCap = PaintStrokeCap.ROUND
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 2f * scale)
            }
        )
        drawLine( // level bar background
            levelPolygonImage.width + 25f * scale, levelPolygonImage.height / 2f,
            levelPolygonImage.width + 25f * scale + levelBarWidth, levelPolygonImage.height / 2f,
            paint.apply {
                color = transparent30PercentBlack
                strokeCap = PaintStrokeCap.ROUND
                strokeWidth = 8f * scale
                maskFilter = null
            }
        )
        drawLine( // level bar foreground
            levelPolygonImage.width + 25f * scale,
            levelPolygonImage.height / 2f,
            levelPolygonImage.width + 25f * scale + levelBarWidth * (levelProgress / 100f),
            levelPolygonImage.height / 2f,
            paint.apply {
                color = colorPink
                strokeWidth = 8f * scale
                strokeCap = PaintStrokeCap.ROUND
                maskFilter = null
            }
        )
        val levelProgressText = TextLine.make("$levelProgress%", Font(semiBoldFont, 20f * scale))
        val levelProgressTextXOffset = (levelProgressText.width / levelBarWidth * 100 / 2f)
            .let { min(100f - it, max(levelProgress.toFloat(), it)) } / 100f *
                levelBarWidth - levelProgressText.width / 2
        drawTextLineWithShadow(
            levelProgressText,
            levelPolygonImage.width + 25f * scale + levelProgressTextXOffset,
            levelPolygonImage.height / 2f + 4f * scale + 5f * scale + levelProgressText.capHeight,
            paint.setColor(colorWhite),
            4 * scale, shadowBlurRadius = 2f * scale
        )
        translate(0f, levelPolygonImage.height + 35f * scale)

        // general info
        var yOffset = 0f
        mapOf(
            "Global" to ((globalRank?.let { usNumber.format(it) } ?: "-") to true),
            "Country" to ((countryRank?.let { usNumber.format(it) } ?: "-") to true),
            "PP" to (buildString {
                append(usNumber.format(round(perfPoint).toInt()))
                val ngp = format2DFix.format(newlyGainPp)
                if (newlyGainPp > 0.0) {
                    append("(+").append(ngp).append(")")
                } else if (newlyGainPp < 0.0) {
                    append("(").append(ngp).append(")")
                }
            } to false)
        ).forEach { (name, value) ->
            val nameText = TextLine.make(name, Font(semiBoldFont, 28f * scale))
            val valueText = TextLine.make(value.first, Font(semiBoldFont, 32f * scale))

            measureFixedTextLineSize(nameText, valueText, 0f, yOffset, contentWidth,
                drawNameText = { x, y ->
                    drawTextLineWithShadow(
                        nameText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }, drawValueText = { x, y ->
                    if (value.second) {
                        val numberSymbolText = TextLine.make("#", Font(semiBoldFont, 24f * scale))
                        drawTextLineWithShadow(
                            numberSymbolText,
                            x - numberSymbolText.width,
                            y,
                            paint.setColor(colorYellow),
                            4 * scale,
                            shadowBlurRadius = 2f * scale
                        )
                    }
                    drawTextLineWithShadow(
                        valueText,
                        x,
                        y,
                        paint.setColor(colorWhite),
                        4 * scale,
                        shadowBlurRadius = 2f * scale
                    )
                }
            )
            yOffset += nameText.capHeight + 22f * scale
        }

        // social info
        translate(0f, yOffset - 22f * scale + 35f * scale)
        //TODO social info
    }

    private fun Canvas.measureFixedTextLineSize(
        nameText: TextLine, valueText: TextLine,
        x: Float, y: Float, width: Float,
        arrangeAtBaseLine: Boolean = true,
        drawNameText: Canvas.(x: Float, y: Float) -> Unit,
        drawValueText: Canvas.(x: Float, y: Float) -> Unit,
    ) {
        drawNameText(x, y + nameText.capHeight)
        drawValueText(
            x + width - valueText.width,
            y + when (arrangeAtBaseLine) {
                true -> nameText.capHeight - valueText.capHeight
                false -> (nameText.capHeight - valueText.capHeight) / 2
            } + valueText.capHeight
        )
    }
}
