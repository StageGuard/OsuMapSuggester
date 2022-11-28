package me.stageguard.obms.bot.route

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.Profile
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import io.github.humbleui.skija.EncodedImageFormat
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.ProfilePanelStyle
import me.stageguard.obms.database.model.ProfilePanelStyleTable
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.ppnative.PPCalculatorNative
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either.Companion.ifRight
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.PlainText
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.math.pow

data class PerformanceStructure(
    var jumpAim: Double = 0.0,
    var flowAim: Double = 0.0,
    var speed: Double = 0.0,
    var accuracy: Double = 0.0,
    var flashlight: Double = 0.0,
    var bonus: Double = 0.0,
)

enum class PanelType {
    BLUR_BACKGROUND,
    BLUR_ITEM,
    NO_BLUR
}
data class PanelStyle(
    val type: PanelType,
    val backgroundAlpha: Double,
    val cardBackgroundAlpha: Double,
    val blurRadius: Double,
    val customBgFile: File?
) {
    fun toDAO(qq: Long): ProfilePanelStyle {
        return ProfilePanelStyle {
            this.qq = qq
            this.type = this@PanelStyle.type.ordinal
            this.blurRadius = this@PanelStyle.blurRadius
            this.backgroundAlpha = this@PanelStyle.backgroundAlpha
            this.cardBackgroundAlpha = this@PanelStyle.cardBackgroundAlpha
            this.useCustomBg = false
        }
    }
}

private val DEFAULT_PANEL_STYLE = PanelStyle(
    PanelType.BLUR_BACKGROUND,
    0.3,
    0.3,
    16.0,
    null
)
private fun getCustomBGFile(qq: Long) =
    OsuMapSuggester.dataFolder.resolve("custom_bg/$qq").also {
        if (!it.parentFile.exists()) it.parentFile.mkdirs()
    }

