package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Une case de la grille de l'inventaire : sa [ligne] et sa [colonne] (à partir de 0), et sa zone dans l'image. */
data class CaseGrille(val ligne: Int, val colonne: Int, val zone: Zone)

/**
 * La grille de l'inventaire dans une image filmée. Les cases sont des carrés réguliers séparés par un fin
 * espace : leurs bords font, colonne après colonne et ligne après ligne, des pics de contraste qui reviennent à
 * pas fixe. Les bords où l'image s'éclaircit et ceux où elle s'assombrit sont suivis à part : le bord d'entrée
 * et le bord de sortie d'une case tombent ainsi dans deux profils différents, même quand l'espace entre deux
 * cases est très fin. On cherche le pas (autocorrélation), puis où tombent les deux familles de bords (la plus
 * longue des deux distances entre elles est l'intérieur d'une case), puis sur quelle étendue ils se répètent.
 * Une première fois avec le contraste cumulé sur toute l'image, puis, dans la seule bande de la grille, avec le
 * contraste médian : un bord de case court tout le long de la grille, le contour d'une icône non (il peut être
 * bien plus fort, mais il ne compte plus).
 * Le téléphone doit être tenu à peu près en face de l'écran : une perspective forte déforme la grille.
 */
object DetectionGrille {
    /** Une case fait au moins ce nombre de pixels de côté dans l'image analysée. */
    private const val PAS_MIN = 12

    /** Il faut voir au moins deux cases dans chaque sens. */
    private const val CASES_MIN = 2

    /** En dessous, les bords ne se répètent pas assez nettement : ce n'est pas une grille. */
    private const val SEUIL_PERIODICITE = 0.25

    /** Écart de taille toléré entre la largeur et la hauteur d'une case (ce sont des carrés). */
    private const val ECART_CARRE = 0.3

    fun detecter(image: ImageRgb): List<CaseGrille> {
        val l = image.largeur
        val h = image.hauteur
        if (l < 3 * PAS_MIN || h < 3 * PAS_MIN) return emptyList()
        val lum = FloatArray(l * h) { ImageRgb.luminance(image.pixels[it]).toFloat() }
        // Une première fois sur toute l'image, puis sur la seule bande où la grille a été vue dans l'autre sens.
        val colonnes1 = axe(profilColonnes(lum, l, 1, h - 1, mediane = false)) ?: return emptyList()
        val lignes1 = axe(profilLignes(lum, l, h, 0, l, mediane = false)) ?: return emptyList()
        val ys = lignes1.debut.toInt().coerceIn(1, h - 2) to ceil(lignes1.fin).toInt().coerceIn(2, h - 1)
        val xs = colonnes1.debut.toInt().coerceIn(0, l - 1) to ceil(colonnes1.fin).toInt().coerceIn(1, l)
        val colonnes = axe(profilColonnes(lum, l, ys.first, ys.second, mediane = true)) ?: colonnes1
        val lignes = axe(profilLignes(lum, l, h, xs.first, xs.second, mediane = true)) ?: lignes1
        if (abs(colonnes.pas - lignes.pas) > ECART_CARRE * max(colonnes.pas, lignes.pas)) return emptyList()
        if (abs(colonnes.taille - lignes.taille) > ECART_CARRE * max(colonnes.taille, lignes.taille)) return emptyList()
        return (0 until lignes.nombre).flatMap { ligne ->
            (0 until colonnes.nombre).map { colonne ->
                val x = colonnes.debut + colonne * colonnes.pas
                val y = lignes.debut + ligne * lignes.pas
                CaseGrille(ligne, colonne, Zone(x.roundToInt(), y.roundToInt(), colonnes.taille.roundToInt(), lignes.taille.roundToInt()))
            }
        }
    }

    /** Les cases dans un sens : début de la première, pas d'une case à l'autre, taille d'une case, et combien. */
    private class Axe(val debut: Double, val pas: Double, val taille: Double, val nombre: Int) {
        val fin: Double get() = debut + (nombre - 1) * pas + taille
    }

    /** Les bords dans un sens : où l'image s'éclaircit ([monte]) et où elle s'assombrit ([descend]). */
    private class Profil(val monte: DoubleArray, val descend: DoubleArray)

