package io.github.slaynaw.carnetduraider

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.slaynaw.carnetduraider.core.Apports
import io.github.slaynaw.carnetduraider.core.BilanRaid
import io.github.slaynaw.carnetduraider.core.Build
import io.github.slaynaw.carnetduraider.core.Builds
import io.github.slaynaw.carnetduraider.core.ButinRecherche
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.ConseilRaid
import io.github.slaynaw.carnetduraider.core.Conseiller
import io.github.slaynaw.carnetduraider.core.Etabli
import io.github.slaynaw.carnetduraider.core.FiltreVerdict
import io.github.slaynaw.carnetduraider.core.ListeDeCourses
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.ObjetATrouver
import io.github.slaynaw.carnetduraider.core.Planificateur
import io.github.slaynaw.carnetduraider.core.PreparationRaid
import io.github.slaynaw.carnetduraider.core.Progression
import io.github.slaynaw.carnetduraider.core.Projet
import io.github.slaynaw.carnetduraider.core.RetourDeRaid
import io.github.slaynaw.carnetduraider.core.SuiviProgression
import io.github.slaynaw.carnetduraider.core.Usages
import io.github.slaynaw.carnetduraider.core.UsagesObjet
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.core.calendrier.DonneesCalendrier
import io.github.slaynaw.carnetduraider.core.calendrier.Region
import io.github.slaynaw.carnetduraider.core.inventaire.SuiviInventaire
import io.github.slaynaw.carnetduraider.core.inventaire.TriInventaire

/**
 * État de l'appli : données du jeu (embarquées) et ce que le joueur a en cours. Tout est fait d'office, sauf
 * ce qu'il indique en cours (quêtes, ateliers, projets, Expéditions) : les objectifs en découlent. Il peut
 * aussi rechercher des objets pour lui-même (« Butin recherché »). Il note seulement ce qu'il rapporte de
 * raid, qui va aux objectifs qui le demandent, puis au butin recherché ; un objectif complet se termine tout
 * seul et la suite s'ajoute, un objet recherché complet quitte la liste. Ses builds disent quels objets
 * garder en plus.
 */
class CarnetViewModel(application: Application) : AndroidViewModel(application) {
    val catalogue: Catalogue = application.carnet.catalogue
    val planificateur = Planificateur(catalogue)
    val suivi = SuiviProgression(catalogue)
    val preparationRaid = PreparationRaid(catalogue)
    val usages = Usages(catalogue)
    val outilsBuilds = Builds(catalogue)
    private val conseiller = Conseiller(catalogue, planificateur)
    private val memoire = Memoire(application).also { it.nouveauDepart() }

    /** Mises à jour intégrées et calendrier : vivent avec l'appli, pas avec l'écran. */
    val miseAJour = (application as CarnetApplication).miseAJour
    val calendrier = application.carnet.calendrier
    val alertes = application.carnet.alertes
    val sauvegarde = application.carnet.sauvegarde

    var progression by mutableStateOf(memoire.lireProgression())
        private set
    var region by mutableStateOf(memoire.region)
        private set

    /** Ce qui a été rapporté de raid pour chaque objectif (clé de l'objectif → objets rapportés). */
    var apports by mutableStateOf(memoire.lireApports())
        private set

    /** Les [Builds.NOMBRE] builds du joueur. */
    var builds by mutableStateOf(memoire.lireBuilds())
        private set

    /** Objets à garder pour les builds (recalculé seulement quand les builds changent). */
    val aGarderPourBuilds by derivedStateOf { outilsBuilds.aGarder(builds) }

    /** Les objectifs : ce que le joueur a en cours, d'après sa progression. */
    val objectifs: List<Objectif> get() = suivi.objectifs(progression)

    /** « Butin recherché » : objets à rapporter sans objectif du jeu (objet → quantité voulue). */
    var butinRecherche by mutableStateOf(memoire.lireButinRecherche())
        private set

