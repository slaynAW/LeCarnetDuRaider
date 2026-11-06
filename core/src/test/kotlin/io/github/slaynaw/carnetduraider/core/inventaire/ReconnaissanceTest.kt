package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.inventaire.ImagesDeTest.CaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/** Grille, reconnaissance des icônes et lecture des nombres, sur des écrans d'inventaire synthétiques filmés. */
class ReconnaissanceTest {
    private val icones = (0 until 40).associate { "objet_$it" to ImagesDeTest.icone(it) }
    private val reconnaisseur = Reconnaisseur(icones)
    private val nombres = LectureNombre(ImagesDeTest.modelesChiffres())
    private val analyse = AnalyseInventaire(reconnaisseur, nombres)

    /** 6 × 4 cases : des piles, des objets seuls, deux cases vides et un reflet. */
    private val inventaire = listOf(
        CaseTest(3), CaseTest(11, 8), CaseTest(7), CaseTest(25), CaseTest(31), CaseTest(14, 12),
        CaseTest(5, 3), CaseTest(18, 2), CaseTest(22), CaseTest(9, 3), CaseTest(36), CaseTest(1),
        CaseTest(28), CaseTest(16, 2), CaseTest(30, reflet = true), CaseTest(12, 2), CaseTest(39), CaseTest(20, 6),
        CaseTest(33, 4), CaseTest(2), CaseTest(null), CaseTest(null), CaseTest(27, 47), CaseTest(8, 105),
    )

    @Test
    fun `la grille de l'inventaire est retrouvee`() {
        val ecran = ImagesDeTest.ecran(inventaire)
        val cases = DetectionGrille.detecter(ecran.image)
        assertEquals(24, cases.size)
        assertEquals(4, cases.maxOf { it.ligne } + 1)
        assertEquals(6, cases.maxOf { it.colonne } + 1)
        for (c in cases) {
            val attendue = ecran.zones[c.ligne * 6 + c.colonne]
            assertTrue(abs(c.zone.x - attendue.x) <= 3 && abs(c.zone.y - attendue.y) <= 3, "case ${c.ligne},${c.colonne} : ${c.zone} au lieu de $attendue")
            assertTrue(abs(c.zone.largeur - attendue.largeur) <= 4, "taille ${c.zone.largeur} au lieu de ${attendue.largeur}")
        }
    }

    @Test
    fun `la grille est retrouvee a une autre taille et un autre endroit`() {
        val ecran = ImagesDeTest.ecran(inventaire.take(12), colonnes = 4, x0 = 120, y0 = 30, cote = 50, pas = 55, graine = 7)
        val cases = DetectionGrille.detecter(ecran.image)
        assertEquals(12, cases.size)
        val premiere = cases.first { it.ligne == 0 && it.colonne == 0 }
        assertTrue(abs(premiere.zone.x - 120) <= 3 && abs(premiere.zone.y - 30) <= 3, "première case : ${premiere.zone}")
    }

    @Test
    fun `pas de grille dans une image sans inventaire`() {
        val alea = Random(5)
        val bruit = ImageRgb(400, 300, IntArray(400 * 300) { ImageRgb.argb(255, 40 + alea.nextInt(30), 40 + alea.nextInt(30), 45 + alea.nextInt(30)) })
        assertTrue(DetectionGrille.detecter(bruit).isEmpty())
        assertTrue(analyse.analyser(bruit).isEmpty())
    }

    @Test
    fun `chaque objet est reconnu avec son nombre, les cases vides sont ignorees`() {
        val ecran = ImagesDeTest.ecran(inventaire)
        val vues = analyse.analyser(ecran.image)
        assertEquals(22, vues.size, "les deux cases vides ne comptent pas")
        for (vue in vues) {
            val k = ecran.zones.indexOfFirst { abs(it.x - vue.zone.x) <= 4 && abs(it.y - vue.zone.y) <= 4 }
            assertTrue(k >= 0, "case inattendue ${vue.zone}")
            val attendue = ecran.cases[k]
            val contenu = vue.contenu
            if (attendue.reflet) {
                // Sous un reflet : pas sûr, ou bien reconnu, mais jamais pris pour un autre objet.
                assertTrue(contenu == ContenuCase.PasSur || (contenu as ContenuCase.Objet).objetIds == listOf("objet_${attendue.graine}"), "reflet : $contenu")
                continue
            }
            assertTrue(contenu is ContenuCase.Objet, "case $k (objet_${attendue.graine}) : $contenu")
            contenu as ContenuCase.Objet
            assertEquals(listOf("objet_${attendue.graine}"), contenu.objetIds, "case $k")
            assertEquals(attendue.quantite, contenu.quantite, "nombre de la case $k")
        }
    }

    @Test
    fun `une icone inconnue n'est jamais reconnue surement`() {
        // L'objet 30 n'est pas dans les icônes connues : sa case ne doit pas passer pour un autre.
        val connues = Reconnaisseur(icones - "objet_30")
        val ecran = ImagesDeTest.ecran(listOf(CaseTest(30), CaseTest(3), CaseTest(30), CaseTest(7)), colonnes = 2, largeur = 300, hauteur = 260)
        val vues = AnalyseInventaire(connues, nombres).analyser(ecran.image)
        assertEquals(4, vues.size)
        val inconnues = vues.filterIndexed { i, _ -> i % 2 == 0 }
        assertTrue(inconnues.all { it.contenu == ContenuCase.PasSur }, "${inconnues.map { it.contenu }}")
    }

    @Test
    fun `deux objets a la meme icone n'en font qu'un, avec leurs deux identifiants`() {
        val jumeaux = Reconnaisseur(mapOf("a" to ImagesDeTest.icone(4), "b" to ImagesDeTest.icone(4), "c" to ImagesDeTest.icone(6)))
        assertEquals(2, jumeaux.taille)
        val ecran = ImagesDeTest.ecran(listOf(CaseTest(4), CaseTest(6), CaseTest(6), CaseTest(4)), colonnes = 2, largeur = 300, hauteur = 260)
        val vues = AnalyseInventaire(jumeaux, nombres).analyser(ecran.image)
        assertEquals(listOf("a", "b"), (vues.first().contenu as ContenuCase.Objet).objetIds)
    }

    @Test
    fun `nombres lus dans le coin de la case, 1 quand il n'y en a pas`() {
        val zone = Zone(10, 10, 72, 72)
        for (n in listOf(2, 7, 12, 30, 98, 450)) {
            val image = ImageRgb(100, 100, IntArray(100 * 100) { ImageRgb.argb(255, 32, 34, 40) })
            ImagesDeTest.ecrireNombre(image, zone, n)
            assertEquals(n, nombres.lire(ImagesDeTest.filmer(image, Random(n)), zone), "nombre $n")
        }
        val sans = ImageRgb(100, 100, IntArray(100 * 100) { ImageRgb.argb(255, 32, 34, 40) })
        assertEquals(1, nombres.lire(sans, zone))
    }

    @Test
    fun `un chiffre qui ne ressemble a aucun modele rend le nombre illisible`() {
        val zone = Zone(10, 10, 72, 72)
        val image = ImageRgb(100, 100, IntArray(100 * 100) { ImageRgb.argb(255, 32, 34, 40) })
        // Un pavé clair à la place du nombre.
        for (y in 64 until 78) for (x in 66 until 76) image.pixels[y * 100 + x] = ImageRgb.argb(255, 250, 250, 250)
        assertNull(nombres.lire(image, zone))
    }
}