fun GroupMessageSubscribersBuilder.profile() {
    routeLock(startWithIgnoreCase(".info")) {
        val additionalCommand = message
            .filterIsInstance<PlainText>().joinToString().trim()
            .substringAfter(".info")
        if (additionalCommand.isEmpty()) {
            drawInfoPanelAndSend()
            return@routeLock
        }
        when (additionalCommand.trim().split(' ').first()) {
            "help" -> atReply("访问 ${PluginConfig.helpLink}/Commands#info-set-argvalueargvalue- 查看自定义个人主页面板指令。")
            "set" -> {
                val args = additionalCommand.substringAfter("set").trim()
                    .split(',').associate { a ->
                        val singleArg = a.trim().split("=")
                        singleArg.first().trim() to singleArg.last().trim()
                    }

                if (args.isEmpty()) {
                    atReply("未指定任何参数。")
                    return@routeLock
                }

                var type = args["t"] ?.toIntOrNull()
                val br = args["br"] ?.toDoubleOrNull()
                val ba = args["ba"] ?.toDoubleOrNull()
                val ca = args["ca"] ?.toDoubleOrNull()

                if (br == null && ba == null && ca == null && type == null) {
                    atReply("未指定任何有效参数。")
                    return@routeLock
                }
                if (type !in 0..2) type = null

                Database.query { db ->
                    val style = db.sequenceOf(ProfilePanelStyleTable).find { f -> f.qq eq sender.id }
                    if (style == null) {
                        ProfilePanelStyleTable.insert(ProfilePanelStyle {
                            qq = sender.id
                            this.type = type ?: 0
                            blurRadius = br ?: 16.0
                            backgroundAlpha = ba ?: 0.3
                            cardBackgroundAlpha = ca ?: 0.3
                            useCustomBg = false
                        })
                    } else {
                        style.type = type ?: style.type
                        style.blurRadius = br ?: style.blurRadius
                        style.backgroundAlpha = ba ?: style.backgroundAlpha
                        style.cardBackgroundAlpha = ca ?: style.cardBackgroundAlpha
                        style.flushChanges()
                    }
                }

                atReply(buildString {
                    if (type != null) append("模糊类型设置为 ${when(type) {
                        0 -> "背景模糊"
                        1 -> "仅卡片模糊"
                        2 -> "无模糊"
                        else -> error("unreachable condition.")
                    }}，")
                    if (br != null) append("模糊范围已设置为 $br，")
                    if (ba != null) append("背景不透明度已设置为 $ba，")
                    if (ca != null) append("卡片不透明度已设置为 $ca。")
                })
            }
            "rmbg" -> {
                val file = getCustomBGFile(sender.id)
                if (file.exists()) file.delete()
                Database.query { db ->
                    val style = db.sequenceOf(ProfilePanelStyleTable).find { f -> f.qq eq sender.id }
                    if (style != null) {
                        style.useCustomBg = false
                        style.flushChanges()
                    }
                }
                atReply("已清除自定义背景图片，将使用个人主页背景图片。")
            }
            "uplbg" -> {
                val image = message.find { m -> m is Image } as? Image
                if (image == null) {
                    atReply("未发送背景图片，请包含图片发送。\n" +
                            "桌面端直接将图片拖进聊天框，移动端请选择图片，勾选发送原图后点发送图片。")
                    return@routeLock
                }

                val warn = image.width < 1280 || image.height < 720

                OsuMapSuggester.launch(CoroutineName("Upload profile bg of ${sender.id}")) {
                    val stream = OsuWebApi.openStream(image.queryUrl(), mapOf(), mapOf()).rightOrNull
                    if (stream == null) {
                        atReply("无法下载图片，请重新发送指令。若持续出现此情况，请更换图片或反馈于 https://github.com/StageGuard/OsuMapSuggester/issues")
                        return@launch
                    }
                    stream.use {s ->
                        val file = getCustomBGFile(sender.id)
                        if (!file.exists()) file.createNewFile()
                        file.writeBytes(s.readAllBytes())
                    }

                    Database.query { db ->
                        val style = db.sequenceOf(ProfilePanelStyleTable).find { f -> f.qq eq sender.id }
                        if (style == null) {
                            ProfilePanelStyleTable.insert(ProfilePanelStyle {
                                qq = sender.id
                                blurRadius = 16.0
                                backgroundAlpha = 0.3
                                cardBackgroundAlpha = 0.3
                                useCustomBg = false
                            })
                        } else {
                            style.useCustomBg = true
                            style.flushChanges()
                        }
                    }

                    atReply(buildString {
                        appendLine("已成功上传自定义背景图片。")
                        if (warn) append("警告：图片尺寸小于 1280x720 可能会有渲染错位问题。")
                    })
                }
            }
        }
    }
}

