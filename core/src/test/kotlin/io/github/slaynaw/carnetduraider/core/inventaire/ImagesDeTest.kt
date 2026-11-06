package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Rarete
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Images synthétiques pour les tests de la reconnaissance, à la manière du jeu : des icônes détourées, des
 * chiffres, et un écran d'inventaire filmé. Chaque case a un cadre fin de la couleur de sa rareté, un halo de
 * cette couleur qui monte du coin en bas à gauche, l'icône en haut (affichée plus claire que le fichier, comme
 * dans le jeu) et une bande sombre en bas, avec la catégorie à gauche et, à droite, « ×N » ou le niveau en
 * chiffres romains. L'écran est filmé un peu de biais, avec un léger flou, du bruit et un écart de luminosité.
 */
object ImagesDeTest {
    private const val TRANSPARENT = 0

    /** Une icône détourée de [cote] pixels : trois formes de couleurs vives, différentes pour chaque [graine]. */
    fun icone(graine: Int, cote: Int = 64): ImageRgb {
        val alea = Random(graine * 7919 + 13)
        val pixels = IntArray(cote * cote) { TRANSPARENT }
        repeat(3) { forme ->
            val couleur = ImageRgb.argb(255, 40 + alea.nextInt(215), 40 + alea.nextInt(215), 40 + alea.nextInt(215))
            val cx = cote * (0.25 + 0.5 * alea.nextDouble())
            val cy = cote * (0.25 + 0.5 * alea.nextDouble())
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
                if (dedans) pixels[y * cote + x] = couleur
            }
        }
        return ImageRgb(cote, cote, pixels)
    }

    /** Les chiffres en 5 × 7 points, pour écrire les nombres et en faire les modèles de lecture. */
    private val POLICE = mapOf(
        0 to listOf(" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "),
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

    /** Le « × » devant le nombre d'une pile : plus petit que les chiffres, au milieu de leur hauteur. */
    private val FOIS = listOf("# #", " # ", "# #")

    /** Les chiffres romains des niveaux d'arme : traits droits, reliés en haut et en bas par les empattements. */
    private val ROMAINS = mapOf(
        1 to listOf("###", " # ", " # ", " # ", " # ", " # ", "###"),
        2 to listOf("######", " #  # ", " #  # ", " #  # ", " #  # ", " #  # ", "######"),
        3 to listOf("#########", " #  #  # ", " #  #  # ", " #  #  # ", " #  #  # ", " #  #  # ", "#########"),
        4 to listOf("###  #   #", " #   #   #", " #   #   #", " #    # # ", " #    # # ", " #     #  ", "###    #  "),
    )

    /** Les modèles de chiffres : clairs sur fond sombre, avec une marge. */
    fun modelesChiffres(echelle: Int = 4): Map<Int, ImageRgb> = POLICE.mapValues { (_, motif) ->
        val l = 5 * echelle + 4
        val h = 7 * echelle + 4
        val image = ImageRgb(l, h, IntArray(l * h) { ImageRgb.argb(255, 10, 10, 12) })
        dessiner(image, motif, 2, 2, echelle, BLANC)
        image
    }

    private val BLANC = ImageRgb.argb(255, 245, 245, 240)

    private fun dessiner(image: ImageRgb, motif: List<String>, x0: Int, y0: Int, echelle: Int, couleur: Int) {
        for ((j, ligne) in motif.withIndex()) for ((i, c) in ligne.withIndex()) {
            if (c != '#') continue
            for (dy in 0 until echelle) for (dx in 0 until echelle) poser(image, x0 + i * echelle + dx, y0 + j * echelle + dy, couleur)
        }
    }

    private fun poser(image: ImageRgb, x: Int, y: Int, p: Int) {
        if (x in 0 until image.largeur && y in 0 until image.hauteur) image.pixels[y * image.largeur + x] = p
    }

    /** La couleur de chaque rareté, comme dans le jeu (cadre et halo). */
    val COULEURS = mapOf(
        Rarete.COMMUN to intArrayOf(185, 188, 200),
        Rarete.INHABITUEL to intArrayOf(70, 215, 105),
        Rarete.RARE to intArrayOf(40, 175, 240),
        Rarete.EPIQUE to intArrayOf(240, 85, 215),
        Rarete.LEGENDAIRE to intArrayOf(250, 195, 40),
    )

    /**
     * Une case de l'inventaire synthétique : [graine] de l'icône (`null` : vide), nombre (« ×N » au-delà de 1),
     * rareté, niveau en chiffres romains, reflet par-dessus.
     */
    data class CaseTest(
        val graine: Int?,
        val quantite: Int = 1,
        val rarete: Rarete = Rarete.COMMUN,
        val niveau: Int? = null,
        val reflet: Boolean = false,
    )

    /** Un écran filmé : l'image, la zone de chaque case (dans l'image filmée) et ce qu'elle contient. */
    class Ecran(val image: ImageRgb, val zones: List<Zone>, val cases: List<CaseTest>)

    /**
     * Un écran d'inventaire de [colonnes] colonnes, à l'origine ([x0], [y0]), cases de [cote] pixels au pas de
     * [pas], filmé tourné de [angle] degrés : flou léger, bruit, luminosité un peu différente. [boutons] : une
     * colonne de petits boutons carrés à gauche de la grille, comme les catégories du jeu. [dessinIcone] : l'icône
     * de chaque graine.
     */
    fun ecran(
        cases: List<CaseTest>,
        colonnes: Int = 4,
        largeur: Int = 560,
        hauteur: Int = 760,
        x0: Int = 110,
        y0: Int = 80,
        cote: Int = 96,
        pas: Int = 103,
        angle: Double = 0.0,
        boutons: Boolean = false,
        graine: Int = 1,
        dessinIcone: (Int) -> ImageRgb = ::icone,
    ): Ecran {
        val alea = Random(graine)
        val image = ImageRgb(largeur, hauteur, IntArray(largeur * hauteur) { ImageRgb.argb(255, 58, 59, 79) })
        val zones = cases.indices.map { k -> Zone(x0 + (k % colonnes) * pas, y0 + (k / colonnes) * pas, cote, cote) }
        for ((k, case) in cases.withIndex()) dessinerCase(image, zones[k], case, dessinIcone)
        if (boutons) {
            val c = cote / 3
            for (k in 0 until 6) cadre(image, Zone(x0 - c - 20, y0 + k * (c + 8), c, c), intArrayOf(200, 200, 210), 3)
        }
        val tournee = if (angle == 0.0) image else tourner(image, angle)
        val cx = largeur / 2.0
        val cy = hauteur / 2.0
        val a = Math.toRadians(angle)
        val zonesTournees = zones.map { z ->
            val dx = z.x + z.largeur / 2.0 - cx
            val dy = z.y + z.hauteur / 2.0 - cy
            val nx = cx + dx * cos(a) - dy * sin(a)
            val ny = cy + dx * sin(a) + dy * cos(a)
            Zone((nx - z.largeur / 2.0).roundToInt(), (ny - z.hauteur / 2.0).roundToInt(), z.largeur, z.hauteur)
        }
        return Ecran(filmer(tournee, alea), zonesTournees, cases)
    }

    /** Une case comme le jeu la dessine (voir [ecran]). */
    private fun dessinerCase(image: ImageRgb, z: Zone, case: CaseTest, dessinIcone: (Int) -> ImageRgb) {
        val couleur = COULEURS.getValue(case.rarete)
        val bande = z.y + (z.hauteur * 0.79).roundToInt()
        val rayon = z.largeur * 0.6
        for (y in z.y until z.bas) for (x in z.x until z.droite) {
            if (!dansCoinsArrondis(z, x, y)) continue
            var p = intArrayOf(34, 38, 56)
            if (case.graine != null) {
                if (y >= bande) {
                    p = intArrayOf(24, 26, 38)
                } else {
                    // Le halo de la rareté, depuis le coin en bas à gauche (au-dessus de la bande).
                    val d = hypot((x - z.x).toDouble(), (bande - y).toDouble()) / rayon
                    if (d < 1.0) p = melange(p, couleur, (1 - d) * (1 - d) * 0.85)
                }
            }
            image.pixels[y * image.largeur + x] = ImageRgb.argb(255, p[0], p[1], p[2])
        }
        // Le cadre : la couleur de la rareté, un peu éclaircie (moins clair que le texte, comme dans le jeu).
        cadre(image, z, if (case.graine == null) intArrayOf(120, 122, 140) else melange(couleur, intArrayOf(255, 255, 255), 0.15), 2)
        val g = case.graine ?: return
        // L'icône, plus claire que le fichier (comme le jeu l'affiche).
        val place = Reconnaisseur.zoneIcone(z)
        val source = dessinIcone(g)
        val reduite = source.echantillon(source.zone, place.largeur, place.hauteur)
        for (y in 0 until place.hauteur) for (x in 0 until place.largeur) {
            val p = reduite.pixel(x, y)
            val a = (p ushr 24) / 255.0
            if (a <= 0.0) continue
            val clair = intArrayOf(
                (((p shr 16) and 0xFF) * 0.55 + 100).roundToInt(),
                (((p shr 8) and 0xFF) * 0.55 + 100).roundToInt(),
                ((p and 0xFF) * 0.55 + 100).roundToInt(),
            )
            val i = (place.y + y) * image.largeur + place.x + x
            val fond = intArrayOf((image.pixels[i] shr 16) and 0xFF, (image.pixels[i] shr 8) and 0xFF, image.pixels[i] and 0xFF)
            val m = melange(fond, clair, a)
            image.pixels[i] = ImageRgb.argb(255, m[0], m[1], m[2])
        }
        // La catégorie, à gauche de la bande : un petit rond clair.
        val rc = z.largeur * 0.05
        val ccx = z.x + z.largeur * 0.12
        val ccy = (bande + z.bas) / 2.0
        for (y in bande until z.bas) for (x in z.x until z.x + z.largeur / 4) {
            if (hypot(x - ccx, y - ccy) <= rc) poser(image, x, y, ImageRgb.argb(255, 220, 220, 225))
        }
        // À droite de la bande : « ×N », ou le niveau.
        val echelle = max(1, (z.hauteur * 0.14 / 7).roundToInt())
        val droite = z.droite - (z.largeur * 0.07).roundToInt()
        val basTexte = z.bas - (z.hauteur * 0.06).roundToInt()
        val motifs = when {
            case.niveau != null -> listOf(ROMAINS.getValue(case.niveau))
            case.quantite > 1 -> listOf(FOIS) + case.quantite.toString().map { POLICE.getValue(it - '0') }
            else -> emptyList()
        }
        // Les signes espacés comme dans la police du jeu (un peu plus que l'épaisseur d'un trait).
        var x = droite - motifs.sumOf { (it[0].length + 2) * echelle } + 2 * echelle
        for (motif in motifs) {
            // Le « × » est centré sur la hauteur des chiffres.
            val y = basTexte - (if (motif === FOIS) (7 + motif.size) / 2 else motif.size) * echelle
            dessiner(image, motif, x, y, echelle, BLANC)
            x += (motif[0].length + 2) * echelle
        }
        if (case.reflet) {
            val cx = z.x + z.largeur * 0.45
            val cy = z.y + z.hauteur * 0.4
            for (y in z.y until z.bas) for (x2 in z.x until z.droite) {
                val d = hypot(x2 - cx, y - cy) / (z.largeur * 0.55)
                if (d >= 1.0) continue
                val i = y * image.largeur + x2
                val m = melange(intArrayOf((image.pixels[i] shr 16) and 0xFF, (image.pixels[i] shr 8) and 0xFF, image.pixels[i] and 0xFF), intArrayOf(255, 255, 255), (1 - d) * 0.95)
                image.pixels[i] = ImageRgb.argb(255, m[0], m[1], m[2])
            }
        }
    }

    /** Un cadre aux coins arrondis, d'[epaisseur] pixels. */
    private fun cadre(image: ImageRgb, z: Zone, couleur: IntArray, epaisseur: Int) {
        val p = ImageRgb.argb(255, couleur[0], couleur[1], couleur[2])
        for (y in z.y until z.bas) for (x in z.x until z.droite) {
            if (!dansCoinsArrondis(z, x, y)) continue
            val bord = x < z.x + epaisseur || x >= z.droite - epaisseur || y < z.y + epaisseur || y >= z.bas - epaisseur ||
                !dansCoinsArrondis(Zone(z.x + epaisseur, z.y + epaisseur, z.largeur - 2 * epaisseur, z.hauteur - 2 * epaisseur), x, y)
            if (bord) poser(image, x, y, p)
        }
    }

    private fun dansCoinsArrondis(z: Zone, x: Int, y: Int): Boolean {
        val r = z.largeur * 0.07
        val cx = x.coerceIn((z.x + r).toInt(), (z.droite - 1 - r).toInt())
        val cy = y.coerceIn((z.y + r).toInt(), (z.bas - 1 - r).toInt())
        return hypot((x - cx).toDouble(), (y - cy).toDouble()) <= r
    }

    private fun melange(a: IntArray, b: IntArray, t: Double): IntArray =
        IntArray(3) { (a[it] * (1 - t) + b[it] * t).roundToInt() }

    /** L'image tournée de [angle] degrés autour de son centre (interpolation bilinéaire, fond de l'écran autour). */
    fun tourner(image: ImageRgb, angle: Double): ImageRgb {
        val l = image.largeur
        val h = image.hauteur
        val a = Math.toRadians(angle)
        val cx = l / 2.0
        val cy = h / 2.0
        val sortie = IntArray(l * h)
        for (y in 0 until h) for (x in 0 until l) {
            // Le point de l'image d'origine qui vient ici (rotation inverse).
            val dx = x - cx
            val dy = y - cy
            val sx = cx + dx * cos(a) + dy * sin(a)
            val sy = cy - dx * sin(a) + dy * cos(a)
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val fx = sx - x0
            val fy = sy - y0
            fun p(xx: Int, yy: Int): Int = image.pixels[min(h - 1, max(0, yy)) * l + min(l - 1, max(0, xx))]
            val c = IntArray(3) { k ->
                val d = 16 - 8 * k
                val v00 = (p(x0, y0) shr d) and 0xFF
                val v10 = (p(x0 + 1, y0) shr d) and 0xFF
                val v01 = (p(x0, y0 + 1) shr d) and 0xFF
                val v11 = (p(x0 + 1, y0 + 1) shr d) and 0xFF
                (v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy).roundToInt()
            }
            sortie[y * l + x] = ImageRgb.argb(255, c[0], c[1], c[2])
        }
        return ImageRgb(l, h, sortie)
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