    /** Ce que le raid doit trouver : les objectifs en cours, puis le butin recherché. */
    private val objectifsDuRaid: List<Objectif> get() = objectifs + ButinRecherche.objectifs(butinRecherche)

    /**
     * Restaure une sauvegarde chiffrée : toutes les données de l'appli sont remplacées, puis relues.
     * Renvoie un message d'erreur, ou `null` si tout s'est bien passé.
     */
    suspend fun restaurerSauvegarde(uri: Uri, motDePasse: CharArray): String? {
        val erreur = sauvegarde.restaurer(uri, motDePasse)
        if (erreur == null) recharger()
        return erreur
    }

    /** Relit tout ce qui est enregistré (après une restauration). */
    private fun recharger() {
        memoire.nouveauDepart()
        progression = memoire.lireProgression()
        region = memoire.region
        apports = memoire.lireApports()
        builds = memoire.lireBuilds()
        butinRecherche = memoire.lireButinRecherche()
        retourOuvert = null
        saisis = emptyMap()
        derniereValidation = null
        alertes.recharger()
    }

    private fun changerApports(nouveaux: Apports) {
        if (nouveaux == apports) return
        apports = nouveaux
        memoire.ecrireApports(nouveaux)
    }

    /** Toute la liste du raid, objets déjà trouvés compris : objectifs en cours et butin recherché. */
    fun listeDuRaid(): List<ObjetATrouver> = planificateur.listeDuRaid(objectifsDuRaid, apports)

    /** Ce qu'il reste à trouver pour les objectifs en cours et le butin recherché. */
    fun aTrouver(): List<ObjetATrouver> = planificateur.aTrouver(objectifsDuRaid, apports)

    /** Ajoute un objet au butin recherché (un de plus s'il y est déjà). */
    fun ajouterAuButin(objetId: String) = changerButin(ButinRecherche.ajouter(butinRecherche, objetId))

    /**
     * Nouvelle quantité voulue d'un objet recherché, jamais moins d'un de plus que ce qui a déjà été rapporté
     * (un objet complet se termine au retour de raid) ; 0 le retire de la liste, avec ce qui avait été rapporté.
     */
    fun changerQuantiteButin(objetId: String, quantite: Int) {
        if (quantite <= 0) {
            changerButin(ButinRecherche.changer(butinRecherche, objetId, 0))
            changerApports(apports - ButinRecherche.cle(objetId))
        } else {
            changerButin(ButinRecherche.changer(butinRecherche, objetId, maxOf(quantite, ButinRecherche.rapportes(apports, objetId) + 1)))
        }
    }

    private fun changerButin(liste: Map<String, Int>) {
        if (liste == butinRecherche) return
        butinRecherche = liste
        memoire.ecrireButinRecherche(liste)
    }

    /** Retour de raid en cours : ce qui était rapporté et la liste du raid à l'ouverture (bilan, annulation). */
    class RetourOuvert(val avant: Apports, val liste: List<ObjetATrouver>)

    private var retourOuvert: RetourOuvert? = null

    /** Butin noté pendant le retour de raid en cours (identifiant → quantité). */
    var saisis by mutableStateOf(emptyMap<String, Int>())
        private set

    /** Ouvre le retour de raid, ou reprend celui qui est en cours. */
    fun ouvrirRetourDeRaid(): RetourOuvert =
        retourOuvert ?: RetourOuvert(apports, listeDuRaid()).also { retourOuvert = it }

    /**
     * Retour de raid : [delta] objets rapportés de plus (ou de moins), sans dépasser ce qui manquait à
     * l'ouverture. Le butin va aussitôt aux objectifs qui le demandent ([Planificateur.repartir]).
     */
    fun noter(objetId: String, delta: Int) {
        val r = retourOuvert ?: return
        val max = r.liste.firstOrNull { it.objetId == objetId }?.reste ?: 0
        val n = ((saisis[objetId] ?: 0) + delta).coerceIn(0, max)
        saisis = if (n == 0) saisis - objetId else saisis + (objetId to n)
        changerApports(planificateur.repartir(objectifsDuRaid, r.avant, saisis))
    }