fun GroupMessageEvent.drawInfoPanelAndSend() {
    OsuMapSuggester.launch(
        CoroutineName("Command \"info\" of ${sender.id}") + refactoredExceptionCatcher
    ) {
        val profile = OsuWebApi.me(sender.id).rightOrThrowLeft()
        val bestScores = OsuWebApi.userScore(sender.id, type = "best", limit = 100).rightOrNull
            ?.mapIndexed { index, scoreDTO -> index to scoreDTO } ?.sortedBy { it.first } ?.toMap() ?: mapOf()
        val bestScoresCount = bestScores.count()

        val newBestScores = kotlin.run {
            val currentLocalDateTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            bestScores.filter { (_, score) ->
                currentLocalDateTime - score.createdAt.primitive.toEpochSecond(ZoneOffset.UTC) < 60 * 60 * 24
            }
        }
        val firstNewBestScoreRank = newBestScores.entries.firstOrNull() ?.key ?: -1
        val lastBestScore = bestScores.entries.lastOrNull() ?.value

        var bpOffset = 0
        val performances = PerformanceStructure()
        var newlyGainPp = 0.0
        var currentAverageBp = 0.0
        var lastAverageBp = 0.0
        var maxBpDiff = bestScores.values.run { last().pp!! - first().pp!! }
        var maxTweenDiff = 0.0
        var maxTweenDiffRank = 0
        val modUsage = mutableMapOf<Mod, Int>()

        bestScores.forEach { scoreAndRank -> BeatmapCache.getBeatmapFile(scoreAndRank.value.beatmap!!.id).ifRight { file ->
            val (rank, score) = scoreAndRank
            val beatmap = Beatmap.parse(file)
            val mods = score.mods.parseMods()

            val pp = PPCalculatorNative.of(file.absolutePath).mods(mods)
                .passedObjects(score.statistics.run { count300 + count100 + count50 })
                .misses(score.statistics.countMiss)
                .combo(score.maxCombo)
                .accuracy(score.accuracy * 100).calculate()
            val ppx = beatmap.calculateSkills(
                ModCombination.of(mods),
                Optional.of(score.statistics.run { count300 + count100 + count50 })
            )

            val totalAimStrain = ppx.jumpAimStrain + ppx.flowAimStrain
            val totalStrainWithoutMultiplier = pp.run {
                aim.pow(1.1) + speed.pow(1.1) + accuracy.pow(1.1) + flashlight.pow(1.1)
            }

            performances.jumpAim += pp.total * (pp.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                    (ppx.jumpAimStrain / totalAimStrain) * 0.95.pow(rank)
            performances.flowAim += pp.total * (pp.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                    (ppx.flowAimStrain / totalAimStrain) * 0.95.pow(rank)
            performances.speed += pp.total * (pp.speed.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)
            performances.accuracy += pp.total * (pp.accuracy.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)
            performances.flashlight += pp.total * (pp.flashlight.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)

            // assume that pp of the best performance that is out of rank 100 is the pp of current last best performance
            currentAverageBp += score.pp!! / bestScoresCount.toDouble()
            if (firstNewBestScoreRank != -1 && lastBestScore != null) {
                if (rank >= firstNewBestScoreRank) {
                    if (newBestScores[rank] != null) { bpOffset -- }
                    val bestScoreWithOffset = bestScores[rank - bpOffset] ?: lastBestScore

                    newlyGainPp += (score.pp - bestScoreWithOffset.pp!!) * 0.95.pow(rank)
                    lastAverageBp += bestScoreWithOffset.pp / bestScoresCount.toDouble()
                } else {
                    lastAverageBp += score.pp / bestScoresCount.toDouble()
                }
            }

            val diff = score.pp - (bestScores[rank + 1] ?: lastBestScore!!).pp!!
            if (diff > maxTweenDiff) {
                maxTweenDiff = diff
                maxTweenDiffRank = rank
            }

            score.mods.parseMods().forEach m@ {
                if (it == Mod.None) return@m
                val prevCount = modUsage.putIfAbsent(it, 1)
                if (prevCount != null) modUsage[it] = prevCount + 1
            }
        } }

        performances.bonus = performances.run { profile.statistics.pp!! - jumpAim - flowAim - speed - accuracy - flashlight }
        if (firstNewBestScoreRank == -1) { lastAverageBp = currentAverageBp }

        val panelStyle = Database.query { db ->
            val style = db.sequenceOf(ProfilePanelStyleTable).find { it.qq eq sender.id }
            if (style != null) PanelStyle(
                enumValues<PanelType>()[style.type],
                style.backgroundAlpha,
                style.cardBackgroundAlpha,
                style.blurRadius,
                getCustomBGFile(sender.id)
            ) else DEFAULT_PANEL_STYLE.also {
                ProfilePanelStyleTable.insert(it.toDAO(sender.id))
            }
        } ?: DEFAULT_PANEL_STYLE

        val bytes = withContext(graphicProcessorDispatcher) {
            Profile.drawProfilePanel(
                profile, panelStyle, performances,
                bestScores.entries.first().value, bestScores.entries.last().value,
                newlyGainPp, currentAverageBp, lastAverageBp, maxTweenDiff, maxTweenDiffRank,
                modUsage.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
            ).bytes(EncodedImageFormat.PNG)
        }
        val externalResource = bytes.toExternalResource("png")
        val image = group.uploadImage(externalResource)
        runInterruptible { externalResource.close() }
        atReply(image.toMessageChain())
    }
}
