package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ce que la reconnaissance a trouvé dans une case : les objets possibles (plusieurs quand ils ont exactement la
 * même icône), la ressemblance avec leur icône (de -1 à 1), et si elle est assez nette pour être sûre.
 */
data class Reconnaissance(val objetIds: List<String>, val score: Double, val sur: Boolean)

/**
 * Reconnaît l'objet d'une case en la comparant aux icônes officielles que l'appli contient déjà ([icones] :
 * identifiant → icône détourée). Le coin en bas à droite, où s'écrit le nombre, ne compte jamais.
 *
 * 1. Classement rapide de toutes les icônes : la case est comparée à chacune sous son détourage seulement (le
 *    fond de la case, couleur de rareté, ne compte pas), canal par canal (rouge, vert, bleu), centrés et normés,
 *    ce qui efface les écarts de luminosité et de balance des blancs de la caméra. Les plus proches sont
 *    essayées avec de petits décalages et changements d'échelle (la grille n'est jamais trouvée au pixel près).
 * 2. Pour départager les plus proches : chaque icône est posée sur le fond de la case (estimé là où l'icône est
 *    transparente, en dégradé de haut en bas), et c'est la case entière qui doit ressembler à ce montage. Une
 *    autre icône, avec des formes là où celle-ci n'en a pas, ou l'inverse, s'en écarte nettement.
 *
 * La réponse n'est « sûre » que si la meilleure icône ressemble assez à la case et nettement plus que la
 * suivante : sinon la case est « pas sûre » et n'est jamais comptée à vendre. Les seuils sont à régler sur de
 * vraies images filmées.
 */
class Reconnaisseur(icones: Map<String, ImageRgb>) {

    /** Une icône réduite : son opacité et ses trois canaux, et les mêmes centrés et normés sous son détourage. */
    private class Signature(val poids: FloatArray, val sommePoids: Double, val normes: Array<FloatArray>, val alpha: FloatArray, val couleurs: Array<FloatArray>)

    private class Reference(val objetIds: List<String>, val grossiere: Signature, val fine: Signature)

    /** Clé d'égalité exacte des pixels d'une icône réduite : deux objets à la même icône ne font qu'une référence. */
    private class Cle(val pixels: IntArray) {
        override fun equals(other: Any?): Boolean = other is Cle && pixels.contentEquals(other.pixels)
        override fun hashCode(): Int = pixels.contentHashCode()
    }

    private val references: List<Reference> = icones.entries
        .groupBy({ Cle(it.value.echantillon(it.value.zone, FINE, FINE).pixels) }, { it.key })
        .mapNotNull { (_, ids) ->
            val icone = icones.getValue(ids.first())
            val grossiere = signature(icone, GROSSIERE) ?: return@mapNotNull null
            val fine = signature(icone, FINE) ?: return@mapNotNull null
            Reference(ids.sorted(), grossiere, fine)
        }

    /** Nombre d'icônes différentes connues (les objets qui partagent une icône n'en font qu'une). */
    val taille: Int get() = references.size

