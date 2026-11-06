package io.github.slaynaw.carnetduraider.core.inventaire

import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Sortie
import io.github.slaynaw.carnetduraider.core.Verdict

/** Ce que contient une case filmée. */
sealed interface ContenuCase {
    /**
     * Un objet reconnu sûrement ([objetIds] : plusieurs quand ils partagent la même icône) et combien la case en
     * contient, `null` si le nombre est illisible.
     */
    data class Objet(val objetIds: List<String>, val quantite: Int?) : ContenuCase

    /** Reconnu sans certitude (reflet, flou, icône inconnue) : jamais compté à vendre. */
    data object PasSur : ContenuCase
}

/** Une case vue par la caméra : sa zone dans l'image analysée et son contenu. */
data class CaseVue(val zone: Zone, val contenu: ContenuCase)

/** Ce que dit l'étiquette d'une case. */
enum class EtiquetteCase { VENDS, VENDS_EN_PARTIE, RECYCLE, GARDE, BUILD, PAS_SUR }

/** Une case et son étiquette ; [nombre] : combien en vendre, pour une pile dont une partie est à garder. */
data class CaseEtiquetee(val zone: Zone, val etiquette: EtiquetteCase, val nombre: Int? = null)

/**
 * Que faire d'un objet de l'inventaire, en tout : combien s'en séparer ([aVendre], vendus ou recyclés selon
 * [etiquette]) et combien garder ([aGarder], `null` si le nombre est inconnu), d'après son [verdict].
 */
data class Decision(
    val objetId: String,
    val quantite: Int?,
    val aVendre: Int,
    val aGarder: Int?,
    val etiquette: EtiquetteCase,
    val verdict: Verdict,
)

/**
 * Le tri de l'inventaire filmé, avec le même verdict que l'onglet Objets ([verdict]) : un objet dont aucun
 * objectif en cours ni aucun build n'a besoin se vend (ou se recycle, si ça rapporte plus). Pour une pile dont
 * les objectifs en cours demandent encore une partie, on garde ce qui manque et on vend le reste ; si son
 * nombre est illisible, ou qu'un build s'en sert aussi, on garde tout. Rien n'est enregistré : c'est une
 * photographie de l'instant, l'appli n'a toujours pas de réserve.
 */
class TriInventaire(private val catalogue: Catalogue, private val verdict: (String) -> Verdict) {

    fun decider(objetIds: List<String>, quantite: Int?): Decision {
        require(objetIds.isNotEmpty()) { "Aucun objet" }
        // Plusieurs objets à la même icône : le plus prudent l'emporte (garder plutôt que vendre).
        val (id, v) = objetIds.map { it to verdict(it) }.minBy { priorite(it.second) }
        return when (v) {
            is Verdict.Garder ->
                if (v.builds.isNotEmpty() || quantite == null || quantite <= v.manquant) {
                    Decision(id, quantite, 0, quantite, EtiquetteCase.GARDE, v)
                } else {
                    Decision(id, quantite, quantite - v.manquant, v.manquant, EtiquetteCase.VENDS_EN_PARTIE, v)
                }
            is Verdict.PourBuild -> Decision(id, quantite, 0, quantite, EtiquetteCase.BUILD, v)
            is Verdict.Inutile -> Decision(
                id,
                quantite,
                quantite ?: 1,
                0,
                if (v.conseil == Sortie.RECYCLER) EtiquetteCase.RECYCLE else EtiquetteCase.VENDS,
                v,
            )
        }
    }

    /**
     * L'étiquette de chaque case d'une image. Plusieurs piles du même objet : ce qu'il faut garder remplit les
     * premières (dans l'ordre de lecture), le reste est à vendre.
     */
    fun etiqueter(cases: List<CaseVue>): List<CaseEtiquetee> {
        val parObjet = cases.filter { it.contenu is ContenuCase.Objet }.groupBy { (it.contenu as ContenuCase.Objet).objetIds.first() }
        val decisions = parObjet.mapValues { (_, piles) ->
            val objet = piles.first().contenu as ContenuCase.Objet
            val quantites = piles.map { (it.contenu as ContenuCase.Objet).quantite }
            decider(objet.objetIds, if (quantites.any { it == null }) null else quantites.sumOf { it ?: 0 })
        }
        val resteAGarder = decisions.mapValues { it.value.aGarder ?: 0 }.toMutableMap()
        return cases.map { case ->
            val objet = case.contenu as? ContenuCase.Objet ?: return@map CaseEtiquetee(case.zone, EtiquetteCase.PAS_SUR)
            val cle = objet.objetIds.first()
            val d = decisions.getValue(cle)
            if (d.etiquette != EtiquetteCase.VENDS_EN_PARTIE) return@map CaseEtiquetee(case.zone, d.etiquette)
            val ici = objet.quantite ?: 0
            val garde = minOf(ici, resteAGarder.getValue(cle))
            resteAGarder[cle] = resteAGarder.getValue(cle) - garde
            if (ici - garde > 0) CaseEtiquetee(case.zone, EtiquetteCase.VENDS_EN_PARTIE, ici - garde) else CaseEtiquetee(case.zone, EtiquetteCase.GARDE)
        }
    }

