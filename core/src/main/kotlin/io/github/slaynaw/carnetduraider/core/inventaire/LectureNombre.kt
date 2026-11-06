package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Le nombre d'objets d'une case, écrit en chiffres clairs dans son coin en bas à droite. Les chiffres sont
 * séparés par les colonnes sans encre claire, puis chacun est pris du bas vers le haut (un morceau de l'icône
 * au-dessus, séparé par une ligne sombre, ne compte pas) et comparé à des modèles : [modeles], les chiffres 0
 * à 9 dessinés en clair sur fond sombre (l'appli les dessine avec une police proche de celle du jeu). Un chiffre
 * trop différent de tous les modèles rend le nombre illisible : mieux vaut ne pas savoir que se tromper.
 */
class LectureNombre(modeles: Map<Int, ImageRgb>) {

    /** Un chiffre repéré : sa boîte dans la zone lue. */
    private class Boite(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val largeur: Int get() = x1 - x0 + 1
        val hauteur: Int get() = y1 - y0 + 1
    }

    private val glyphes: List<Pair<Int, FloatArray>> = modeles.mapNotNull { (chiffre, image) ->
        val lum = IntArray(image.pixels.size) { ImageRgb.luminance(image.pixels[it]) }
        val seuil = seuil(lum)
        val boites = chiffres(lum, image.largeur, image.hauteur, seuil)
        if (boites.isEmpty()) return@mapNotNull null
        val b = Boite(boites.minOf { it.x0 }, boites.minOf { it.y0 }, boites.maxOf { it.x1 }, boites.maxOf { it.y1 })
        chiffre to glyphe(lum, image.largeur, seuil, b)
    }

    /**
     * Le nombre écrit dans la case [zone] de [image] : 1 si la case n'en affiche pas (un seul objet), `null` s'il
     * est illisible.
     */
    fun lire(image: ImageRgb, zone: Zone): Int? {
        val x0 = max(0, zone.x + (zone.largeur * 0.45).toInt())
        val x1 = min(image.largeur, zone.droite - max(1, zone.largeur / 40))
        val y0 = max(0, zone.y + (zone.hauteur * 0.62).toInt())
        val y1 = min(image.hauteur, zone.bas - max(1, zone.hauteur / 40))
        val l = x1 - x0
        val h = y1 - y0
        if (l < 4 || h < 5 || glyphes.isEmpty()) return null
        val lum = IntArray(l * h) { image.luminance(x0 + it % l, y0 + it / l) }
        val seuil = seuil(lum)
        // Un chiffre touche presque le bas de la case, et il est plus haut que large.
        val candidates = chiffres(lum, l, h, seuil).filter { b ->
            b.hauteur >= 0.3 * h && b.y1 >= 0.7 * h && b.largeur <= 1.2 * b.hauteur
        }
        if (candidates.isEmpty()) return 1
        // Les chiffres sont alignés par le bas, serrés les uns contre les autres, et finissent tout à droite.
        val bas = candidates.maxOf { it.y1 }
        val alignees = candidates.filter { bas - it.y1 <= 0.2 * h }
        val hauteurMoyenne = alignees.map { it.hauteur }.average()
        val nombre = mutableListOf(alignees.last())
        for (b in alignees.dropLast(1).reversed()) {
            if (nombre.first().x0 - b.x1 > 0.8 * hauteurMoyenne) break
            nombre.add(0, b)
        }
        if (nombre.size > 4) return null
        var valeur = 0
        for (b in nombre) {
            val g = glyphe(lum, l, seuil, b)
            val scores = glyphes.map { it.first to correlation(g, it.second) }.sortedByDescending { it.second }
            val (chiffre, score) = scores.first()
            val second = scores.getOrNull(1)?.second ?: -1.0
            if (score < SEUIL_CHIFFRE || score - second < ECART_CHIFFRE) return null
            valeur = valeur * 10 + chiffre
        }
        return valeur.takeIf { it > 0 }
    }

    /**
     * Le chiffre de la boîte [b], ramené à une hauteur fixe (sans le déformer) et centré dans un petit cadre :
     * chaque point est la moyenne de l'encre qu'il couvre, puis l'ensemble est un peu adouci, pour que le flou de
     * la caméra et un trait plus ou moins épais comptent peu.
     */
    private fun glyphe(lum: IntArray, l: Int, seuil: Int, b: Boite): FloatArray {
        val brut = FloatArray(GL * GH)
        val echelle = min(GH.toDouble() / b.hauteur, GL.toDouble() / b.largeur)
        val decalageX = (GL - b.largeur * echelle) / 2
        val decalageY = (GH - b.hauteur * echelle) / 2
        fun encre(x: Int, y: Int): Double =
            if (x < b.x0 || x > b.x1 || y < b.y0 || y > b.y1) 0.0 else ((lum[y * l + x] - seuil + 40).toDouble() / (255 - seuil + 40)).coerceIn(0.0, 1.0)
        for (j in 0 until GH) for (i in 0 until GL) {
            // La surface de la boîte que couvre ce point.
            val x0 = b.x0 + (i - decalageX) / echelle
            val x1 = b.x0 + (i + 1 - decalageX) / echelle
            val y0 = b.y0 + (j - decalageY) / echelle
            val y1 = b.y0 + (j + 1 - decalageY) / echelle
            var somme = 0.0
            var surface = 0.0
            var sy = floor(y0).toInt()
            while (sy < y1) {
                val cy = min(y1, sy + 1.0) - max(y0, sy.toDouble())
                var sx = floor(x0).toInt()
                while (sx < x1) {
                    val c = cy * (min(x1, sx + 1.0) - max(x0, sx.toDouble()))
                    somme += c * encre(sx, sy)
                    surface += c
                    sx++
                }
                sy++
            }
            brut[j * GL + i] = if (surface > 0) (somme / surface).toFloat() else 0f
        }
        val sortie = FloatArray(GL * GH)
        for (j in 0 until GH) for (i in 0 until GL) {
            var s = 0f
            var n = 0
            for (dj in -1..1) for (di in -1..1) {
                val ii = i + di
                val jj = j + dj
                if (ii !in 0 until GL || jj !in 0 until GH) continue
                s += brut[jj * GL + ii] * (if (di == 0 && dj == 0) 4 else 1)
                n += if (di == 0 && dj == 0) 4 else 1
            }
            sortie[j * GL + i] = s / n
        }
        return sortie
    }

    companion object {
        /** Taille des chiffres comparés. */
        private const val GL = 8
        private const val GH = 12

        /** Un chiffre est clair : le seuil d'encre reste entre ces deux niveaux de luminance. */
        private const val SEUIL_MIN = 110
        private const val SEUIL_MAX = 200

        private const val SEUIL_CHIFFRE = 0.55
        private const val ECART_CHIFFRE = 0.03

        private fun seuil(lum: IntArray): Int = otsu(lum).coerceIn(SEUIL_MIN, SEUIL_MAX)

        /**
         * Les chiffres d'une zone ([l] × [h]) : chaque suite de colonnes où il y a de l'encre (plus clair que
         * [seuil]), prise depuis sa ligne d'encre la plus basse jusqu'à la première ligne vide au-dessus.
         */
        private fun chiffres(lum: IntArray, l: Int, h: Int, seuil: Int): List<Boite> {
            val encre = IntArray(l)
            for (y in 0 until h) for (x in 0 until l) if (lum[y * l + x] > seuil) encre[x]++
            val boites = mutableListOf<Boite>()
            var x = 0
            while (x < l) {
                if (encre[x] == 0) {
                    x++
                    continue
                }
                val debut = x
                while (x < l && encre[x] > 0) x++
                val fin = x - 1
                fun ligneEncree(y: Int): Boolean = (debut..fin).any { lum[y * l + it] > seuil }
                var bas = h - 1
                while (bas >= 0 && !ligneEncree(bas)) bas--
                if (bas < 0) continue
                var haut = bas
                while (haut > 0 && ligneEncree(haut - 1)) haut--
                // Les colonnes vraiment encrées sur cette hauteur : la boîte au plus juste.
                val colonnes = (debut..fin).filter { cx -> (haut..bas).any { lum[it * l + cx] > seuil } }
                if (colonnes.isEmpty()) continue
                boites += Boite(colonnes.first(), haut, colonnes.last(), bas)
            }
            return boites
        }

        /** Seuil d'Otsu : la luminance qui sépare le mieux les pixels clairs des sombres. */
        private fun otsu(lum: IntArray): Int {
            val histo = IntArray(256)
            for (v in lum) histo[v.coerceIn(0, 255)]++
            val total = lum.size
            var somme = 0.0
            for (i in 0 until 256) somme += i * histo[i].toDouble()
            var sommeFond = 0.0
            var poidsFond = 0
            var meilleur = 0.0
            var seuil = 128
            for (i in 0 until 256) {
                poidsFond += histo[i]
                if (poidsFond == 0) continue
                val poidsClair = total - poidsFond
                if (poidsClair == 0) break
                sommeFond += i * histo[i].toDouble()
                val mFond = sommeFond / poidsFond
                val mClair = (somme - sommeFond) / poidsClair
                val ecart = poidsFond.toDouble() * poidsClair * (mFond - mClair) * (mFond - mClair)
                if (ecart > meilleur) {
                    meilleur = ecart
                    seuil = i
                }
            }
            return seuil
        }

        /** Corrélation de deux chiffres ramenés au même cadre (de -1 à 1). */
        private fun correlation(a: FloatArray, b: FloatArray): Double {
            val ma = a.average()
            val mb = b.average()
            var s = 0.0
            var sa = 0.0
            var sb = 0.0
            for (k in a.indices) {
                val da = a[k] - ma
                val db = b[k] - mb
                s += da * db
                sa += da * da
                sb += db * db
            }
            return if (sa <= 1e-9 || sb <= 1e-9) 0.0 else s / sqrt(sa * sb)
        }
    }
}
