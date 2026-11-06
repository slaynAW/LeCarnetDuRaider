package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Rarete
import io.github.slaynaw.carnetduraider.core.inventaire.ImagesDeTest.CaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Cases, rareté, reconnaissance des icônes et lecture de la bande du bas, sur des écrans d'inventaire
 * synthétiques dessinés à la manière du jeu et filmés de biais.
 */
class ReconnaissanceTest {
    /** 40 objets, de raretés variées ; « arme_i » et « arme_ii » partagent la même icône (deux niveaux). */
    private val raretesTest = (0 until 40).associate { "objet_$it" to Rarete.entries[it % Rarete.entries.size] }
    private val icones = (0 until 40).associate { "objet_$it" to ImagesDeTest.icone(it) } +
        mapOf("arme_i" to ImagesDeTest.icone(77), "arme_ii" to ImagesDeTest.icone(77))
    private val raretes: Map<String, Rarete?> = raretesTest + mapOf("arme_i" to Rarete.RARE, "arme_ii" to Rarete.RARE)
    private val piles = icones.keys.associateWith { if (it.startsWith("arme")) 1 else 200 }
    private val reconnaisseur = Reconnaisseur(icones, raretes)
    private val nombres = LectureNombre(ImagesDeTest.modelesChiffres())

    private fun analyse() = AnalyseInventaire(reconnaisseur, nombres, piles)

    /** Les icônes de test par graine : 77 pour l'arme. */
    private fun rarete(graine: Int): Rarete = if (graine == 77) Rarete.RARE else Rarete.entries[graine % Rarete.entries.size]

    private fun case(graine: Int?, quantite: Int = 1, niveau: Int? = null, reflet: Boolean = false) =
        CaseTest(graine, quantite, if (graine == null) Rarete.COMMUN else rarete(graine), niveau, reflet)

    /** 4 × 6 cases : des piles, des objets seuls, une arme à deux niveaux, une case vide et un reflet. */
    private val inventaire = listOf(
        case(3), case(11, 8), case(7), case(25, 24),
        case(31), case(14, 12), case(5, 3), case(18, 2),
        case(22), case(9, 3), case(77, niveau = 2), case(1),
        case(28), case(16, 2), case(30, reflet = true), case(12, 60),
        case(39), case(20, 6), case(33, 4), case(2),
        case(null), case(77, niveau = 1), case(27, 47), case(8, 105),
    )

    /** La même case, à quelques pixels près : centres à moins de 5 pixels, côtés à moins de 12 % (le cadre a une épaisseur). */
    private fun proches(a: Zone, b: Zone): Boolean =
        abs((a.x + a.droite) - (b.x + b.droite)) <= 10 && abs((a.y + a.bas) - (b.y + b.bas)) <= 10 &&
            abs(a.largeur - b.largeur) <= 0.12 * b.largeur && abs(a.hauteur - b.hauteur) <= 0.12 * b.hauteur

    @Test
    fun `les cases sont retrouvees par leur cadre, meme filmees un peu de biais`() {
        for (angle in listOf(0.0, 2.5)) {
            val ecran = ImagesDeTest.ecran(inventaire, angle = angle)
            val cases = DetectionGrille.detecter(ecran.image)
            assertEquals(24, cases.size, "angle $angle")
            for (z in ecran.zones) assertTrue(cases.any { proches(it.zone, z) }, "case $z trouvée (angle $angle)")
            assertEquals(6, cases.maxOf { it.ligne } + 1)
            assertEquals(4, cases.maxOf { it.colonne } + 1)
        }
    }

    @Test
    fun `les cases sont retrouvees a une autre taille, et plus vite avec la taille de l'image precedente`() {
        val ecran = ImagesDeTest.ecran(inventaire.take(12), colonnes = 3, largeur = 400, hauteur = 360, x0 = 50, y0 = 40, cote = 70, pas = 75)
        val cases = DetectionGrille.detecter(ecran.image)
        assertEquals(12, cases.size)
        assertEquals(12, DetectionGrille.detecter(ecran.image, tailleAttendue = 70).size)
        // Une taille attendue fausse ne fait rien manquer : toutes les tailles sont alors essayées.
        assertEquals(12, DetectionGrille.detecter(ecran.image, tailleAttendue = 30).size)
    }

    @Test
    fun `les boutons de l'interface, en colonne, ne sont pas des cases`() {
        val ecran = ImagesDeTest.ecran(inventaire, boutons = true)
        val cases = DetectionGrille.detecter(ecran.image)
        assertEquals(24, cases.size)
        assertTrue(cases.all { c -> ecran.zones.any { proches(c.zone, it) } })
    }

    @Test
    fun `pas de case dans une image sans inventaire`() {
        val alea = Random(4)
        val bruit = ImageRgb(320, 240, IntArray(320 * 240) { ImageRgb.argb(255, alea.nextInt(256), alea.nextInt(256), alea.nextInt(256)) })
        assertTrue(DetectionGrille.detecter(bruit).isEmpty())
        val unie = ImageRgb(320, 240, IntArray(320 * 240) { ImageRgb.argb(255, 40, 42, 50) })
        assertTrue(DetectionGrille.detecter(unie).isEmpty())
    }

    @Test
    fun `la rarete se lit sur le halo de la case`() {
        val cases = Rarete.entries.map { CaseTest(3, rarete = it) }
        val ecran = ImagesDeTest.ecran(cases, colonnes = 5, largeur = 640, hauteur = 300, x0 = 40, y0 = 60)
        for ((k, z) in ecran.zones.withIndex()) assertEquals(Rarete.entries[k], HaloRarete.lire(ecran.image, z))
    }