    /** Remet ce qui était rapporté comme à l'ouverture du retour de raid. */
    fun annulerRetourDeRaid() {
        val r = retourOuvert ?: return
        saisis = emptyMap()
        changerApports(r.avant)
    }

    /** Ce que l'animation montre : objectifs terminés, objets trouvés, raid terminé. */
    var celebration by mutableStateOf<BilanRaid?>(null)
        private set

    /**
     * Retour de raid validé : les objectifs dont tout a été rapporté se terminent d'eux-mêmes (la suite
     * s'ajoute), et l'animation montre le bilan. « Annuler » remet tout comme avant le retour de raid.
     */
    fun validerRetourDeRaid() {
        val r = retourOuvert ?: return
        retourOuvert = null
        val notes = saisis.values.sum()
        saisis = emptyMap()
        val avant = Validation("Retour de raid", progression, r.avant, butinRecherche)
        val apres = listeDuRaid()
        // Même sans rien de noté (appli fermée par Android en plein retour), ce qui est complet se termine,
        // objectifs du jeu et objets du butin recherché.
        val termines = terminerLesComplets()
        terminerLeButinComplet()
        val bilan = RetourDeRaid.bilan(r.liste, apres, termines)
        if (!bilan.vide) celebration = bilan
        if (notes > 0 || termines.isNotEmpty()) derniereValidation = avant.copy(objetsNotes = notes, termines = termines)
    }

    fun finCelebration() {
        celebration = null
    }

    /** Les objets du butin recherché dont tout a été rapporté quittent la liste. */
    private fun terminerLeButinComplet() {
        val (liste, nouveaux, termines) = ButinRecherche.terminerLesComplets(butinRecherche, apports)
        if (termines.isEmpty()) return
        changerButin(liste)
        changerApports(nouveaux)
    }

    /** Où aller pour trouver ce qui manque encore. */
    fun conseilRaid(): ConseilRaid = preparationRaid.conseiller(aTrouver().map { it.objetId })

    /** Dernière validation, annulable quelques secondes (bandeau « Annuler »). */
    var derniereValidation by mutableStateOf<Validation?>(null)
        private set

    /** Quêtes en cours dans le jeu : tout le reste est fait d'office, sauf ce qui vient après elles. */
    fun indiquerQuetesEnCours(enCours: Collection<String>) = changerProgression(suivi.indiquerQuetesEnCours(progression, enCours))

    /** Niveau en cours d'un établi de l'Atelier, ou `null` : rien en cours pour lui. */
    fun indiquerNiveauEnCours(etabli: Etabli, niveau: Int?) = changerProgression(suivi.indiquerNiveauEnCours(progression, etabli, niveau))

    /** Étape en cours d'un projet ou d'une Expédition, ou `null` : pas en cours. */
    fun indiquerEtapeEnCours(projet: Projet, etape: Int?) = changerProgression(suivi.indiquerEtapeEnCours(progression, projet, etape))

    /** L'établi est terminé : tous ses niveaux sont faits. */
    fun indiquerEtabliTermine(etabli: Etabli) = changerProgression(suivi.indiquerEtabliTermine(progression, etabli))

    /** Le projet ou l'Expédition est terminé : toutes ses étapes sont faites. */
    fun indiquerProjetTermine(projet: Projet) = changerProgression(suivi.indiquerProjetTermine(progression, projet))

    /** Annule la dernière validation : progression, butin recherché et ce qui avait été rapporté reviennent comme avant. */
    fun annulerValidation() {
        val v = derniereValidation ?: return
        changerProgression(v.progression)
        changerButin(v.butinRecherche)
        changerApports(v.apports)
        derniereValidation = null
    }

    fun oublierValidation() {
        derniereValidation = null
    }