    fun reconnaitre(image: ImageRgb, zone: Zone): Reconnaissance {
        if (references.isEmpty()) return Reconnaissance(emptyList(), 0.0, false)
        val interieur = zone.interieur(MARGE_CASE)
        val grossiere = canaux(image.echantillon(interieur, GROSSIERE, GROSSIERE))
        val candidats = references.indices
            .map { it to correlationSousDetourage(references[it].grossiere, grossiere) }
            .sortedByDescending { it.second }
            .take(CANDIDATS)
            .map { it.first }

        // Pour chaque candidate, le meilleur cadrage de la case (petits décalages, petites échelles).
        val meilleurs = DoubleArray(candidats.size) { -2.0 }
        val cadrages = arrayOfNulls<Array<FloatArray>>(candidats.size)
        val pasPixel = interieur.largeur.toDouble() / FINE
        for (echelle in ECHELLES) {
            val l = interieur.largeur * echelle
            val h = interieur.hauteur * echelle
            for (dy in -1..1) for (dx in -1..1) {
                val x = interieur.x + (interieur.largeur - l) / 2 + dx * pasPixel
                val y = interieur.y + (interieur.hauteur - h) / 2 + dy * pasPixel
                val essai = canaux(image.echantillon(Zone(x.roundToInt(), y.roundToInt(), l.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)), FINE, FINE))
                candidats.forEachIndexed { rang, i ->
                    val s = correlationSousDetourage(references[i].fine, essai)
                    if (s > meilleurs[rang]) {
                        meilleurs[rang] = s
                        cadrages[rang] = essai
                    }
                }
            }
        }
        // Puis la case entière, comparée à chaque icône posée sur le fond de la case.
        val scores = candidats.indices.map { rang -> correlationSurFond(references[candidats[rang]].fine, cadrages[rang]!!) }
        val ordre = scores.indices.sortedByDescending { scores[it] }
        val premier = scores[ordre[0]]
        val second = ordre.getOrNull(1)?.let { scores[it] } ?: -1.0
        val sur = premier >= SEUIL_SCORE && premier - second >= SEUIL_ECART
        return Reconnaissance(references[candidats[ordre[0]]].objetIds, premier, sur)
    }

    private fun signature(icone: ImageRgb, t: Int): Signature? {
        val r = icone.echantillon(icone.zone, t, t)
        val alpha = FloatArray(t * t) { k -> (r.pixels[k] ushr 24) / 255f }
        val poids = FloatArray(t * t) { k -> if (coinDuNombre(k % t, k / t, t)) 0f else alpha[k] }
        val somme = poids.sumOf { it.toDouble() }
        if (somme < t * t * PART_OPAQUE_MIN) return null
        val couleurs = Array(3) { c -> FloatArray(t * t) { k -> canal(r.pixels[k], c).toFloat() } }
        val normes = Array(3) { c ->
            val v = couleurs[c]
            var moyenne = 0.0
            for (k in v.indices) moyenne += poids[k] * v[k]
            moyenne /= somme
            var variance = 0.0
            for (k in v.indices) variance += poids[k] * (v[k] - moyenne) * (v[k] - moyenne)
            val ecart = sqrt(variance / somme)
            if (ecart < 1.0) FloatArray(t * t) else FloatArray(t * t) { k -> ((v[k] - moyenne) / ecart).toFloat() }
        }
        return Signature(poids, somme, normes, alpha, couleurs)
    }

    /** Corrélation de la case avec l'icône, sous son détourage, moyennée sur les trois canaux (de -1 à 1). */
    private fun correlationSousDetourage(s: Signature, cellule: Array<FloatArray>): Double {
        var total = 0.0
        for (c in 0 until 3) {
            val n = s.normes[c]
            val v = cellule[c]
            var swv = 0.0
            var swv2 = 0.0
            var swnv = 0.0
            for (k in s.poids.indices) {
                val w = s.poids[k]
                if (w == 0f) continue
                val x = v[k].toDouble()
                swv += w * x
                swv2 += w * x * x
                swnv += w * n[k] * x
            }
            val moyenne = swv / s.sommePoids
            val variance = swv2 / s.sommePoids - moyenne * moyenne
            if (variance > 1.0) total += swnv / (s.sommePoids * sqrt(variance))
        }
        return total / 3
    }

    /**
     * Corrélation de la case entière avec l'icône posée sur le fond de la case, moyennée sur les trois canaux. Le
     * fond est estimé canal par canal, en dégradé de haut en bas, là où l'icône est transparente.
     */
    private fun correlationSurFond(s: Signature, cellule: Array<FloatArray>): Double {
        val t = FINE
        var total = 0.0
        for (c in 0 until 3) {
            val v = cellule[c]
            // Fond : droite des moindres carrés (luminance selon la hauteur), pondérée par la transparence.
            var su = 0.0
            var suy = 0.0
            var suy2 = 0.0
            var suv = 0.0
            var suyv = 0.0
            var sv = 0.0
            var n = 0
            for (k in v.indices) {
                if (coinDuNombre(k % t, k / t, t)) continue
                val u = (1 - s.alpha[k]) * (1 - s.alpha[k])
                val y = (k / t).toDouble() / t
                su += u
                suy += u * y
                suy2 += u * y * y
                suv += u * v[k]
                suyv += u * y * v[k]
                sv += v[k]
                n++
            }
            val det = su * suy2 - suy * suy
            val (a, b) = when {
                su < 1.0 -> (sv / n) to 0.0
                det < 1e-6 -> (suv / su) to 0.0
                else -> ((suy2 * suv - suy * suyv) / det) to ((su * suyv - suy * suv) / det)
            }
            // Le montage, puis sa corrélation avec la case.
            var sp = 0.0
            var sp2 = 0.0
            var sx = 0.0
            var sx2 = 0.0
            var spx = 0.0
            for (k in v.indices) {
                if (coinDuNombre(k % t, k / t, t)) continue
                val y = (k / t).toDouble() / t
                val p = s.alpha[k] * s.couleurs[c][k] + (1 - s.alpha[k]) * (a + b * y)
                val x = v[k].toDouble()
                sp += p
                sp2 += p * p
                sx += x
                sx2 += x * x
                spx += p * x
            }
            val vp = sp2 - sp * sp / n
            val vx = sx2 - sx * sx / n
            if (vp > n && vx > n) total += (spx - sp * sx / n) / sqrt(vp * vx)
        }
        return total / 3
    }

    private fun canaux(image: ImageRgb): Array<FloatArray> =
        Array(3) { c -> FloatArray(image.pixels.size) { k -> canal(image.pixels[k], c).toFloat() } }

    private fun canal(p: Int, c: Int): Int = (p shr (16 - 8 * c)) and 0xFF

    companion object {
        /** Côté des images comparées : grossière pour trier toutes les icônes, fine pour départager les plus proches. */
        private const val GROSSIERE = 10
        private const val FINE = 20

        /** Icônes gardées après la comparaison grossière. */
        private const val CANDIDATS = 12

        /** Bord de la case laissé de côté (son cadre). */
        private const val MARGE_CASE = 0.04

        private val ECHELLES = doubleArrayOf(0.9, 1.0, 1.1)

        /** Une icône presque toute transparente ne peut servir de référence. */
        private const val PART_OPAQUE_MIN = 0.05

        /** Ressemblance minimale, et avance minimale sur l'icône suivante, pour une réponse sûre. */
        const val SEUIL_SCORE = 0.8
        const val SEUIL_ECART = 0.1

        /** Le coin en bas à droite d'une case, où s'écrit le nombre d'objets : il ne compte pas dans la comparaison. */
        fun coinDuNombre(i: Int, j: Int, t: Int): Boolean = i >= 0.6 * t && j >= 0.66 * t
    }
}
