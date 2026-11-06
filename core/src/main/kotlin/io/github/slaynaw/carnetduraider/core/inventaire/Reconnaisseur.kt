package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Rarete
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ce que la reconnaissance a trouvé dans une case : les objets possibles (plusieurs quand leurs icônes sont les
 * mêmes ou presque), la ressemblance avec leur icône (de 0 à 1), et si elle est assez nette pour être sûre.
 */
data class Reconnaissance(val objetIds: List<String>, val score: Double, val sur: Boolean)

/**
 * Reconnaît l'objet d'une case en la comparant aux icônes officielles que l'appli contient déjà ([icones] :
 * identifiant → icône détourée ; [raretes] : la rareté de chaque objet, quand le jeu en donne une).
 *
 * Dans le jeu, l'icône occupe le haut de la case ([ICONE] : un carré de 76 % de la largeur, centré, juste sous le
 * cadre) ; en dessous, une bande sombre porte la catégorie et le nombre, et ne compte pas. Le fond de la case est
 * sombre, avec un halo de la couleur de la rareté qui monte du coin en bas à gauche.
 *
 * Pour chaque icône essayée, la case filmée est expliquée au mieux, canal par canal (rouge, vert, bleu), comme
 * cette icône posée sur un fond qui varie doucement : « contraste × icône + décalage » là où l'icône est opaque,
 * un fond en pente douce là où elle est transparente (ajustement aux moindres carrés). Le contraste et le
 * décalage effacent la luminosité et la balance des blancs de la caméra ; la pente, le halo. La ressemblance est
 * la part de la case que ce montage explique (de 0 à 1) : une autre icône, avec des formes là où celle-ci n'en a
 * pas, ou l'inverse, en explique bien moins.
 *
 * D'abord toutes les icônes de la rareté du halo (et celles sans rareté), en petit ; puis les plus proches en
 * plus grand, avec de petits décalages (une case n'est jamais trouvée au pixel près). La réponse n'est « sûre »
 * que si la meilleure icône ressemble assez à la case et nettement plus que la suivante, sauf si la suivante est
 * le même objet à un autre niveau (une arme, un accessoire : le niveau écrit dans la case choisira) ou une
 * variante du même module : ils sont alors rendus ensemble, du plus ressemblant au moins. Sinon la case est
 * « pas sûre » et n'est jamais comptée à vendre.
 */
class Reconnaisseur(icones: Map<String, ImageRgb>, raretes: Map<String, Rarete?> = emptyMap()) {

    /**
     * Une icône réduite à [t] × [t] : son opacité, ses couleurs prémultipliées, et, canal par canal, ce qui ne
     * dépend que d'elle dans l'ajustement (les produits de ses cinq colonnes et leur inverse).
     */
    private class Signature(val t: Int, icone: ImageRgb) {
        val alpha: FloatArray
        val alphaX: FloatArray
        val alphaY: FloatArray
        val couleurs: Array<FloatArray>
        val gram: Array<DoubleArray>
        val inverse: Array<DoubleArray>
        val opacite: Double

        init {
            val r = icone.echantillon(icone.zone, t, t)
            val n = t * t
            alpha = FloatArray(n) { (r.pixels[it] ushr 24) / 255f }
            alphaX = FloatArray(n) { (alpha[it] * x(it, t)).toFloat() }
            alphaY = FloatArray(n) { (alpha[it] * y(it, t)).toFloat() }
            couleurs = Array(3) { c -> FloatArray(n) { alpha[it] * canal(r.pixels[it], c) } }
            opacite = alpha.sumOf { it.toDouble() }
            gram = Array(3) { DoubleArray(25) }
            inverse = Array(3) { c ->
                val g = gram[c]
                for (k in 0 until n) {
                    val colonnes = colonnes(k, c)
                    for (i in 0 until 5) for (j in 0 until 5) g[i * 5 + j] += colonnes[i] * colonnes[j]
                }
                val trace = (0 until 5).sumOf { g[it * 5 + it] }
                for (i in 0 until 5) g[i * 5 + i] += 1e-6 * trace + 1e-9
                inverser(g)
            }
        }

        /** Les cinq colonnes de l'ajustement au point [k] : icône, opacité, transparence, et sa pente en x et en y. */
        fun colonnes(k: Int, c: Int): DoubleArray {
            val a = alpha[k].toDouble()
            return doubleArrayOf(couleurs[c][k].toDouble(), a, 1 - a, (1 - a) * x(k, t), (1 - a) * y(k, t))
        }
    }

    /** Une case réduite à t × t, canal par canal, et ce qui ne dépend que d'elle dans l'ajustement. */
    private class Echantillon(val t: Int, image: ImageRgb) {
        val canaux: Array<FloatArray> = Array(3) { c -> FloatArray(t * t) { canal(image.pixels[it], c) } }
        val somme = DoubleArray(3)
        val sommeX = DoubleArray(3)
        val sommeY = DoubleArray(3)
        val carres = DoubleArray(3)

