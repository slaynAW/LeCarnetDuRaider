package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.ceil
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
        // Ligne par ligne de sortie : chaque ligne d'entrée qu'elle recouvre est d'abord réduite en largeur, puis
        // ajoutée au prorata de la hauteur couverte ; couleurs prémultipliées par l'opacité.
        val colonnes = Couverture(zone.x, zone.largeur, l, largeur)
        val lignes = Couverture(zone.y, zone.hauteur, h, hauteur)
        val surface = colonnes.pas * lignes.pas
        val sa = DoubleArray(l)
        val sr = DoubleArray(l)
        val sv = DoubleArray(l)
        val sb = DoubleArray(l)
        val sortie = IntArray(l * h)
        for (j in 0 until h) {
            sa.fill(0.0)
            sr.fill(0.0)
            sv.fill(0.0)
            sb.fill(0.0)
            val partsY = lignes.parts[j]
            for (k in partsY.indices) {
                val ligne = (lignes.premier[j] + k) * largeur
                val py = partsY[k]
                for (i in 0 until l) {
                    val premier = colonnes.premier[i]
                    val partsX = colonnes.parts[i]
                    for (m in partsX.indices) {
                        val p = pixels[ligne + premier + m]
                        val poids = (p ushr 24) * partsX[m] * py
                        sa[i] += poids
                        sr[i] += ((p shr 16) and 0xFF) * poids
                        sv[i] += ((p shr 8) and 0xFF) * poids
                        sb[i] += (p and 0xFF) * poids
                    }
                }
            }
            for (i in 0 until l) {
                val a = sa[i]
                sortie[j * l + i] = if (a <= 0.0) 0 else argb((a / surface).roundToInt(), (sr[i] / a).roundToInt(), (sv[i] / a).roundToInt(), (sb[i] / a).roundToInt())
            }
        }
        return ImageRgb(l, h, sortie)
    }

    /**
     * Le long d'un axe : pour chacun des [n] pixels de sortie qui couvrent [etendue] pixels à partir de [depart],
     * le premier pixel d'entrée qu'il recouvre et la part recouverte de chacun (ceux hors de l'image, de 0 à
     * [taille] − 1, ne comptent pas : ils sont transparents).
     */
    private class Couverture(depart: Int, etendue: Int, n: Int, taille: Int) {
        val pas = etendue.toDouble() / n
        val premier = IntArray(n)
        val parts: Array<DoubleArray>

        init {
            parts = Array(n) { i ->
                val debut = depart + i * pas
                val fin = debut + pas
                val p0 = max(0, floor(debut).toInt())
                val p1 = min(taille - 1, ceil(fin).toInt() - 1)
                premier[i] = p0
                if (p1 < p0) {
                    DoubleArray(0)
                } else {
                    DoubleArray(p1 - p0 + 1) { k -> min(fin, p0 + k + 1.0) - max(debut, (p0 + k).toDouble()) }
                }
            }
        }
    }

    companion object {
        fun argb(a: Int, r: Int, v: Int, b: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (v.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        /** Luminance (Rec. 601) d'un pixel ARGB, de 0 à 255. */
        fun luminance(p: Int): Int = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }
}
