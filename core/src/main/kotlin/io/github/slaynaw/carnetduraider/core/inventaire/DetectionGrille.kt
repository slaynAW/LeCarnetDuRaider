package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Une case de l'inventaire : sa [ligne] et sa [colonne] (à partir de 0, dans sa grille), et sa zone dans l'image. */
data class CaseGrille(val ligne: Int, val colonne: Int, val zone: Zone)

/**
 * Les cases de l'inventaire dans une image filmée. Chaque case a un cadre : un fin trait clair (la couleur de sa
 * rareté) sur le fond sombre de la grille. On cherche donc des carrés dont les quatre côtés suivent un bord de
 * l'image presque d'un bout à l'autre (les coins, arrondis, ne comptent pas). Chaque case est trouvée seule : un
 * écran filmé un peu de biais (téléphone pas tout à fait en face, image un peu tournée) ne gêne pas, tant qu'une
 * case reste à peu près carrée. Puis seules restent les cases rangées en grille, de même taille et côte à côte
 * sur une même ligne : les boutons de l'interface (catégories, en-tête), seuls ou en colonne, sont écartés, et
 * une case à moitié cachée par le bord de la liste aussi (elle n'a pas ses quatre côtés).
 */
object DetectionGrille {
    /** La recherche se fait sur une copie dont le grand côté fait au plus ce nombre de pixels. */
    private const val COTE_TRAVAIL = 800

    /** Écart de luminance (entre les deux voisins d'un pixel) à partir duquel il y a un bord. */
    private const val SEUIL_BORD = 18

    /** Un bord compte à ce nombre de pixels près : un écran filmé un peu tourné (jusqu'à 3 degrés environ). */
    private const val TOLERANCE = 2

    /** Part de chaque côté laissée aux coins, arrondis. */
    private const val COINS = 0.15

    /** Part minimale d'un côté qui doit suivre un bord, et moyenne minimale sur les quatre côtés. */
    private const val COTE_MIN = 0.6
    private const val COTES_MIN = 0.75

    /** Part maximale d'une ligne juste à l'intérieur du cadre qui suit un bord. */
    private const val INTERIEUR_MAX = 0.75

    /** Une case fait au moins ce nombre de pixels de côté dans la copie de travail. */
    private const val CASE_MIN = 18

    /** En dessous de ce nombre de cases trouvées à la taille attendue, toutes les tailles sont essayées. */
    private const val CASES_SUIVIES = 4

    /** Pas entre deux tailles de case essayées. */
    private const val PAS_TAILLE = 1.04

    /** Écart de taille toléré entre deux cases d'une même grille. */
    private const val ECART_TAILLE = 0.15

    /**
     * Les cases de [image], de haut en bas et de gauche à droite. [tailleAttendue] : la taille des cases de
     * l'image précédente (en pixels de [image]), pour chercher plus vite autour d'elle.
     */
    fun detecter(image: ImageRgb, tailleAttendue: Int? = null): List<CaseGrille> {
        val facteur = min(1.0, COTE_TRAVAIL.toDouble() / max(image.largeur, image.hauteur))
        val travail = reduite(image, facteur)
        val bords = Bords(travail)
        val cote = min(travail.largeur, travail.hauteur) / 2.0
        var grille = tailleAttendue?.let { enGrille(chercher(bords, it * facteur * 0.8, it * facteur * 1.25)) }.orEmpty()
        // Taille inconnue (ou changée : presque rien trouvé autour d'elle) : toutes les tailles.
        if (grille.size < CASES_SUIVIES) {
            val toutes = enGrille(chercher(bords, CASE_MIN.toDouble(), cote))
            if (toutes.size > grille.size) grille = toutes
        }
        return grille.map { (ligne, colonne, b) ->
            CaseGrille(
                ligne,
                colonne,
                Zone(
                    (b.x / facteur).roundToInt(),
                    (b.y / facteur).roundToInt(),
                    max(1, (b.largeur / facteur).roundToInt()),
                    max(1, (b.hauteur / facteur).roundToInt()),
                ),
            )
        }
    }

