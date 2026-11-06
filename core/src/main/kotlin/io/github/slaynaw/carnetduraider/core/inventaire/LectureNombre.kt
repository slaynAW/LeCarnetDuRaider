package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ce que dit la bande du bas d'une case : la [quantite] (« ×24 » : 24 ; 1 quand rien n'est écrit ; `null` si elle
 * est illisible) et le [niveau] d'une arme ou d'un accessoire (« II » : 2), quand il y en a un.
 */
data class Bande(val quantite: Int?, val niveau: Int? = null)

/**
 * La bande du bas d'une case, lue à droite : le jeu y écrit « × » suivi du nombre d'objets d'une pile, ou le
 * niveau d'une arme en chiffres romains (I à IV, sans « × »), ou rien pour un objet seul. Les signes clairs sont
 * séparés les uns des autres ; un « × » est plus petit que les chiffres et centré sur leur hauteur. Chaque chiffre
 * est comparé à des modèles : [modeles], les chiffres 0 à 9 dessinés en clair sur fond sombre (l'appli les dessine
 * avec une police proche de celle du jeu). Un chiffre trop différent de tous les modèles rend le nombre
 * illisible : mieux vaut ne pas savoir que se tromper.
 */
class LectureNombre(modeles: Map<Int, ImageRgb>) {

    /** Un signe repéré : sa boîte dans la zone lue. */
    private class Boite(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val largeur: Int get() = x1 - x0 + 1
        val hauteur: Int get() = y1 - y0 + 1
        val milieu: Double get() = (y0 + y1) / 2.0
    }

    private val glyphes: List<Pair<Int, FloatArray>> = modeles.mapNotNull { (chiffre, image) ->
        val lum = IntArray(image.pixels.size) { ImageRgb.luminance(image.pixels[it]) }
        val seuil = seuil(lum)
        val boites = signes(lum, image.largeur, image.hauteur, seuil)
        if (boites.isEmpty()) return@mapNotNull null
        val b = Boite(boites.minOf { it.x0 }, boites.minOf { it.y0 }, boites.maxOf { it.x1 }, boites.maxOf { it.y1 })
        chiffre to glyphe(lum, image.largeur, seuil, b)
    }

