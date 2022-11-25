package me.stageguard.obms.graph

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.utils.lerp
import io.github.humbleui.skija.*
import io.github.humbleui.skija.svg.SVGDOM
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import me.stageguard.obms.utils.bmf.BitmapFont
import java.io.File
import java.io.InputStream
import kotlin.math.ceil

private val RES_PATH_ROOT by lazy {
    if (System.getProperty("me.stageguard.obms.debug", "0") == "1") {
        "./src/main"
    } else {
        OsuMapSuggester.dataFolder.absolutePath
    }
}

fun resourcePath(path: String) = RES_PATH_ROOT + File.separator + "resources" + File.separator + path
fun resourceStream(path: String): InputStream = File(resourcePath(path)).inputStream()
fun typeface(variant: String) = Typeface.makeFromFile(resourcePath("font/Torus-$variant.otf"))
fun image(path: String) = Image.makeFromEncoded(resourceStream(path).readAllBytes())
fun svgDom(path: String) = SVGDOM(Data.makeFromBytes(resourceStream(path).readAllBytes()))

fun Surface.bytes(
    format: EncodedImageFormat = EncodedImageFormat.WEBP
) : ByteArray {
    val image = makeImageSnapshot()
    val imgData = image.encodeToData(format) ?.bytes!!
    try {
        return imgData
    } catch (ex: Exception) {
        throw IllegalStateException("DRAW_ERROR:$ex")
    }
}

fun Canvas.drawSvg(dom: SVGDOM, x: Float, y: Float, scale: Float = 1.0f, paint: Paint? = null) {
    val surface = dom.root!!.run {
        Surface.makeRasterN32Premul((width.value * scale).toInt(), (height.value * scale).toInt())
    }
    surface.canvas.setMatrix(Matrix33.makeScale(scale, scale))
    dom.render(surface.canvas)
    drawImage(surface.makeImageSnapshot(), x, y, paint)
}

fun SVGDOM.toScaledImage(ratio: Float): Image = kotlin.run {
    val surface = root!!.run {
        Surface.makeRasterN32Premul(ceil(width.value * ratio).toInt(), ceil(height.value * ratio).toInt())
    }
    surface.canvas.setMatrix(Matrix33.makeScale(ratio, ratio))
    render(surface.canvas)
    surface.makeImageSnapshot()
}

fun Image.scale(ratioX: Float, ratioY: Float = ratioX): Image =
    Surface.makeRasterN32Premul(
        ceil(width * ratioX).toInt(),
        ceil(height * ratioY).toInt()
    ).run {
        canvas.setMatrix(Matrix33.makeScale(ratioX, ratioY))
        canvas.drawImage(this@scale, 0F, 0F)
        makeImageSnapshot()
    }

fun Image.cutCenter(remainXRatio: Float, remainYRatio: Float): Image =
    Surface.makeRasterN32Premul(
        ceil(width * remainXRatio).toInt(),
        ceil(height * remainYRatio).toInt()
    ).run {
        val xOffset = this@cutCenter.width * (1.0 - remainXRatio) / 2
        val yOffset = this@cutCenter.height * (1.0 - remainYRatio) / 2
        canvas.save()
        canvas.translate(-xOffset.toFloat(), -yOffset.toFloat())
        canvas.drawImage(this@cutCenter, 0F, 0F)
        canvas.restore()
        makeImageSnapshot()
    }

fun Canvas.drawRoundCorneredImage(src: Image, left: Float, top: Float, radius: Float) {
    Surface.makeRasterN32Premul(src.width, src.height).run {
        val paint = Paint().apply {
            isAntiAlias = true
            mode = PaintMode.FILL
        }
        canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()), paint.apply {
            color = Color.makeARGB(0, 0, 0, 0)
        })
        canvas.drawRRect(RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(), radius), paint.apply {
            color = 0xff424242.toInt()
        })

        canvas.drawImage(src, 0f, 0f, paint.apply {
            setBlendMode(BlendMode.SRC_IN)
        })
        makeImageSnapshot()
    }.also {
        drawImage(it, left, top)
    }
}

fun Canvas.drawTextLineWithShadow(
    textLine: TextLine, x: Float, y: Float, paint: Paint,
    dropShadowX: Float, dropShadowY: Float = dropShadowX, shadowColor: Int = Color.makeRGB(0, 0, 0)
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

fun Canvas.drawBitmapFontText(
    bitmapFont: BitmapFont, character: Int,
    x: Float, y: Float, paint: Paint,
) {
    val image = bitmapFont[character] ?: Surface.makeRasterN32Premul(1, 1).makeImageSnapshot()
    drawImage(image, x, y, paint)
}

fun lerpColor(src: Int, dst: Int, percentage: Double) =
    Color.makeRGB(
        lerp(Color.getR(src).toDouble(), Color.getR(dst).toDouble(), percentage).toInt(),
        lerp(Color.getG(src).toDouble(), Color.getG(dst).toDouble(), percentage).toInt(),
        lerp(Color.getB(src).toDouble(), Color.getB(dst).toDouble(), percentage).toInt()
    )

fun parseTime(second: Int) : String {
    val minute = (second / 60).run { if(this < 10) "0$this" else this.toString() }
    val remainSec = (second % 60).run { if(this < 10) "0$this" else this.toString() }
    return "${minute}:$remainSec"
}
