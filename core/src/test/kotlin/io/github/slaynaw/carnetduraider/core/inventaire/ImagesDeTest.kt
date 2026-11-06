package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Images synthétiques pour les tests de la reconnaissance : des icônes détourées, des chiffres, et un écran
 * d'inventaire filmé (grille de cases, fonds de rareté, nombres, flou, bruit et écart de luminosité).
 */
object ImagesDeTest {
    private const val TRANSPARENT = 0

    /** Une icône détourée de [cote] pixels : trois formes de couleurs vives, différentes pour chaque [graine]. */
    fun icone(graine: Int, cote: Int = 64): ImageRgb {
        val alea = Random(graine * 7919 + 13)
        val pixels = IntArray(cote * cote) { TRANSPARENT }
        repeat(3) { forme ->
            val couleur = ImageRgb.argb(255, 40 + alea.nextInt(215), 40 + alea.nextInt(215), 40 + alea.nextInt(215))
            // Les formes restent dans les 80 % du centre (le coin du nombre reste dégagé, comme sur les vraies icônes).
            val cx = cote * (0.25 + 0.5 * alea.nextDouble())
            val cy = cote * (0.22 + 0.45 * alea.nextDouble())
            val rx = cote * (0.10 + 0.16 * alea.nextDouble())
            val ry = cote * (0.10 + 0.16 * alea.nextDouble())
            val sorte = (forme + graine) % 3
            for (y in 0 until cote) for (x in 0 until cote) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val dedans = when (sorte) {
                    0 -> dx * dx + dy * dy <= 1.0
                    1 -> kotlin.math.abs(dx) <= 1.0 && kotlin.math.abs(dy) <= 0.6
                    else -> dx * dx + dy * dy in 0.45..1.0
                }
                if (dedans && y < cote * 0.86) pixels[y * cote + x] = couleur
            }
        }
        return ImageRgb(cote, cote, pixels)
    }

    /** Les chiffres en 5 × 7 points, pour écrire les nombres et en faire les modèles de lecture. */
    private val POLICE = mapOf(
        0 to listOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "),
        1 to listOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
        2 to listOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"),
        3 to listOf("#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "),
        4 to listOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "),
        5 to listOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "),
        6 to listOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "),
        7 to listOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "),
        8 to listOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "),
        9 to listOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "),
    )

    /** Les modèles de chiffres : clairs sur fond sombre, avec une marge. */
    fun modelesChiffres(echelle: Int = 4): Map<Int, ImageRgb> = POLICE.mapValues { (chiffre, _) ->
        val l = 5 * echelle + 4
        val h = 7 * echelle + 4
        val pixels = IntArray(l * h) { ImageRgb.argb(255, 10, 10, 12) }
        val image = ImageRgb(l, h, pixels)
        ecrireChiffre(image, chiffre, 2, 2, echelle, contour = false)
        image
    }

    /** Un chiffre clair ; avec [contour], cerné de sombre comme les nombres du jeu (lisibles sur toutes les icônes). */
    private fun ecrireChiffre(image: ImageRgb, chiffre: Int, x0: Int, y0: Int, echelle: Int, contour: Boolean) {
        val motif = POLICE.getValue(chiffre)
        val points = motif.withIndex().flatMap { (j, ligne) ->
            ligne.withIndex().filter { it.value == '#' }.flatMap { (i, _) ->
                (0 until echelle).flatMap { dy -> (0 until echelle).map { dx -> (x0 + i * echelle + dx) to (y0 + j * echelle + dy) } }
            }
        }
        if (contour) for ((x, y) in points) for (dy in -1..1) for (dx in -1..1) poser(image, x + dx, y + dy, ImageRgb.argb(255, 0, 0, 0))
        for ((x, y) in points) poser(image, x, y, ImageRgb.argb(255, 250, 250, 245))
    }

    private fun poser(image: ImageRgb, x: Int, y: Int, p: Int) {
        if (x in 0 until image.largeur && y in 0 until image.hauteur) image.pixels[y * image.largeur + x] = p
    }

    /** Écrit [nombre] calé en bas à droite de [zone], comme le jeu le fait pour une pile. */
    fun ecrireNombre(image: ImageRgb, zone: Zone, nombre: Int, echelle: Int = 2) {
        val chiffres = nombre.toString().map { it - '0' }
        val largeur = chiffres.size * 6 * echelle - echelle
        var x = zone.droite - 4 - largeur
        val y = zone.bas - 4 - 7 * echelle
        for (c in chiffres) {
            ecrireChiffre(image, c, x, y, echelle, contour = true)
            x += 6 * echelle
        }
    }

    /** Une case de l'inventaire synthétique : [graine] de l'icône (`null` : vide), nombre, reflet par-dessus. */
    data class CaseTest(val graine: Int?, val quantite: Int = 1, val reflet: Boolean = false)

    class Ecran(val image: ImageRgb, val zones: List<Zone>, val cases: List<CaseTest>)

    /**
     * Un écran d'inventaire de [colonnes] × [cases.size / colonnes] cases, à l'origine ([x0], [y0]), cases de [cote]
     * pixels au pas de [pas], filmé : flou léger, bruit, luminosité un peu différente.
     */
    fun ecran(
        cases: List<CaseTest>,
        colonnes: Int = 6,
        largeur: Int = 560,
        hauteur: Int = 420,
        x0: Int = 40,
        y0: Int = 60,
        cote: Int = 72,
        pas: Int = 78,
        graine: Int = 1,
    ): Ecran {
        val alea = Random(graine)
        val image = ImageRgb(largeur, hauteur, IntArray(largeur * hauteur) { ImageRgb.argb(255, 18, 19, 23) })
        val raretes = listOf(intArrayOf(180, 180, 180), intArrayOf(108, 203, 95), intArrayOf(79, 163, 247), intArrayOf(212, 107, 224), intArrayOf(242, 178, 51))
        val zones = cases.indices.map { k -> Zone(x0 + (k % colonnes) * pas, y0 + (k / colonnes) * pas, cote, cote) }
        for ((k, case) in cases.withIndex()) {
            val z = zones[k]
            val rarete = raretes[(case.graine ?: 0) % raretes.size]
            for (y in z.y until z.bas) for (x in z.x until z.droite) {
                // Fond de la case : sombre en haut, la couleur de rareté monte du bas, comme dans le jeu.
                val t = ((y - z.y).toDouble() / z.hauteur - 0.3).coerceIn(0.0, 1.0) * (if (case.graine == null) 0.0 else 0.55)
                val p = ImageRgb.argb(255, (30 + (rarete[0] - 30) * t).roundToInt(), (31 + (rarete[1] - 31) * t).roundToInt(), (36 + (rarete[2] - 36) * t).roundToInt())
                image.pixels[y * largeur + x] = p
            }
            val g = case.graine ?: continue
            val icone = icone(g)
            val dedans = z.interieur(0.04)
            val reduite = icone.echantillon(icone.zone, dedans.largeur, dedans.hauteur)
            for (y in 0 until dedans.hauteur) for (x in 0 until dedans.largeur) {
                val p = reduite.pixel(x, y)
                val a = (p ushr 24) / 255.0
                if (a <= 0.0) continue
                val i = (dedans.y + y) * largeur + dedans.x + x
                image.pixels[i] = melange(image.pixels[i], p, a)
            }
            if (case.quantite > 1) ecrireNombre(image, z, case.quantite)
            if (case.reflet) {
                val cx = z.x + z.largeur * 0.45
                val cy = z.y + z.hauteur * 0.4
                for (y in z.y until z.bas) for (x in z.x until z.droite) {
                    val d = kotlin.math.hypot(x - cx, y - cy) / (z.largeur * 0.55)
                    if (d < 1.0) image.pixels[y * largeur + x] = melange(image.pixels[y * largeur + x], ImageRgb.argb(255, 255, 255, 255), (1 - d) * 0.95)
                }
            }
        }
        return Ecran(filmer(image, alea), zones, cases)
    }

    private fun melange(fond: Int, dessus: Int, a: Double): Int {
        fun c(p: Int, d: Int) = (p shr d) and 0xFF
        return ImageRgb.argb(
            255,
            (c(fond, 16) * (1 - a) + c(dessus, 16) * a).roundToInt(),
            (c(fond, 8) * (1 - a) + c(dessus, 8) * a).roundToInt(),
            (c(fond, 0) * (1 - a) + c(dessus, 0) * a).roundToInt(),
        )
    }

    /** Ce que voit la caméra : un léger flou (3 × 3), du bruit, une luminosité et un contraste un peu différents. */
    fun filmer(image: ImageRgb, alea: Random): ImageRgb {
        val l = image.largeur
        val h = image.hauteur
        val sortie = IntArray(l * h)
        for (y in 0 until h) for (x in 0 until l) {
            var r = 0
            var v = 0
            var b = 0
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = min(l - 1, max(0, x + dx))
                val yy = min(h - 1, max(0, y + dy))
                val p = image.pixels[yy * l + xx]
                r += (p shr 16) and 0xFF
                v += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
            fun camera(c: Int) = (c / n * 0.9 + 8 + alea.nextInt(-7, 8)).roundToInt()
            sortie[y * l + x] = ImageRgb.argb(255, camera(r), camera(v), camera(b))
        }
        return ImageRgb(l, h, sortie)
    }
}