    /**
     * Contraste horizontal de chaque colonne, entre les lignes [y0] et [y1] : les bords verticaux. Cumulé, ou
     * [mediane] (la valeur du milieu le long de la colonne).
     */
    private fun profilColonnes(lum: FloatArray, l: Int, y0: Int, y1: Int, mediane: Boolean): Profil {
        val monte = DoubleArray(l)
        val descend = DoubleArray(l)
        val valeurs = Valeurs()
        for (x in 1 until l - 1) {
            valeurs.vider()
            for (y in y0 until y1) valeurs.ajouter(lum[y * l + x + 1] - lum[y * l + x - 1])
            monte[x] = valeurs.monte(mediane)
            descend[x] = valeurs.descend(mediane)
        }
        return Profil(monte, descend)
    }

    /**
     * Contraste vertical de chaque ligne, entre les colonnes [x0] et [x1] : les bords horizontaux. Cumulé, ou
     * [mediane] (la valeur du milieu le long de la ligne).
     */
    private fun profilLignes(lum: FloatArray, l: Int, h: Int, x0: Int, x1: Int, mediane: Boolean): Profil {
        val monte = DoubleArray(h)
        val descend = DoubleArray(h)
        val valeurs = Valeurs()
        for (y in 1 until h - 1) {
            valeurs.vider()
            for (x in x0 until x1) valeurs.ajouter(lum[(y + 1) * l + x] - lum[(y - 1) * l + x])
            monte[y] = valeurs.monte(mediane)
            descend[y] = valeurs.descend(mediane)
        }
        return Profil(monte, descend)
    }

    /** Les écarts de luminance le long d'une ligne ou d'une colonne : leur somme ou leur médiane, par signe. */
    private class Valeurs {
        private val histoMonte = IntArray(256)
        private val histoDescend = IntArray(256)
        private var sommeMonte = 0.0
        private var sommeDescend = 0.0
        private var n = 0

        fun vider() {
            histoMonte.fill(0)
            histoDescend.fill(0)
            sommeMonte = 0.0
            sommeDescend = 0.0
            n = 0
        }

        fun ajouter(d: Float) {
            n++
            val v = d.toInt().coerceIn(-255, 255)
            if (d > 0) {
                sommeMonte += d
                histoMonte[v]++
                histoDescend[0]++
            } else {
                sommeDescend -= d
                histoDescend[-v]++
                histoMonte[0]++
            }
        }

        fun monte(mediane: Boolean): Double = if (mediane) milieu(histoMonte) else sommeMonte
        fun descend(mediane: Boolean): Double = if (mediane) milieu(histoDescend) else sommeDescend

        private fun milieu(histo: IntArray): Double {
            var cumul = 0
            for (v in 0 until 256) {
                cumul += histo[v]
                if (2 * cumul >= n) return v.toDouble()
            }
            return 0.0
        }
    }

    private fun axe(profil: Profil): Axe? {
        val n = profil.monte.size
        val pasMax = n / CASES_MIN
        if (pasMax <= PAS_MIN + 2) return null
        val monte = pics(profil.monte)
        val descend = pics(profil.descend)

        // Le pas : le premier pic de l'autocorrélation (des deux familles de bords) presque aussi haut que le plus
        // haut. Chaque famille n'a qu'un bord par case : seuls le pas et ses multiples ressortent.
        val auto = DoubleArray(pasMax + 2)
        val am = autocorrelation(monte, pasMax + 1)
        val ad = autocorrelation(descend, pasMax + 1)
        for (k in auto.indices) auto[k] = (am[k] + ad[k]) / 2
        val maxima = (PAS_MIN..min(pasMax, n - 3)).filter { auto[it] > 0 && auto[it] >= auto[it - 1] && auto[it] >= auto[it + 1] }
        val plusHaut = maxima.maxOfOrNull { auto[it] } ?: return null
        if (plusHaut < SEUIL_PERIODICITE) return null
        val k = maxima.first { auto[it] >= 0.7 * plusHaut }
        val courbure = auto[k - 1] - 2 * auto[k] + auto[k + 1]
        val pas = k + if (abs(courbure) > 1e-12) (0.5 * (auto[k - 1] - auto[k + 1]) / courbure).coerceIn(-0.5, 0.5) else 0.0

        // Où tombent les bords de chaque famille : le peigne (une dent par pas) qui accroche le plus de contraste.
        val (bordMonte, forceMonte) = phase(monte, pas)
        val (bordDescend, forceDescend) = phase(descend, pas)
        // Un bord tombe entre deux pixels : le contraste culmine un demi-pixel avant le début de la case.
        val (debutCase, taille) = if (min(forceMonte, forceDescend) < 0.08 * max(forceMonte, forceDescend)) {
            // Une seule famille nette : des cases jointives, de bord à bord.
            val bord = if (forceMonte >= forceDescend) bordMonte else bordDescend
            (bord + 1.5) to (pas - 2)
        } else {
            val d = ((bordDescend - bordMonte) % pas + pas) % pas
            // Le plus long des deux intervalles entre les deux familles de bords est l'intérieur d'une case.
            if (d >= pas - d) (bordMonte + 0.5) to d else (bordDescend + 0.5) to (pas - d)
        }

        // Jusqu'où la grille s'étend : la plus longue suite de cases dont les deux bords se voient bien.
        var position = debutCase - floor(debutCase / pas) * pas
        val positions = mutableListOf<Double>()
        while (position + taille <= n - 1) {
            positions += position
            position += pas
        }
        if (positions.size < CASES_MIN) return null
        val appuis = positions.map { pic(monte, it - 0.5) + pic(descend, it - 0.5) + pic(monte, it + taille - 0.5) + pic(descend, it + taille - 0.5) }
        val seuil = 0.3 * (appuis.maxOrNull() ?: 0.0)
        var meilleurDebut = 0
        var meilleureLongueur = 0
        var debutSuite = 0
        for (i in positions.indices) {
            if (appuis[i] < seuil) {
                debutSuite = i + 1
                continue
            }
            val longueur = i - debutSuite + 1
            if (longueur > meilleureLongueur) {
                meilleureLongueur = longueur
                meilleurDebut = debutSuite
            }
        }
        if (meilleureLongueur < CASES_MIN) return null
        return Axe(positions[meilleurDebut], pas, taille, meilleureLongueur)
    }