        init {
            for (c in 0 until 3) {
                val v = canaux[c]
                for (k in v.indices) {
                    somme[c] += v[k]
                    sommeX[c] += v[k] * x(k, t)
                    sommeY[c] += v[k] * y(k, t)
                    carres[c] += v[k].toDouble() * v[k]
                }
            }
        }
    }

    private class Reference(val objetIds: List<String>, val raretes: Set<Rarete?>, val petite: Signature, val grande: Signature)

    /** Clé d'égalité exacte des pixels d'une icône réduite : deux objets à la même icône ne font qu'une référence. */
    private class Cle(val pixels: IntArray) {
        override fun equals(other: Any?): Boolean = other is Cle && pixels.contentEquals(other.pixels)
        override fun hashCode(): Int = pixels.contentHashCode()
    }

    private val references: List<Reference> = icones.entries
        .groupBy({ Cle(it.value.echantillon(it.value.zone, GRANDE, GRANDE).pixels) }, { it.key })
        .mapNotNull { (_, ids) ->
            val icone = icones.getValue(ids.first())
            val grande = Signature(GRANDE, icone)
            if (grande.opacite < GRANDE * GRANDE * PART_OPAQUE_MIN) return@mapNotNull null
            Reference(ids.sorted(), ids.map { raretes[it] }.toSet(), Signature(PETITE, icone), grande)
        }

    /** Nombre d'icônes différentes connues (les objets qui partagent une icône n'en font qu'une). */
    val taille: Int get() = references.size

    fun reconnaitre(image: ImageRgb, zone: Zone, rarete: Rarete? = null): Reconnaissance {
        val classement = classer(image, zone, rarete)
        if (classement.isEmpty()) return Reconnaissance(emptyList(), 0.0, false)
        val (premier, score) = classement[0]
        // Les suivantes trop proches : une variante du même objet (rendue avec), ou un autre objet (pas sûr).
        val proches = classement.drop(1).filter { score - it.second < SEUIL_ECART }
        val (variantes, autres) = proches.partition { memeFamille(references[premier], references[it.first]) }
        val ids = (listOf(premier) + variantes.map { it.first }).flatMap { references[it].objetIds }
        return Reconnaissance(ids, score, score >= SEUIL_SCORE && autres.isEmpty())
    }

    /** Les icônes les plus proches de la case [zone], de la plus ressemblante à la moins : leurs objets et leur ressemblance. */
    internal fun classement(image: ImageRgb, zone: Zone, rarete: Rarete? = null): List<Pair<List<String>, Double>> =
        classer(image, zone, rarete).map { references[it.first].objetIds to it.second }