    private fun reduite(image: ImageRgb, facteur: Double): ImageRgb =
        if (facteur >= 1.0) {
            image
        } else {
            image.echantillon(image.zone, max(1, (image.largeur * facteur).roundToInt()), max(1, (image.hauteur * facteur).roundToInt()))
        }

    /** Un carré trouvé : sa place, et la part de ses quatre côtés qui suit un bord. */
    private class Boite(val x: Int, val y: Int, val largeur: Int, val hauteur: Int, val score: Double) {
        val cx: Double get() = x + largeur / 2.0
        val cy: Double get() = y + hauteur / 2.0
    }

    /**
     * Les bords d'une image : verticaux (l'image change de gauche à droite) et horizontaux, chacun compté à
     * [TOLERANCE] pixels près (un trait fin n'est jamais pile sur la même colonne d'un bout à l'autre), et leurs
     * cumuls le long des colonnes et des lignes, pour savoir en un calcul quelle part d'un segment suit un bord.
     */
    private class Bords(image: ImageRgb) {
        val l = image.largeur
        val h = image.hauteur
        private val cumulColonnes = IntArray(l * (h + 1))
        private val cumulLignes = IntArray((l + 1) * h)

        init {
            val lum = IntArray(l * h) { ImageRgb.luminance(image.pixels[it]) }
            val vertical = BooleanArray(l * h)
            val horizontal = BooleanArray(l * h)
            for (y in 1 until h - 1) for (x in 1 until l - 1) {
                val i = y * l + x
                val gx = abs(lum[i + 1] - lum[i - 1])
                val gy = abs(lum[i + l] - lum[i - l])
                if (gx > SEUIL_BORD && gx >= gy) vertical[i] = true
                if (gy > SEUIL_BORD && gy >= gx) horizontal[i] = true
            }
            for (x in 0 until l) {
                val x0 = max(0, x - TOLERANCE)
                val x1 = min(l - 1, x + TOLERANCE)
                for (y in 0 until h) {
                    val i = y * l + x
                    var bord = false
                    for (xx in x0..x1) if (vertical[y * l + xx]) {
                        bord = true
                        break
                    }
                    cumulColonnes[i + l] = cumulColonnes[i] + if (bord) 1 else 0
                }
            }
            for (y in 0 until h) {
                val y0 = max(0, y - TOLERANCE)
                val y1 = min(h - 1, y + TOLERANCE)
                for (x in 0 until l) {
                    var bord = false
                    for (yy in y0..y1) if (horizontal[yy * l + x]) {
                        bord = true
                        break
                    }
                    cumulLignes[y * (l + 1) + x + 1] = cumulLignes[y * (l + 1) + x] + if (bord) 1 else 0
                }
            }
        }

        /** Part de la colonne [x], de la ligne [y0] à [y1] (exclue), qui suit un bord vertical. */
        fun colonne(x: Int, y0: Int, y1: Int): Double = (cumulColonnes[y1 * l + x] - cumulColonnes[y0 * l + x]).toDouble() / (y1 - y0)

        /** Part de la ligne [y], de la colonne [x0] à [x1] (exclue), qui suit un bord horizontal. */
        fun ligne(y: Int, x0: Int, x1: Int): Double = (cumulLignes[y * (l + 1) + x1] - cumulLignes[y * (l + 1) + x0]).toDouble() / (x1 - x0)
    }