    /** La bande de la case [zone] de [image]. */
    fun lire(image: ImageRgb, zone: Zone): Bande {
        val x0 = max(0, zone.x + (zone.largeur * GAUCHE).roundToInt())
        val x1 = min(image.largeur, zone.x + (zone.largeur * DROITE).roundToInt())
        val y0 = max(0, zone.y + (zone.hauteur * HAUT).roundToInt())
        val y1 = min(image.hauteur, zone.y + (zone.hauteur * BAS).roundToInt())
        val l = x1 - x0
        val h = y1 - y0
        if (l < 6 || h < 6) return Bande(null)
        val lum = IntArray(l * h) { image.luminance(x0 + it % l, y0 + it / l) }
        val seuil = seuil(lum)
        // Le cadre de la case (un trait clair en bas, à droite, un peu penché si l'écran est filmé de biais)
        // n'est pas du texte : effacé avant de lire.
        effacerCadre(lum, l, h, seuil)
        // Les signes : ni trop petits (du bruit), ni collés au haut de la zone (le bas de l'icône). Rien d'écrit :
        // un seul objet. Quelque chose d'écrit mais pas de ligne nette : illisible.
        val tous = signes(lum, l, h, seuil).filter { it.hauteur >= 0.2 * h && it.y0 > 0 }
        if (tous.isEmpty()) return Bande(1)
        // La ligne écrite finit le plus à droite ; ses signes se suivent de près, sur la même hauteur.
        val dernier = tous.filter { it.hauteur >= 0.35 * h }.maxByOrNull { it.x1 } ?: return Bande(null)
        val ligne = mutableListOf(dernier)
        for (b in tous.filter { it.x1 < dernier.x0 }.sortedByDescending { it.x1 }) {
            val premier = ligne.first()
            if (premier.x0 - b.x1 > 0.9 * dernier.hauteur) break
            if (b.milieu < dernier.y0 || b.milieu > dernier.y1) continue
            ligne.add(0, b)
        }
        // Une tache trop large pour un seul signe : des signes que le flou de la caméra a collés (« ×6 », « 60 »).
        // Un seuil plus haut les sépare ; ses morceaux qui se touchent (un « 0 » qui s'ouvre) restent ensemble.
        val boite = Boite(ligne.minOf { it.x0 }, ligne.minOf { it.y0 }, ligne.maxOf { it.x1 }, ligne.maxOf { it.y1 })
        val valeurs = (boite.y0..boite.y1).flatMap { y -> (boite.x0..boite.x1).map { x -> lum[y * l + x] } }.sorted()
        val fond = valeurs[valeurs.size * 3 / 10]
        val clair = valeurs[(valeurs.size * 98 / 100).coerceAtMost(valeurs.size - 1)]
        val haut = max(seuil, fond + ((clair - fond) * NET).roundToInt())
        val hauteurLigne = ligne.maxOf { it.hauteur }
        val signes = ligne.flatMap { b -> if (b.largeur <= LARGE * hauteurLigne) listOf(b) else decouper(lum, l, h, b, haut) }
        val haute = signes.maxOf { it.hauteur }
        // Le « × » : plus petit que les chiffres, centré sur leur hauteur (le flou l'aplatit parfois un peu).
        val fois = signes.size >= 2 && signes[0].let { x ->
            x.hauteur <= 0.8 * signes.drop(1).maxOf { it.hauteur } && x.largeur <= 0.85 * haute &&
                abs(x.milieu - signes.drop(1).map { it.milieu }.average()) <= 0.25 * haute
        }
        if (fois) return Bande(nombre(lum, l, seuil, signes.drop(1)))
        romain(lum, l, seuil, signes)?.let { return Bande(1, it) }
        // Ni « × » ni chiffres romains : un « × » noyé dans le flou, ou autre chose. Illisible si ce sont des chiffres.
        return Bande(nombre(lum, l, seuil, signes))
    }

    /** Les signes collés de la tache [b], séparés au seuil [haut] ; ses morceaux qui se touchent restent ensemble. */
    private fun decouper(lum: IntArray, l: Int, h: Int, b: Boite, haut: Int): List<Boite> {
        val morceaux = signes(lum, l, h, haut).filter { it.x0 >= b.x0 && it.x1 <= b.x1 && it.y0 >= b.y0 && it.y1 <= b.y1 }.sortedBy { it.x0 }
        val groupes = mutableListOf<Boite>()
        for (m in morceaux) {
            val d = groupes.lastOrNull()
            if (d != null && m.x0 <= d.x1 + 1) {
                groupes[groupes.size - 1] = Boite(min(d.x0, m.x0), min(d.y0, m.y0), max(d.x1, m.x1), max(d.y1, m.y1))
            } else {
                groupes += m
            }
        }
        return if (groupes.size >= 2) groupes else listOf(b)
    }

