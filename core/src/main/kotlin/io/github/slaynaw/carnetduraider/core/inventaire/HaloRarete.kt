package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Rarete
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * La rareté d'un objet d'après sa case : le jeu y fait monter, du coin en bas à gauche, un halo de la couleur de
 * la rareté (gris, vert, bleu, rose, or), là où l'icône ne va presque jamais. On prend les pixels les plus
 * colorés de ce coin, et leur teinte. La caméra change un peu les couleurs (le gris y devient bleuté) : une
 * teinte entre deux raretés ne dit rien (`null`), et la reconnaissance essaie alors toutes les icônes.
 */
object HaloRarete {

    fun lire(image: ImageRgb, zone: Zone): Rarete? {
        val x0 = max(0, zone.x + (zone.largeur * 0.03).roundToInt())
        val x1 = min(image.largeur, zone.x + (zone.largeur * 0.13).roundToInt())
        val y0 = max(0, zone.y + (zone.hauteur * 0.5).roundToInt())
        val y1 = min(image.hauteur, zone.y + (zone.hauteur * 0.76).roundToInt())
        if (x1 <= x0 || y1 <= y0) return null
        val pixels = (y0 until y1).flatMap { y -> (x0 until x1).map { x -> image.pixel(x, y) } }
        // Les plus colorés (un bout d'icône, gris, ne doit pas compter) : le tiers le plus saturé.
        val tries = pixels.sortedByDescending { saturation(it) }
        val gardes = tries.take(max(1, tries.size * 3 / 10))
        val r = gardes.sumOf { (it shr 16) and 0xFF } / gardes.size.toDouble()
        val v = gardes.sumOf { (it shr 8) and 0xFF } / gardes.size.toDouble()
        val b = gardes.sumOf { it and 0xFF } / gardes.size.toDouble()
        return rarete(teinte(r, v, b), saturation(r, v, b))
    }

    /** La rareté d'une couleur de halo : [teinte] en degrés (0 à 360), [saturation] de 0 à 1. */
    fun rarete(teinte: Double, saturation: Double): Rarete? = when {
        saturation < 0.4 -> Rarete.COMMUN
        teinte in 215.0..265.0 && saturation < 0.5 -> Rarete.COMMUN
        teinte in 20.0..75.0 -> Rarete.LEGENDAIRE
        teinte in 100.0..172.0 -> Rarete.INHABITUEL
        teinte in 178.0..213.0 && saturation >= 0.45 -> Rarete.RARE
        teinte in 272.0..345.0 -> Rarete.EPIQUE
        else -> null
    }

    private fun saturation(p: Int): Double = saturation(((p shr 16) and 0xFF).toDouble(), ((p shr 8) and 0xFF).toDouble(), (p and 0xFF).toDouble())

    private fun saturation(r: Double, v: Double, b: Double): Double {
        val haut = maxOf(r, v, b)
        val bas = minOf(r, v, b)
        return if (haut <= 0.0) 0.0 else (haut - bas) / haut
    }

    private fun teinte(r: Double, v: Double, b: Double): Double {
        val haut = maxOf(r, v, b)
        val ecart = haut - minOf(r, v, b)
        if (ecart <= 0.0) return 0.0
        val h = when (haut) {
            r -> ((v - b) / ecart).mod(6.0)
            v -> (b - r) / ecart + 2
            else -> (r - v) / ecart + 4
        }
        return h * 60
    }
}