    /** Les icônes les plus proches de la case, de la plus ressemblante à la moins (indice de référence, ressemblance). */
    private fun classer(image: ImageRgb, zone: Zone, rarete: Rarete?): List<Pair<Int, Double>> {
        val icone = zoneIcone(zone)
        val candidates = references.indices.filter { i ->
            val r = references[i].raretes
            rarete == null || null in r || rarete in r
        }
        if (candidates.isEmpty()) return emptyList()
        val petit = Echantillon(PETITE, image.echantillon(icone, PETITE, PETITE))
        val premieres = candidates
            .map { it to ressemblance(references[it].petite, petit) }
            .sortedByDescending { it.second }
            .take(CANDIDATES)
            .map { it.first }

        // En grand, au cadrage attendu ; puis, pour les plus proches seulement, le meilleur de petits décalages.
        val pas = icone.largeur.toDouble() / GRANDE
        fun cadre(dx: Int, dy: Int) = Echantillon(
            GRANDE,
            image.echantillon(Zone((icone.x + dx * pas).roundToInt(), (icone.y + dy * pas).roundToInt(), icone.largeur, icone.hauteur), GRANDE, GRANDE),
        )
        val centre = cadre(0, 0)
        val meilleurs = DoubleArray(premieres.size) { ressemblance(references[premieres[it]].grande, centre) }
        val proches = premieres.indices.sortedByDescending { meilleurs[it] }.take(DECALEES)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val decale = cadre(dx, dy)
            for (rang in proches) {
                val s = ressemblance(references[premieres[rang]].grande, decale)
                if (s > meilleurs[rang]) meilleurs[rang] = s
            }
        }
        return premieres.indices.map { premieres[it] to meilleurs[it] }.sortedByDescending { it.second }
    }

    /**
     * Part de la case (de 0 à 1) qu'explique l'icône posée sur un fond en pente douce, ajustée aux moindres carrés
     * canal par canal. Une icône qu'il faudrait inverser (contraste négatif) n'explique rien.
     */
    private fun ressemblance(s: Signature, e: Echantillon): Double {
        var reste = 0.0
        var total = 0.0
        val n = s.t * s.t
        for (c in 0 until 3) {
            val v = e.canaux[c]
            val couleurs = s.couleurs[c]
            var bIcone = 0.0
            var bAlpha = 0.0
            var bAlphaX = 0.0
            var bAlphaY = 0.0
            for (k in 0 until n) {
                val x = v[k]
                bIcone += couleurs[k] * x
                bAlpha += s.alpha[k] * x
                bAlphaX += s.alphaX[k] * x
                bAlphaY += s.alphaY[k] * x
            }
            val b = doubleArrayOf(bIcone, bAlpha, e.somme[c] - bAlpha, e.sommeX[c] - bAlphaX, e.sommeY[c] - bAlphaY)
            val inv = s.inverse[c]
            val sol = DoubleArray(5) { i -> (0 until 5).sumOf { j -> inv[i * 5 + j] * b[j] } }
            val variance = e.carres[c] - e.somme[c] * e.somme[c] / n
            total += variance
            if (sol[0] <= 0.0) {
                reste += variance
                continue
            }
            // Reste = v·v − 2 sol·b + sol·G·sol (exact, même avec la petite régularisation).
            val g = s.gram[c]
            var sgs = 0.0
            for (i in 0 until 5) for (j in 0 until 5) sgs += sol[i] * g[i * 5 + j] * sol[j]
            val r = e.carres[c] - 2 * (0 until 5).sumOf { sol[it] * b[it] } + sgs
            reste += r.coerceIn(0.0, variance)
        }
        return if (total <= 1e-6) 0.0 else 1 - reste / total
    }

    companion object {
        /** Côté des images comparées : petite pour trier toutes les icônes, grande pour départager les plus proches. */
        private const val PETITE = 12
        private const val GRANDE = 24

        /** Icônes gardées après la comparaison en petit. */
        private const val CANDIDATES = 10

        /** Icônes essayées aussi avec de petits décalages. */
        private const val DECALEES = 4

        /** Une icône presque toute transparente ne peut servir de référence. */
        private const val PART_OPAQUE_MIN = 0.05

        /** Ressemblance minimale, et avance minimale sur une autre icône, pour une réponse sûre. */
        const val SEUIL_SCORE = 0.6
        const val SEUIL_ECART = 0.04

        /** En dessous, ce n'est pas une case d'objet entière (une case à moitié cachée par le bord de la liste). */
        const val SEUIL_CASE = 0.35

        /** Les niveaux d'une arme ou d'un accessoire (`anvil_i` à `anvil_iv`) et les variantes d'un module (`looting_mk3_…`). */
        private val NIVEAU = Regex("^(.+)_(i|ii|iii|iv|v)$")
        private val VARIANTE = Regex("^(.+_mk\\d+)_[a-z]+$")

        /** La famille d'un objet : le même objet à un autre niveau, ou une variante du même module ; `null` s'il n'en a pas. */
        fun famille(id: String): String? = NIVEAU.find(id)?.groupValues?.get(1) ?: VARIANTE.find(id)?.groupValues?.get(1)

        private fun memeFamille(a: Reference, b: Reference): Boolean {
            val fa = a.objetIds.mapNotNull { famille(it) }.toSet()
            return b.objetIds.any { famille(it) in fa }
        }

        /** Où l'icône est posée dans la case : un carré de [ICONE] de la largeur, à [ICONE_X] du bord gauche et [ICONE_Y] du haut. */
        const val ICONE = 0.76
        const val ICONE_X = 0.14
        const val ICONE_Y = 0.02

        /** La place de l'icône dans la case [zone]. */
        fun zoneIcone(zone: Zone): Zone {
            val cote = zone.largeur * ICONE
            return Zone(
                (zone.x + zone.largeur * ICONE_X).roundToInt(),
                (zone.y + zone.hauteur * ICONE_Y).roundToInt(),
                cote.roundToInt().coerceAtLeast(1),
                (zone.hauteur * ICONE).roundToInt().coerceAtLeast(1),
            )
        }

        private fun x(k: Int, t: Int): Double = (k % t + 0.5) / t
        private fun y(k: Int, t: Int): Double = (k / t + 0.5) / t

        private fun canal(p: Int, c: Int): Float = ((p shr (16 - 8 * c)) and 0xFF).toFloat()

        /** Inverse d'une matrice 5 × 5 (Gauss-Jordan), rangée ligne par ligne. */
        private fun inverser(m: DoubleArray): DoubleArray {
            val n = 5
            val a = m.copyOf()
            val inv = DoubleArray(n * n) { if (it / n == it % n) 1.0 else 0.0 }
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(a[r * n + col]) > abs(a[pivot * n + col])) pivot = r
                if (pivot != col) {
                    for (j in 0 until n) {
                        var t = a[col * n + j]
                        a[col * n + j] = a[pivot * n + j]
                        a[pivot * n + j] = t
                        t = inv[col * n + j]
                        inv[col * n + j] = inv[pivot * n + j]
                        inv[pivot * n + j] = t
                    }
                }
                val p = a[col * n + col]
                if (abs(p) < 1e-12) continue
                for (j in 0 until n) {
                    a[col * n + j] /= p
                    inv[col * n + j] /= p
                }
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r * n + col]
                    if (f == 0.0) continue
                    for (j in 0 until n) {
                        a[r * n + j] -= f * a[col * n + j]
                        inv[r * n + j] -= f * inv[col * n + j]
                    }
                }
            }
            return inv
        }
    }
}