    /**
     * Efface le cadre de la case, où qu'il tombe dans la zone. Colonne par colonne, la tache claire la plus basse,
     * si elle est fine (trois pixels au plus) : sur presque toute la largeur, ces taches forment le trait du bas
     * du cadre, à la même hauteur à deux pixels près (il peut être un peu penché) ; elles s'effacent, avec tout
     * ce qui est en dessous. Le texte, au-dessus et séparé du trait, reste. De même, ligne par ligne, pour le
     * trait de droite.
     */
    private fun effacerCadre(lum: IntArray, l: Int, h: Int, seuil: Int) {
        fun encre(x: Int, y: Int): Boolean = lum[y * l + x] > seuil
        // Le trait du bas.
        val bas = IntArray(l) { -1 }
        for (x in 0 until l) {
            var y = h - 1
            while (y >= h * 55 / 100 && !encre(x, y)) y--
            if (y < h * 55 / 100) continue
            var haut = y
            while (haut > 0 && encre(x, haut - 1)) haut--
            if (y - haut < 3) bas[x] = haut
        }
        val lignes = bas.filter { it >= 0 }.sorted()
        if (lignes.size >= 0.7 * l) {
            val milieu = lignes[lignes.size / 2]
            for (x in 0 until l) if (bas[x] >= 0 && abs(bas[x] - milieu) <= 2) for (y in bas[x] until h) lum[y * l + x] = 0
        }
        // Le trait de droite.
        val droite = IntArray(h) { -1 }
        for (y in 0 until h) {
            var x = l - 1
            while (x >= l * 3 / 4 && !encre(x, y)) x--
            if (x < l * 3 / 4) continue
            var gauche = x
            while (gauche > 0 && encre(gauche - 1, y)) gauche--
            if (x - gauche < 3) droite[y] = gauche
        }
        val colonnes = droite.filter { it >= 0 }.sorted()
        if (colonnes.size >= 0.6 * h) {
            val milieu = colonnes[colonnes.size / 2]
            for (y in 0 until h) if (droite[y] >= 0 && abs(droite[y] - milieu) <= 2) for (x in droite[y] until l) lum[y * l + x] = 0
        }
    }

