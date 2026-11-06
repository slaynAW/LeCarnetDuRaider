package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Build
import io.github.slaynaw.carnetduraider.core.Builds
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.DonneesJeu
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tri de l'inventaire filmé, avec le verdict de l'onglet Objets, sur les vraies données du jeu. */
class TriInventaireTest {
    private val catalogue = Catalogue(DonneesJeu.embarquees())
    private val conseiller = Conseiller(catalogue, Planificateur(catalogue))
    private val armurier2 = Objectif.NiveauDEtabli("weapon_bench", 2)
    private val objectifs = listOf(armurier2)
    private val builds = Builds(catalogue).aGarder(listOf(Build(objets = mapOf(CaseBuild.BOUCLIER to "light_shield"))))
    private val tri = TriInventaire(catalogue) { conseiller.verdict(it, objectifs, emptyMap(), builds) }

    /** Ce que l'Armurier niveau 2 demande encore en composants mécaniques. */
    private val manque = (conseiller.verdict("mechanical_components", objectifs) as Verdict.Garder).manquant

    private fun objet(id: String, quantite: Int?) = CaseVue(Zone(0, 0, 10, 10), ContenuCase.Objet(listOf(id), quantite))

    @Test
    fun `un objet dont rien n'a besoin se vend en entier`() {
        val d = tri.decider(listOf("bronze_statuette"), 2)
        assertEquals(EtiquetteCase.VENDS, d.etiquette)
        assertEquals(2, d.aVendre)
        assertEquals(0, d.aGarder)
    }

    @Test
    fun `une pile - on garde ce qui manque a l'objectif, on vend le reste`() {
        assertTrue(manque > 0)
        val d = tri.decider(listOf("mechanical_components"), manque + 3)
        assertEquals(EtiquetteCase.VENDS_EN_PARTIE, d.etiquette)
        assertEquals(3, d.aVendre)
        assertEquals(manque, d.aGarder)
        // Pas plus que ce qui manque : tout se garde.
        assertEquals(EtiquetteCase.GARDE, tri.decider(listOf("mechanical_components"), manque).etiquette)
    }

    @Test
    fun `nombre illisible - un objet utile se garde en entier, jamais vendu au hasard`() {
        val d = tri.decider(listOf("mechanical_components"), null)
        assertEquals(EtiquetteCase.GARDE, d.etiquette)
        assertEquals(0, d.aVendre)
        assertNull(d.aGarder)
    }

    @Test
    fun `un objet du build se garde`() {
        val d = tri.decider(listOf("light_shield"), 1)
        assertEquals(EtiquetteCase.BUILD, d.etiquette)
        assertEquals(0, d.aVendre)
    }

    @Test
    fun `deux objets a la meme icone - le plus prudent l'emporte`() {
        val d = tri.decider(listOf("bronze_statuette", "mechanical_components"), 1)
        assertEquals("mechanical_components", d.objetId)
        assertEquals(EtiquetteCase.GARDE, d.etiquette)
    }

    @Test
    fun `plusieurs piles du meme objet - ce qu'il faut garder remplit la premiere`() {
        val cases = listOf(objet("mechanical_components", manque), objet("bronze_statuette", 1), objet("mechanical_components", 3), CaseVue(Zone(0, 0, 1, 1), ContenuCase.PasSur))
        val etiquettes = tri.etiqueter(cases)
        assertEquals(EtiquetteCase.GARDE, etiquettes[0].etiquette)
        assertEquals(EtiquetteCase.VENDS, etiquettes[1].etiquette)
        assertEquals(EtiquetteCase.VENDS_EN_PARTIE, etiquettes[2].etiquette)
        assertEquals(3, etiquettes[2].nombre)
        assertEquals(EtiquetteCase.PAS_SUR, etiquettes[3].etiquette)
    }

    @Test
    fun `bilan - a vendre du plus rentable au moins rentable, a garder par objectif puis par build`() {
        val bilan = tri.bilan(
            mapOf(
                listOf("apricot") to 3,
                listOf("bronze_statuette") to 1,
                listOf("mechanical_components") to manque + 3,
                listOf("light_shield") to 1,
            ),
            pasSur = 1,
        )
        val statuette = catalogue.objet("bronze_statuette")!!.valeur
        val abricot = catalogue.objet("apricot")!!.valeur
        val meca = catalogue.objet("mechanical_components")!!.valeur
        assertEquals(listOf("bronze_statuette", "apricot", "mechanical_components").sortedByDescending {
            when (it) {
                "bronze_statuette" -> statuette
                "apricot" -> 3 * abricot
                else -> 3 * meca
            }
        }, bilan.aVendre.map { it.objetId })
        assertEquals(statuette + 3 * abricot + 3 * meca, bilan.total)
        val composants = bilan.aVendre.single { it.objetId == "mechanical_components" }
        assertEquals(3, composants.quantite)
        assertEquals(manque + 3, composants.sur)
        assertEquals(listOf("Armurier · niveau 2", "Build « Build 1 »"), bilan.aGarder.map { it.libelle })
        assertEquals(listOf("mechanical_components" to manque), bilan.aGarder[0].objets)
        assertTrue(bilan.aGarder[1].build)
        assertEquals(2, bilan.objetsAGarder)
        assertEquals(1, bilan.pasSur)
        assertFalse(bilan.vide)
    }

    @Test
    fun `suivi - un objet compte apres deux images, avec le plus grand nombre lu deux fois`() {
        val suivi = SuiviInventaire(tri)
        suivi.observer(listOf(objet("bronze_statuette", 1), objet("apricot", 3)))
        assertTrue(suivi.bilan().aVendre.isEmpty(), "une seule image : rien n'est encore sûr")
        suivi.observer(listOf(objet("apricot", 3), CaseVue(Zone(0, 0, 1, 1), ContenuCase.PasSur)))
        suivi.observer(listOf(objet("apricot", 4)))
        val bilan = suivi.bilan()
        // La statuette n'a été vue qu'une fois ; l'abricot trois fois, lu « 3 » deux fois, « 4 » une seule.
        assertEquals(listOf("apricot"), bilan.aVendre.map { it.objetId })
        assertEquals(3, bilan.aVendre.single().quantite)
        assertEquals(1, bilan.pasSur)
        suivi.vider()
        assertTrue(suivi.bilan().vide)
    }

    @Test
    fun `suivi - aucun nombre lu deux fois - le plus petit l'emporte`() {
        val suivi = SuiviInventaire(tri)
        suivi.observer(listOf(objet("mechanical_components", manque + 4)))
        suivi.observer(listOf(objet("mechanical_components", manque + 2)))
        assertEquals(2, suivi.bilan().aVendre.single().quantite)
    }

    @Test
    fun `suivi - les piles d'un objet comptent toutes une fois a l'ecran ensemble`() {
        // Trois piles de 24 : on n'en voit d'abord que deux, puis les trois (deux images), puis une seule.
        val suivi = SuiviInventaire(tri)
        suivi.observer(listOf(objet("apricot", 24), objet("apricot", 24)))
        suivi.observer(listOf(objet("apricot", 24), objet("apricot", 24), objet("apricot", 24)))
        suivi.observer(listOf(objet("apricot", 24), objet("apricot", 24), objet("apricot", 24)))
        suivi.observer(listOf(objet("apricot", 24)))
        assertEquals(72, suivi.bilan().aVendre.single().quantite)
    }
}