    /** Les carrés de [tailleMin] à [tailleMax] pixels de côté dont les quatre côtés suivent un bord ; un seul par endroit. */
    private fun chercher(bords: Bords, tailleMin: Double, tailleMax: Double): List<Boite> {
        val l = bords.l
        val h = bords.h
        val haut = min(tailleMax, min(l, h) / 2.0)
        val bas = max(CASE_MIN * 0.5, tailleMin)
        if (haut < bas) return emptyList()
        val trouvees = mutableListOf<Boite>()
        val nombre = (ln(haut / bas) / ln(PAS_TAILLE)).toInt() + 1
        var precedente = -1
        for (k in 0 until nombre) {
            val c = (bas * PAS_TAILLE.pow(k)).roundToInt()
            if (c == precedente || c < 8) continue
            precedente = c
            val a = (c * COINS).roundToInt()
            for (y in 0 until h - c) for (x in 0 until l - c) {
                val gauche = bords.colonne(x, y + a, y + c - a)
                if (gauche < COTE_MIN) continue
                val droite = bords.colonne(x + c - 1, y + a, y + c - a)
                if (droite < COTE_MIN) continue
                val dessus = bords.ligne(y, x + a, x + c - a)
                if (dessus < COTE_MIN) continue
                val dessous = bords.ligne(y + c - 1, x + a, x + c - a)
                if (dessous < COTE_MIN) continue
                val score = (gauche + droite + dessus + dessous) / 4
                if (score < COTES_MIN) continue
                // Juste à l'intérieur du cadre, le fond de la case est calme : ni un bord partout (du bruit), ni un
                // second cadre le long d'un côté (ce carré-là englobe la case et l'espace qui la sépare de ses
                // voisines, dont il suit les cadres).
                val d = max(TOLERANCE + 2, c / 10)
                val dedans = maxOf(
                    bords.colonne(x + d, y + a, y + c - a),
                    bords.colonne(x + c - 1 - d, y + a, y + c - a),
                    bords.ligne(y + d, x + a, x + c - a),
                    bords.ligne(y + c - 1 - d, x + a, x + c - a),
                )
                if (dedans > INTERIEUR_MAX) continue
                // Ni deux cadres en croix au milieu, l'un vertical, l'autre horizontal : ce carré-là couvre quatre
                // cases (un bloc de deux sur deux, dans une image floue où les cases elles-mêmes se voient mal).
                val m = (c * 0.08).roundToInt()
                val vertical = (c / 2 - m..c / 2 + m).maxOf { j -> bords.colonne(x + j, y + a, y + c - a) }
                val horizontal = (c / 2 - m..c / 2 + m).maxOf { j -> bords.ligne(y + j, x + a, x + c - a) }
                if (min(vertical, horizontal) <= INTERIEUR_MAX) trouvees += Boite(x, y, c, c, score)
            }
        }
        // Les meilleures d'abord ; à égalité, la plus petite : une case plutôt que la case et l'espace qui la sépare
        // de ses voisines (ce carré-là aussi a ses quatre côtés sur des bords, ceux des cases voisines).
        val gardees = mutableListOf<Boite>()
        for (b in trouvees.sortedWith(compareByDescending<Boite> { it.score }.thenBy { it.largeur })) {
            if (gardees.none { conflit(it, b) }) gardees += affinee(bords, b)
        }
        return gardees
    }

    /** Chaque côté recalé à deux pixels près sur le bord le plus net (un écran filmé de biais : cases pas tout à fait carrées). */
    private fun affinee(bords: Bords, b: Boite): Boite {
        val a = (b.largeur * COINS).roundToInt()
        fun meilleur(depart: Int, limite: Int, part: (Int) -> Double): Int =
            (depart - 2..depart + 2).filter { it in 0 until limite }.maxByOrNull { part(it) - abs(it - depart) * 0.01 } ?: depart
        val y0 = b.y + a
        val y1 = b.y + b.hauteur - a
        val x0 = b.x + a
        val x1 = b.x + b.largeur - a
        val gauche = meilleur(b.x, bords.l) { bords.colonne(it, y0, y1) }
        val droite = meilleur(b.x + b.largeur - 1, bords.l) { bords.colonne(it, y0, y1) }
        val dessus = meilleur(b.y, bords.h) { bords.ligne(it, x0, x1) }
        val dessous = meilleur(b.y + b.hauteur - 1, bords.h) { bords.ligne(it, x0, x1) }
        if (droite - gauche < 8 || dessous - dessus < 8) return b
        return Boite(gauche, dessus, droite - gauche + 1, dessous - dessus + 1, b.score)
    }

    /** Part commune de deux carrés, rapportée au plus petit. */
    private fun recouvrement(a: Boite, b: Boite): Double {
        val ix = max(0, min(a.x + a.largeur, b.x + b.largeur) - max(a.x, b.x))
        val iy = max(0, min(a.y + a.hauteur, b.y + b.hauteur) - max(a.y, b.y))
        return ix.toDouble() * iy / min(a.largeur * a.hauteur, b.largeur * b.hauteur)
    }