    /** Le nombre écrit par les chiffres [boites] (de gauche à droite), ou `null` si l'un d'eux est illisible. */
    private fun nombre(lum: IntArray, l: Int, seuil: Int, boites: List<Boite>): Int? {
        if (boites.isEmpty() || boites.size > 4 || glyphes.isEmpty()) return null
        var valeur = 0
        for (b in boites) {
            if (b.largeur > 1.2 * b.hauteur) return null
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
     * Le niveau écrit en chiffres romains par les signes [boites] (I, II, III, IV, V), ou `null` si ce n'en est
     * pas. Tous les signes ont la hauteur de la ligne (pas de « × ») ; au milieu de leur hauteur, ce ne sont que
     * des traits fins : ceux d'un « I » sont droits (au même endroit à 35 %, 50 % et 65 % de la hauteur), ceux
     * d'un « V » se rapprochent vers le bas. Les empattements, en haut et en bas, relient les traits : seul le
     * milieu compte.
     */
    private fun romain(lum: IntArray, l: Int, seuil: Int, boites: List<Boite>): Int? {
        val b = Boite(boites.minOf { it.x0 }, boites.minOf { it.y0 }, boites.maxOf { it.x1 }, boites.maxOf { it.y1 })
        if (b.hauteur < 7 || boites.any { it.hauteur < 0.8 * b.hauteur }) return null
        fun traits(fraction: Double): List<Double> {
            val y = b.y0 + (b.hauteur * fraction).roundToInt()
            // Le seuil de cette ligne : à mi-chemin entre son plus sombre et son plus clair (le flou ne remplit pas
            // tout à fait l'espace entre deux traits).
            val ligne = (b.x0..b.x1).map { lum[y * l + it] }
            if (ligne.max() - ligne.min() < 40) return emptyList()
            val milieu = max(seuil, (ligne.max() + ligne.min()) / 2)
            val traits = mutableListOf<Double>()
            var debut = -1
            for (x in b.x0..b.x1 + 1) {
                val encre = x <= b.x1 && lum[y * l + x] > milieu
                if (encre && debut < 0) debut = x
                if (!encre && debut >= 0) {
                    // Un trait épais n'est pas un chiffre romain (les chiffres arabes ont des courbes et des barres).
                    if (x - debut > max(2.0, 0.3 * b.hauteur)) return emptyList()
                    traits += (debut + x - 1) / 2.0
                    debut = -1
                }
            }
            return traits
        }
        val haut = traits(0.35)
        val milieu = traits(0.5)
        val bas = traits(0.65)
        if (haut.isEmpty() || milieu.isEmpty() || bas.isEmpty() || haut.size > 3) return null
        val ecart = max(1.0, b.hauteur / 8.0)
        fun droits(a: List<Double>, c: List<Double>) = a.size == c.size && a.indices.all { abs(a[it] - c[it]) <= ecart }
        // I, II, III : des traits droits.
        if (droits(haut, milieu) && droits(milieu, bas)) return if (haut.size == 1 && b.largeur > 0.75 * b.hauteur) null else haut.size
        // Un V : deux traits qui se rapprochent nettement vers le bas ; IV : un I droit devant.
        fun v(a: List<Double>, c: List<Double>): Boolean = a.size == 2 && c.size in 1..2 && (a[1] - a[0]) - (c.last() - c.first()) > ecart
        return when {
            v(haut, bas) -> 5
            haut.size == 3 && abs(haut[0] - bas[0]) <= ecart && v(haut.drop(1), bas.drop(1)) -> 4
            else -> null
        }
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
        /**
         * La partie lue de la case : la droite de la bande du bas, jusqu'au cadre (la case trouvée s'arrête parfois
         * juste à l'intérieur de son cadre ; le cadre, lui, est effacé avant de lire).
         */
        private const val GAUCHE = 0.4
        private const val DROITE = 1.05
        private const val HAUT = 0.76
        private const val BAS = 1.05

        /** Taille des chiffres comparés. */
        private const val GL = 8
        private const val GH = 12

        /** Un signe est clair : le seuil d'encre reste entre ces deux niveaux de luminance. */
        private const val SEUIL_MIN = 100
        private const val SEUIL_MAX = 200

        /** Dans la ligne écrite, le seuil qui sépare les signes : à cette part du chemin entre le fond et le plus clair. */
        private const val NET = 0.68

        /** Une tache plus large que cette part de la hauteur des chiffres en contient plusieurs. */
        private const val LARGE = 0.85

        private const val SEUIL_CHIFFRE = 0.55
        private const val ECART_CHIFFRE = 0.06

        private fun seuil(lum: IntArray): Int = otsu(lum).coerceIn(SEUIL_MIN, SEUIL_MAX)

        /** Les signes clairs d'une zone ([l] × [h]) : chaque tache de pixels plus clairs que [seuil], qui se touchent. */
        private fun signes(lum: IntArray, l: Int, h: Int, seuil: Int): List<Boite> {
            val vu = BooleanArray(l * h)
            val boites = mutableListOf<Boite>()
            val pile = IntArray(l * h)
            for (depart in 0 until l * h) {
                if (vu[depart] || lum[depart] <= seuil) continue
                var n = 0
                pile[n++] = depart
                vu[depart] = true
                var x0 = l
                var y0 = h
                var x1 = -1
                var y1 = -1
                var taille = 0
                while (n > 0) {
                    val p = pile[--n]
                    val x = p % l
                    val y = p / l
                    taille++
                    x0 = min(x0, x)
                    x1 = max(x1, x)
                    y0 = min(y0, y)
                    y1 = max(y1, y)
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = x + dx
                        val yy = y + dy
                        if (xx !in 0 until l || yy !in 0 until h) continue
                        val q = yy * l + xx
                        if (!vu[q] && lum[q] > seuil) {
                            vu[q] = true
                            pile[n++] = q
                        }
                    }
                }
                if (taille >= 4) boites += Boite(x0, y0, x1, y1)
            }
            // Les morceaux d'un même signe, l'un au-dessus de l'autre (un trait coupé par le flou), n'en font qu'un.
            boites.sortBy { it.x0 }
            val fusion = mutableListOf<Boite>()
            for (b in boites) {
                val d = fusion.lastOrNull()
                val chevauche = d != null && min(d.x1, b.x1) - max(d.x0, b.x0) >= 0.6 * min(d.largeur, b.largeur)
                if (d != null && chevauche) {
                    fusion[fusion.size - 1] = Boite(min(d.x0, b.x0), min(d.y0, b.y0), max(d.x1, b.x1), max(d.y1, b.y1))
                } else {
                    fusion += b
                }
            }
            return fusion
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