    /** Une ligne « À vendre » : [quantite] à vendre (sur [sur] quand on en garde une partie), au [prix] l'unité. */
    data class LigneVente(val objetId: String, val nom: String, val quantite: Int, val sur: Int?, val prix: Int, val recycler: Boolean) {
        val total: Int get() = quantite * prix
    }

    /** Ce qu'il faut garder pour un objectif ou un build ([libelle]) : objet → nombre gardé (`null` : tout). */
    data class GroupeGarde(val libelle: String, val build: Boolean, val objets: List<Pair<String, Int?>>)

    /**
     * Le bilan de tout ce que la caméra a vu : à vendre (du plus rentable au moins rentable), à garder (par objectif
     * puis par build), et combien de cases n'ont pas été reconnues sûrement.
     */
    data class Bilan(val aVendre: List<LigneVente>, val aGarder: List<GroupeGarde>, val pasSur: Int) {
        val total: Int get() = aVendre.sumOf { it.total }
        val objetsAGarder: Int get() = aGarder.sumOf { it.objets.size }
        val vide: Boolean get() = aVendre.isEmpty() && aGarder.isEmpty() && pasSur == 0
    }

    fun bilan(objets: Map<List<String>, Int?>, pasSur: Int): Bilan {
        val ventes = mutableListOf<LigneVente>()
        val groupes = LinkedHashMap<Pair<String, Boolean>, MutableList<Pair<String, Int?>>>()
        for ((ids, quantite) in objets) {
            val d = decider(ids, quantite)
            val objet = catalogue.objet(d.objetId)
            val nom = catalogue.nomObjet(d.objetId)
            if (d.aVendre > 0) {
                val recycler = d.etiquette == EtiquetteCase.RECYCLE
                val prix = when {
                    objet == null -> 0
                    recycler -> catalogue.valeurRecyclage(objet)
                    else -> objet.valeur
                }
                ventes += LigneVente(d.objetId, nom, d.aVendre, if (d.etiquette == EtiquetteCase.VENDS_EN_PARTIE) quantite else null, prix, recycler)
            }
            val groupe = when (val v = d.verdict) {
                is Verdict.Garder -> v.pour.firstOrNull()?.libelle?.let { it to false }
                is Verdict.PourBuild -> v.builds.firstOrNull()?.build?.let { "Build « $it »" to true }
                is Verdict.Inutile -> null
            }
            if (groupe != null) groupes.getOrPut(groupe) { mutableListOf() } += d.objetId to d.aGarder
        }
        val aGarder = groupes.entries
            .sortedBy { it.key.second }
            .map { (cle, liste) -> GroupeGarde(cle.first, cle.second, liste.sortedBy { Catalogue.normaliser(catalogue.nomObjet(it.first)) }) }
        return Bilan(ventes.sortedWith(compareByDescending<LigneVente> { it.total }.thenBy { Catalogue.normaliser(it.nom) }), aGarder, pasSur)
    }

    private fun priorite(v: Verdict): Int = when (v) {
        is Verdict.Garder -> 0
        is Verdict.PourBuild -> 1
        is Verdict.Inutile -> if (v.conseil == Sortie.RECYCLER) 2 else 3
    }
}

/**
 * Ce que la caméra a vu depuis qu'elle est ouverte, image après image, le temps de faire défiler l'inventaire.
 * Un objet ne compte qu'après avoir été reconnu sur [imagesPourConfirmer] images (une erreur d'une image ne
 * s'ajoute pas). Son nombre est celui lu le plus souvent (le plus petit, à égalité) : jamais plus que ce qu'on
 * a vraiment vu ensemble. Tout s'efface avec [vider] : rien n'est gardé une fois la caméra fermée.
 */
class SuiviInventaire(private val tri: TriInventaire, private val imagesPourConfirmer: Int = 2) {

    private class Vu(val objetIds: List<String>) {
        var images = 0
        val lectures = HashMap<Int, Int>()
        var illisible = 0
    }

    private val vus = LinkedHashMap<String, Vu>()
    private var pasSur = 0

    /** Une image analysée : ses cases. */
    fun observer(cases: List<CaseVue>) {
        cases.mapNotNull { it.contenu as? ContenuCase.Objet }.groupBy { it.objetIds.first() }.forEach { (cle, piles) ->
            val vu = vus.getOrPut(cle) { Vu(piles.first().objetIds) }
            vu.images++
            if (piles.any { it.quantite == null }) {
                vu.illisible++
            } else {
                val q = piles.sumOf { it.quantite ?: 0 }
                vu.lectures[q] = (vu.lectures[q] ?: 0) + 1
            }
        }
        pasSur = maxOf(pasSur, cases.count { it.contenu == ContenuCase.PasSur })
    }

    /** Ce qui est confirmé jusqu'ici. */
    fun bilan(): TriInventaire.Bilan {
        val objets = vus.values.filter { it.images >= imagesPourConfirmer }.associate { vu ->
            vu.objetIds to vu.lectures.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })?.key
        }
        return tri.bilan(objets, pasSur)
    }

    fun vider() {
        vus.clear()
        pasSur = 0
    }
}