    /**
     * Objectif fait en jeu (« C'est fait »), même sans avoir tout noté (une quête sans objet à rapporter, par
     * exemple) : la suite s'ajoute, et ce qui avait été rapporté pour lui est oublié. La suite peut être déjà
     * complète : elle se termine aussi.
     */
    fun valider(objectif: Objectif) {
        val avant = Validation(planificateur.libelle(objectif), progression, apports, butinRecherche)
        changerProgression(suivi.terminer(progression, objectif))
        changerApports(RetourDeRaid.sansLesTermines(apports, listOf(objectif)))
        derniereValidation = avant.copy(termines = terminerLesComplets())
    }

    /** Termine d'eux-mêmes les objectifs dont tout a été rapporté ; renvoie ceux qui sont terminés. */
    private fun terminerLesComplets(): List<Objectif> {
        val (nouvelle, termines) = suivi.terminerLesComplets(progression) { planificateur.complet(it, apports) }
        if (termines.isNotEmpty()) {
            changerProgression(nouvelle)
            changerApports(RetourDeRaid.sansLesTermines(apports, termines))
        }
        return termines
    }

    /** Un build modifié (nom ou objet d'une case). */
    fun changerBuild(rang: Int, build: Build) {
        if (rang !in builds.indices || builds[rang] == build) return
        builds = builds.toMutableList().also { it[rang] = build }
        memoire.ecrireBuilds(builds)
    }

    /** Fait du build [rang] le build préféré (un seul à la fois), ou le retire ([prefere] faux). */
    fun preferer(rang: Int, prefere: Boolean = true) {
        if (rang !in builds.indices) return
        builds = Builds.preferer(builds, rang, prefere)
        memoire.ecrireBuilds(builds)
    }

    fun changerRegion(nouvelle: Region) {
        region = nouvelle
        memoire.region = nouvelle
        alertes.replanifier()
    }

    fun calendrierOfficiel(donnees: DonneesCalendrier): Calendrier = Calendrier(donnees, catalogue)

    fun listeDeCourses(): ListeDeCourses = planificateur.listeDeCourses(objectifs, apports)

    /** Garder, vendre ou recycler : un objet que demandent les objectifs ou le butin recherché est à garder. */
    fun verdict(objetId: String): Verdict = conseiller.verdict(objetId, objectifsDuRaid, apports, aGarderPourBuilds)

    /** Filtres de la liste des objets (« Recycler » seulement s'il peut servir). */
    val filtresObjets: List<FiltreVerdict> get() = conseiller.filtres

    /** Filmer mon inventaire : le tri de ce que montre la caméra, avec le même verdict que la liste des objets. */
    val triInventaire = TriInventaire(catalogue) { verdict(it) }

    /**
     * Ce que la caméra a vu depuis qu'elle est ouverte (le temps de faire défiler l'inventaire en jeu) : en mémoire
     * seulement, jamais enregistré, effacé à sa fermeture ([viderInventaireFilme]). Une rotation de l'écran le garde.
     */
    val suiviInventaire = SuiviInventaire(triInventaire)

    fun viderInventaireFilme() = suiviInventaire.vider()

    /** À quoi peut servir l'objet dans le jeu (explication, sans effet sur le verdict). */
    fun usagesDe(objetId: String): UsagesObjet = usages.pour(objetId)

    private fun changerProgression(nouvelle: Progression) {
        progression = nouvelle
        memoire.ecrireProgression(nouvelle)
    }
}

/** Une validation (objectif fait, retour de raid), avec l'état d'avant pour pouvoir l'annuler. */
data class Validation(
    val titre: String,
    val progression: Progression,
    val apports: Apports,
    val butinRecherche: Map<String, Int>,
    /** Objets notés au retour de raid. */
    val objetsNotes: Int = 0,
    /** Objectifs terminés d'eux-mêmes, tout ayant été rapporté. */
    val termines: List<Objectif> = emptyList(),
)