    @Test
    fun `chaque objet est reconnu, son nombre est juste ou illisible, jamais faux, et les cases vides sont ignorees`() {
        val ecran = ImagesDeTest.ecran(inventaire, angle = 1.5)
        val vues = analyse().analyser(ecran.image)
        var piles = 0
        var lues = 0
        for ((k, attendu) in ecran.cases.withIndex()) {
            val zone = ecran.zones[k]
            val vue = vues.firstOrNull { proches(it.zone, zone) }
            val g = attendu.graine
            when {
                g == null -> assertNull(vue, "case vide $k")
                attendu.reflet -> assertTrue(vue == null || vue.contenu == ContenuCase.PasSur, "reflet $k : jamais un objet sûr")
                g == 77 -> {
                    val objet = vue?.contenu as? ContenuCase.Objet
                    assertEquals(listOf(if (attendu.niveau == 2) "arme_ii" else "arme_i"), objet?.objetIds, "case $k : le niveau lu choisit l'arme")
                    assertEquals(1, objet?.quantite, "case $k : une arme ne s'empile pas")
                }
                else -> {
                    val objet = vue?.contenu as? ContenuCase.Objet
                    assertEquals(listOf("objet_$g"), objet?.objetIds, "case $k")
                    val q = objet?.quantite
                    assertTrue(q == null || q == attendu.quantite, "case $k : nombre $q, jamais faux (${attendu.quantite})")
                    if (attendu.quantite > 1) {
                        piles++
                        if (q == attendu.quantite) lues++
                    }
                }
            }
        }
        // Une image filmée un peu de biais : presque tous les nombres se lisent (les autres, sur l'image suivante).
        assertTrue(lues >= piles * 8 / 10, "$lues nombres lus sur $piles")
    }

    @Test
    fun `toutes les cases trouvees sont rendues avec leur cadre, meme vides`() {
        val ecran = ImagesDeTest.ecran(inventaire)
        val vu = analyse().analyserImage(ecran.image)
        assertEquals(24, vu.cadres.size)
        // La case vide a un cadre, mais pas de contenu.
        val vide = ecran.zones[ecran.cases.indexOfFirst { it.graine == null }]
        assertTrue(vu.cadres.any { proches(it, vide) })
        assertTrue(vu.cases.none { proches(it.zone, vide) })
    }

    @Test
    fun `une icone inconnue n'est jamais reconnue comme un objet`() {
        val inconnue = ImagesDeTest.icone(1234)
        val cases = listOf(CaseTest(1234), case(3), case(7), case(11))
        val ecran = ImagesDeTest.ecran(cases, colonnes = 4, largeur = 560, hauteur = 260, x0 = 60, y0 = 60) { if (it == 1234) inconnue else ImagesDeTest.icone(it) }
        val vues = analyse().analyser(ecran.image)
        assertTrue(vues.none { proches(it.zone, ecran.zones[0]) && it.contenu is ContenuCase.Objet })
        assertEquals(3, vues.count { it.contenu is ContenuCase.Objet })
    }

    @Test
    fun `deux objets a la meme icone n'en font qu'un, avec leurs deux identifiants`() {
        val ecran = ImagesDeTest.ecran(listOf(case(77), case(3)), colonnes = 2, largeur = 340, hauteur = 240, x0 = 60, y0 = 60)
        val r = reconnaisseur.reconnaitre(ecran.image, ecran.zones[0], Rarete.RARE)
        assertTrue(r.sur)
        assertEquals(listOf("arme_i", "arme_ii"), r.objetIds)
        assertEquals(listOf("arme_ii"), AnalyseInventaire.auNiveau(r.objetIds, 2))
        assertEquals(listOf("arme_i", "arme_ii"), AnalyseInventaire.auNiveau(r.objetIds, null))
    }

    @Test
    fun `les variantes d'un objet sont de la meme famille`() {
        assertEquals("anvil", Reconnaisseur.famille("anvil_iv"))
        assertEquals("looting_mk3", Reconnaisseur.famille("looting_mk3_cautious"))
        assertNull(Reconnaisseur.famille("light_ammo"))
        assertNull(Reconnaisseur.famille("looting_mk2"))
    }

    @Test
    fun `la bande du bas - le nombre apres le signe fois, rien d'ecrit - un, le niveau en chiffres romains`() {
        val cases = listOf(case(3, 24), case(7), case(11, 105), case(77, niveau = 3), case(14, 9), case(5, 60))
        val ecran = ImagesDeTest.ecran(cases, colonnes = 3, largeur = 440, hauteur = 340, x0 = 50, y0 = 50, angle = 1.0)
        val lu = ecran.zones.map { nombres.lire(ecran.image, it) }
        assertEquals(Bande(24), lu[0])
        assertEquals(Bande(1), lu[1])
        assertEquals(Bande(105), lu[2])
        assertEquals(Bande(1, 3), lu[3])
        assertEquals(Bande(9), lu[4])
        assertEquals(Bande(60), lu[5])
    }

    @Test
    fun `un nombre qui ne ressemble a aucun modele est illisible`() {
        // Des modèles de chiffres tous identiques : aucun chiffre ne l'emporte nettement.
        val flous = LectureNombre((0..9).associateWith { ImagesDeTest.modelesChiffres().getValue(8) })
        val ecran = ImagesDeTest.ecran(listOf(case(3, 24), case(7)), colonnes = 2, largeur = 340, hauteur = 240, x0 = 60, y0 = 60)
        assertNull(flous.lire(ecran.image, ecran.zones[0]).quantite)
        assertEquals(1, flous.lire(ecran.image, ecran.zones[1]).quantite)
    }
}
