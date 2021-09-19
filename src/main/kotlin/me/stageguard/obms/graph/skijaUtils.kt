package me.stageguard.obms.graph

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.graph.item.RecentPlay
import me.stageguard.obms.utils.lerp
import org.jetbrains.skija.*
import org.jetbrains.skija.svg.SVGDOM
import java.io.File
import java.io.InputStream
import java.lang.IllegalStateException
import kotlin.math.ceil
import kotlin.math.round

fun resourcePath(path: String) = OsuMapSuggester.dataFolder.absolutePath + File.separator + "resources" + File.separator + path
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
        val xOffset = imageInfo.width * (1.0 - remainXRatio) / 2
        val yOffset = imageInfo.height * (1.0 - remainYRatio) / 2
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
            filterQuality = FilterQuality.HIGH
        }
        canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()), paint.apply {
            color = Color.makeARGB(0, 0, 0, 0)
        })
        canvas.drawRRect(RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(), radius), paint.apply {
            color = 0xff424242.toInt()
        })

        canvas.drawImage(src, 0f, 0f, paint.apply {
            paint.blendMode = BlendMode.SRC_IN
        })
        makeImageSnapshot()
    }.also {
        drawImage(it, left, top)
    }
}

fun Canvas.drawTextLineWithShadow(
    textLine: TextLine, x: Float, y: Float, paint: Paint,
    dropShadowX: Float = 3f, dropShadowY: Float = dropShadowX, shadowColor: Int = Color.makeRGB(0, 0, 0)
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

fun lerpColor(src: Int, dst: Int, percentage: Double) =
    Color.makeRGB(
        lerp(Color.getR(src).toDouble(), Color.getR(dst).toDouble(), percentage).toInt(),
        lerp(Color.getG(src).toDouble(), Color.getG(dst).toDouble(), percentage).toInt(),
        lerp(Color.getB(src).toDouble(), Color.getB(dst).toDouble(), percentage).toInt()
    )