package me.stageguard.obms.utils

import me.stageguard.obms.OsuMapSuggester
import org.jetbrains.skija.*
import org.jetbrains.skija.svg.SVGDOM
import java.io.File
import java.io.InputStream
import java.lang.IllegalStateException
import kotlin.math.ceil

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun resourcePath(path: String) = OsuMapSuggester.dataFolder.absolutePath + File.separator + "resources" + File.separator + path
fun resourceStream(path: String): InputStream = File(resourcePath(path)).inputStream()
fun typeface(variant: String) = Typeface.makeFromFile(resourcePath("font/Torus-$variant.otf"))
fun image(path: String) = Image.makeFromEncoded(resourceStream(path).readAllBytes())
fun svgDom(path: String) = SVGDOM(Data.makeFromBytes(resourceStream(path).readAllBytes()))

fun Surface.export(
    path: String,
    format: EncodedImageFormat = EncodedImageFormat.WEBP
) {
    val image = makeImageSnapshot()
    val imgData = image.encodeToData(format) ?.bytes!!
    try {
        val file = File(path).run {
            File(parent).mkdirs()
            createNewFile()
            writeBytes(imgData)
        }
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

fun SVGDOM.toScaledImage(scale: Float): Image = kotlin.run {
    val surface = root!!.run {
        Surface.makeRasterN32Premul(ceil(width.value * scale).toInt(), ceil(height.value * scale).toInt())
    }
    surface.canvas.setMatrix(Matrix33.makeScale(scale, scale))
    render(surface.canvas)
    surface.makeImageSnapshot()
}