    /**
     * Deux carrés de taille voisine qui se recouvrent : un seul est une case (la même case trouvée deux fois, ou
     * décalée d'une demi-case). Un petit carré dans une case (le dessin d'une icône) ne la fait pas disparaître.
     */
    private fun conflit(a: Boite, b: Boite): Boolean {
        val petite = min(a.largeur * a.hauteur, b.largeur * b.hauteur).toDouble()
        val grande = max(a.largeur * a.hauteur, b.largeur * b.hauteur).toDouble()
        return recouvrement(a, b) > 0.3 && petite >= 0.5 * grande
    }

    /**
     * Les carrés rangés en grille : de même taille, voisins d'un pas (un peu plus d'une case) à gauche, à droite,
     * en haut ou en bas. Un groupe de voisins n'est gardé que s'il a au moins deux cases côte à côte sur une même
     * ligne (une colonne de boutons ne compte pas). Chaque case reçoit sa ligne et sa colonne dans son groupe.
     */
    private fun enGrille(boites: List<Boite>): List<Triple<Int, Int, Boite>> {
        val n = boites.size
        val parent = IntArray(n) { it }
        fun racine(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        val coteACote = BooleanArray(n)
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = boites[i]
            val b = boites[j]
            val taille = max(a.largeur, b.largeur).toDouble()
            if (abs(a.largeur - b.largeur) > ECART_TAILLE * taille) continue
            val dx = abs(a.cx - b.cx)
            val dy = abs(a.cy - b.cy)
            val horizontal = dx in 0.95 * taille..1.45 * taille && dy <= 0.35 * taille
            val vertical = dy in 0.95 * taille..1.45 * taille && dx <= 0.35 * taille
            if (horizontal || vertical) parent[racine(i)] = racine(j)
            if (horizontal) {
                coteACote[i] = true
                coteACote[j] = true
            }
        }
        val grilles = (0 until n).groupBy { racine(it) }.values.filter { g -> g.count { coteACote[it] } >= 2 }
        // La grille principale (la plus fournie), et celles de même taille à côté (le sac à dos à côté de la
        // réserve) ; des boutons alignés, plus petits ou plus grands, ne comptent pas.
        val principale = grilles.maxWithOrNull(compareBy({ it.size }, { g -> g.sumOf { boites[it].largeur } })) ?: return emptyList()
        val taillePrincipale = mediane(principale.map { boites[it].largeur.toDouble() }) ?: return emptyList()
        val groupes = grilles.filter { g ->
            val t = mediane(g.map { boites[it].largeur.toDouble() }) ?: 0.0
            g === principale || (
                t in 0.8 * taillePrincipale..1.25 * taillePrincipale &&
                    g.none { i -> principale.any { j -> recouvrement(boites[i], boites[j]) > 0.1 } }
                )
        }
        return groupes
            .sortedWith(compareBy({ g -> g.minOf { boites[it].y } }, { g -> g.minOf { boites[it].x } }))
            .flatMap { g ->
                val taille = mediane(g.map { boites[it].largeur.toDouble() }) ?: 1.0
                val lignes = rangs(g.map { boites[it].cy }, taille)
                val colonnes = rangs(g.map { boites[it].cx }, taille)
                g.indices.map { k -> Triple(lignes[k], colonnes[k], boites[g[k]]) }
                    .sortedWith(compareBy({ it.first }, { it.second }))
            }
    }

    /** Le rang de chaque valeur : une nouvelle ligne (ou colonne) commence à plus d'une demi-case de la précédente. */
    private fun rangs(valeurs: List<Double>, taille: Double): List<Int> {
        val ordre = valeurs.indices.sortedBy { valeurs[it] }
        val rang = IntArray(valeurs.size)
        var r = 0
        for (k in ordre.indices) {
            if (k > 0 && valeurs[ordre[k]] - valeurs[ordre[k - 1]] > taille / 2) r++
            rang[ordre[k]] = r
        }
        return rang.toList()
    }

    private fun mediane(valeurs: List<Double>): Double? = valeurs.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
}