    /** Seuls comptent les pics : le profil moins sa moyenne locale (les variations lentes de lumière s'effacent). */
    private fun pics(profil: DoubleArray): DoubleArray {
        val n = profil.size
        val cumul = DoubleArray(n + 1)
        for (i in 0 until n) cumul[i + 1] = cumul[i] + profil[i]
        return DoubleArray(n) { i ->
            val a = max(0, i - PAS_MIN)
            val b = min(n, i + PAS_MIN + 1)
            max(0.0, profil[i] - (cumul[b] - cumul[a]) / (b - a))
        }
    }

    /**
     * Autocorrélation de [pics], centrée et normée (de -1 à 1) jusqu'au décalage [kMax] : proche de 0 pour du
     * bruit, forte au pas d'une grille.
     */
    private fun autocorrelation(pics: DoubleArray, kMax: Int): DoubleArray {
        val n = pics.size
        val moyenne = pics.average()
        val c = DoubleArray(n) { pics[it] - moyenne }
        val variance = c.sumOf { it * it } / n
        val auto = DoubleArray(kMax + 1)
        if (variance <= 1e-9) return auto
        for (k in 1..min(kMax, n - 2)) {
            var s = 0.0
            for (x in 0 until n - k) s += c[x] * c[x + k]
            auto[k] = s / (n - k) / variance
        }
        return auto
    }

    /** Le départ du peigne de pas [pas] qui accroche le plus de contraste, et sa force moyenne par dent. */
    private fun phase(pics: DoubleArray, pas: Double): Pair<Double, Double> {
        var meilleur = 0.0
        var force = -1.0
        var depart = 0.0
        while (depart < pas) {
            var s = 0.0
            var dents = 0
            var x = depart
            while (x < pics.size - 1) {
                s += pic(pics, x)
                dents++
                x += pas
            }
            val f = if (dents == 0) 0.0 else s / dents
            if (f > force) {
                force = f
                meilleur = depart
            }
            depart += 0.5
        }
        // Affinage au-delà du demi-pixel : le centre du contraste autour de chaque dent.
        var decalage = 0.0
        var poids = 0.0
        var x = meilleur
        while (x < pics.size - 1) {
            for (i in max(0, floor(x - 3).toInt())..min(pics.size - 1, ceil(x + 3).toInt())) {
                decalage += (i - x) * pics[i]
                poids += pics[i]
            }
            x += pas
        }
        return (if (poids > 0) meilleur + decalage / poids else meilleur) to force
    }

    /** Le plus fort du profil à moins de 1,5 pixel de [x] : un bord n'est jamais pile sur un pixel. */
    private fun pic(pics: DoubleArray, x: Double): Double {
        val a = max(0, (x - 1.5).roundToInt())
        val b = min(pics.size - 1, (x + 1.5).roundToInt())
        var m = 0.0
        for (i in a..b) m = max(m, pics[i])
        return m
    }
}
