package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un rectangle de l'image, en pixels. */
data class Zone(val x: Int, val y: Int, val largeur: Int, val hauteur: Int) {
    val droite: Int get() = x + largeur
    val bas: Int get() = y + hauteur

    /** La même zone, rétrécie de [part] de sa largeur et de sa hauteur de chaque côté (0,05 : 5 %). */
    fun interieur(part: Double): Zone {
        val dx = (largeur * part).roundToInt()
        val dy = (hauteur * part).roundToInt()
        return Zone(x + dx, y + dy, max(1, largeur - 2 * dx), max(1, hauteur - 2 * dy))
    }

    /** La même zone dans une image [facteur] fois plus grande. */
    fun agrandie(facteur: Double): Zone =
        Zone((x * facteur).roundToInt(), (y * facteur).roundToInt(), max(1, (largeur * facteur).roundToInt()), max(1, (hauteur * facteur).roundToInt()))
}

/**
 * Une image en couleurs : pixels ARGB ligne après ligne, comme `Bitmap.getPixels` les donne. L'alpha ne sert
 * qu'aux icônes officielles, détourées ; une image de la caméra est opaque.
 */
class ImageRgb(val largeur: Int, val hauteur: Int, val pixels: IntArray) {
    init {
        require(largeur > 0 && hauteur > 0 && pixels.size == largeur * hauteur) { "Taille d'image incohérente" }
    }

    fun pixel(x: Int, y: Int): Int = pixels[y * largeur + x]

    /** Luminance d'un pixel, de 0 à 255. */
    fun luminance(x: Int, y: Int): Int = luminance(pixel(x, y))

    /** Toute l'image, comme zone. */
    val zone: Zone get() = Zone(0, 0, largeur, hauteur)

    /**
     * La zone [zone] ramenée à [l] × [h] pixels : chaque pixel est la moyenne de ceux qu'il couvre (au prorata
     * de la surface couverte), pondérée par leur opacité. Réduire ainsi lisse le bruit et le moiré d'un écran
     * filmé. Ce qui dépasse de l'image compte comme transparent.
     */
    fun echantillon(zone: Zone, l: Int, h: Int): ImageRgb {
        val sortie = IntArray(l * h)
        val pasX = zone.largeur.toDouble() / l
        val pasY = zone.hauteur.toDouble() / h
        for (j in 0 until h) {
            val y0 = zone.y + j * pasY
            val y1 = y0 + pasY
            for (i in 0 until l) {
                val x0 = zone.x + i * pasX
                val x1 = x0 + pasX
                var surface = 0.0
                var a = 0.0
                var r = 0.0
                var v = 0.0
                var b = 0.0
                var sy = floor(y0).toInt()
                while (sy < y1) {
                    val couvreY = min(y1, sy + 1.0) - max(y0, sy.toDouble())
                    var sx = floor(x0).toInt()
                    while (sx < x1) {
                        val couvre = couvreY * (min(x1, sx + 1.0) - max(x0, sx.toDouble()))
                        surface += couvre
                        if (sx in 0 until largeur && sy in 0 until hauteur) {
                            val p = pixels[sy * largeur + sx]
                            val alpha = (p ushr 24) * couvre
                            a += alpha
                            r += ((p shr 16) and 0xFF) * alpha
                            v += ((p shr 8) and 0xFF) * alpha
                            b += (p and 0xFF) * alpha
                        }
                        sx++
                    }
                    sy++
                }
                sortie[j * l + i] = if (a <= 0.0) {
                    0
                } else {
                    argb((a / surface).roundToInt(), (r / a).roundToInt(), (v / a).roundToInt(), (b / a).roundToInt())
                }
            }
        }
        return ImageRgb(l, h, sortie)
    }

    companion object {
        fun argb(a: Int, r: Int, v: Int, b: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (v.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        /** Luminance (Rec. 601) d'un pixel ARGB, de 0 à 255. */
        fun luminance(p: Int): Int = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }
}
