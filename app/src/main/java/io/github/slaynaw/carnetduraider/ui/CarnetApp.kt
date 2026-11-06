package io.github.slaynaw.carnetduraider.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import kotlinx.coroutines.delay

/**
 * Les six onglets, chacun avec sa couleur et le code de son bandeau ([BandeauSection]) : ce qui compte
 * maintenant, garder ou vendre un objet, où aller au prochain raid, les cartes, ce qu'on a en cours, et ses builds.
 */
enum class Onglet(val titre: String, val code: String, val icone: ImageVector, val couleur: Color) {
    ACCUEIL("Accueil", "01 / SPERANZA", Icons.Filled.Home, Signal.Texte),
    OBJETS("Objets", "02 / INVENTAIRE", Icons.Filled.Search, Signal.Or),
    RAID("Raid", "03 / OPÉRATION", Icons.Filled.ShoppingCart, Signal.Rouge),
    CARTES("Cartes", "04 / TERRAIN", Icons.Filled.Place, Signal.Cyan),
    OBJECTIFS("Objectifs", "05 / MISSIONS", Icons.Filled.CheckCircle, Signal.Vert),
    BUILDS("Builds", "06 / ARSENAL", Icons.Filled.Build, Signal.Violet),
}

@Composable
fun CarnetApp(ongletDemande: Onglet? = null, demandeTraitee: () -> Unit = {}, vm: CarnetViewModel = viewModel()) {
    var onglet by rememberSaveable { mutableStateOf(Onglet.ACCUEIL) }
    var objetOuvert by rememberSaveable { mutableStateOf<String?>(null) }
    var recherche by rememberSaveable { mutableStateOf("") }
    var reglages by rememberSaveable { mutableStateOf(false) }
    var choixEnCours by rememberSaveable { mutableStateOf<ChoixEnCours?>(null) }
    // Case de build à remplir : « rang:CASE ».
    var choixBuild by rememberSaveable { mutableStateOf<String?>(null) }
    var retourRaid by rememberSaveable { mutableStateOf(false) }
    var choixButin by rememberSaveable { mutableStateOf(false) }
    var planOuvert by rememberSaveable { mutableStateOf<String?>(null) }
    var planRecherche by rememberSaveable { mutableStateOf("") }
    // Filmer mon inventaire : la caméra en plein écran, par-dessus tout le reste.
    var filmer by rememberSaveable { mutableStateOf(false) }
    val calendrier by vm.calendrier.etat.collectAsStateWithLifecycle()
    val calendrierOfficiel = remember(calendrier.donnees) { calendrier.donnees?.let(vm::calendrierOfficiel) }
    val miseAJour by vm.miseAJour.etat.collectAsStateWithLifecycle()
    var dialogueMiseAJour by rememberSaveable { mutableStateOf(false) }
    var versionProposee by rememberSaveable { mutableStateOf("") }

    // À chaque lancement de l'appli (et à chaque retour) : recherche d'une mise à jour et relecture du
    // calendrier officiel des conditions de la carte, sauf si c'est déjà fait depuis quelques minutes.
    LifecycleStartEffect(Unit) {
        vm.miseAJour.rechercherSiNecessaire()
        vm.calendrier.actualiserSiNecessaire()
        onStopOrDispose { }
    }

    // Nouvelle version : proposée d'elle-même une fois (sauf « Plus tard » dans les dernières 24 h).
    LaunchedEffect(miseAJour.nouvelle?.version, miseAJour.reportee) {
        val version = miseAJour.nouvelle?.version ?: return@LaunchedEffect
        if (!miseAJour.reportee && versionProposee != version) {
            versionProposee = version
            dialogueMiseAJour = true
        }
    }

    // La caméra fermée, ce qu'elle a vu s'efface : rien n'est gardé.
    fun fermerCamera() {
        filmer = false
        vm.viderInventaireFilme()
    }

    fun aller(cible: Onglet) {
        if (filmer) fermerCamera()
        objetOuvert = null
        reglages = false
        choixEnCours = null
        choixBuild = null
        retourRaid = false
        choixButin = false
        planOuvert = null
        onglet = cible
    }

    // Retour de raid quitté (« Valider », retour, autre onglet) : il est validé, et l'animation montre ce
    // qui a été trouvé. Une rotation de l'écran ne le valide pas (l'état est gardé).
    LaunchedEffect(retourRaid) {
        if (!retourRaid) vm.validerRetourDeRaid()
    }

    // Ouverture depuis une notification : directement sur l'onglet demandé.
    LaunchedEffect(ongletDemande) {
        ongletDemande?.let {
            aller(it)
            demandeTraitee()
        }
    }

    // Retour : d'abord l'écran ouvert par-dessus l'onglet, puis l'accueil.
    BackHandler(
        enabled = objetOuvert != null || choixEnCours != null || choixBuild != null || retourRaid || choixButin || planOuvert != null || reglages ||
            onglet != Onglet.ACCUEIL,
    ) {
        when {
            planOuvert != null -> planOuvert = null
            objetOuvert != null -> objetOuvert = null
            choixEnCours != null -> choixEnCours = null
            choixBuild != null -> choixBuild = null
            retourRaid -> retourRaid = false
            choixButin -> choixButin = false
            reglages -> reglages = false
            else -> onglet = Onglet.ACCUEIL
        }
    }

    Box(Modifier.fillMaxSize()) {
        val anime = animationsActives()
        Scaffold(
            containerColor = Signal.Fond,
            topBar = {
                EnTete(reglagesOuverts = reglages) {
                    objetOuvert = null
                    choixEnCours = null
                    choixBuild = null
                    retourRaid = false
                    choixButin = false
                    planOuvert = null
                    reglages = true
                }
            },
            bottomBar = {
                BarreOnglets(onglet, ::aller)
            },
        ) { marges ->
            Box(Modifier.fillMaxSize().padding(marges)) {
                val id = objetOuvert
                when {
                    // Le plan passe devant la fiche : ouvert depuis une zone de butin, le retour ramène à la fiche.
                    planOuvert != null -> EcranPlan(vm, planOuvert!!, planRecherche, onRetour = { planOuvert = null })
                    id != null -> EcranFiche(
                        vm,
                        id,
                        onRetour = { objetOuvert = null },
                        ouvrir = { objetOuvert = it },
                        ouvrirZone = { carte, zone ->
                            planRecherche = zone
                            planOuvert = carte
                        },
                    )
                    choixEnCours != null -> when (choixEnCours) {
                        ChoixEnCours.QUETES -> EcranQuetesEnCours(vm, onRetour = { choixEnCours = null })
                        ChoixEnCours.ATELIERS -> EcranAteliersEnCours(vm, onRetour = { choixEnCours = null })
                        ChoixEnCours.PROJETS -> EcranProjetsEnCours(vm, expeditions = false, onRetour = { choixEnCours = null })
                        ChoixEnCours.EXPEDITIONS -> EcranProjetsEnCours(vm, expeditions = true, onRetour = { choixEnCours = null })
                        null -> Unit
                    }
                    choixBuild != null -> {
                        val (rang, case) = choixBuild!!.split(':').let { it[0].toInt() to CaseBuild.valueOf(it[1]) }
                        EcranChoixObjetBuild(vm, rang, case, onRetour = { choixBuild = null }, ouvrir = { objetOuvert = it })
                    }
                    retourRaid -> EcranRetourRaid(vm, onRetour = { retourRaid = false })
                    choixButin -> EcranChoixButin(vm, onRetour = { choixButin = false })
                    reglages -> EcranReglages(
                        vm,
                        vm.miseAJour,
                        miseAJour,
                        afficherMiseAJour = { dialogueMiseAJour = true },
                        onRetour = { reglages = false },
                    )
                    // Changement d'onglet : le nouvel écran apparaît en fondu (instantané avec « Supprimer les animations »).
                    else -> AnimatedContent(
                        targetState = onglet,
                        transitionSpec = {
                            if (anime) fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(60))
                            else EnterTransition.None togetherWith ExitTransition.None
                        },
                        label = "onglet",
                    ) { ongletAffiche -> when (ongletAffiche) {
                        Onglet.ACCUEIL -> EcranAccueil(
                            vm,
                            aller = ::aller,
                            nouvelleVersion = miseAJour.nouvelle?.version,
                            calendrier = calendrierOfficiel,
                            afficherMiseAJour = { dialogueMiseAJour = true },
                        )
                        Onglet.OBJETS -> EcranObjets(vm, recherche, { recherche = it }, ouvrir = { objetOuvert = it }, filmer = { filmer = true })
                        Onglet.RAID -> EcranRaid(
                            vm,
                            ouvrir = { objetOuvert = it },
                            ouvrirZone = { carte, zone ->
                                planRecherche = zone
                                planOuvert = carte
                            },
                            retourDeRaid = { retourRaid = true },
                            choisirObjectifs = { aller(Onglet.OBJECTIFS) },
                            ajouterButin = { choixButin = true },
                        )
                        Onglet.CARTES -> EcranCartes(vm, ouvrirPlan = {
                            planRecherche = ""
                            planOuvert = it
                        })
                        Onglet.OBJECTIFS -> EcranObjectifs(vm, choisir = { choixEnCours = it }, voirRaid = { aller(Onglet.RAID) })
                        Onglet.BUILDS -> EcranBuilds(vm, choisir = { rang, case -> choixBuild = "$rang:${case.name}" })
                    } }
                }
                // Après une validation (retour de raid, niveau construit…) : « Annuler » pendant quelques secondes.
                if (vm.celebration == null) BandeauValidation(vm, Modifier.align(Alignment.BottomCenter))
            }
        }

        // Fin du retour de raid : objets trouvés, objectifs terminés, raid terminé.
        vm.celebration?.let { bilan -> CelebrationRaid(vm, bilan, onFin = vm::finCelebration) }

        // Filmer mon inventaire : ce qui se vend sans souci, en direct (son retour à lui ferme la caméra).
        if (filmer) EcranFilmerInventaire(vm, onFermer = ::fermerCamera)
    }

    if (dialogueMiseAJour && miseAJour.nouvelle != null) {
        DialogueMiseAJour(
            miseAJour,
            plusTard = {
                dialogueMiseAJour = false
                vm.miseAJour.reporter()
            },
            installer = vm.miseAJour::installer,
        )
    }
}

/** Après une validation : ce qui a été fait, avec « Annuler » pendant 3 secondes. */
@Composable
private fun BandeauValidation(vm: CarnetViewModel, modifier: Modifier = Modifier) {
    val validation = vm.derniereValidation ?: return
    val anime = animationsActives()
    val opacite = remember(validation) { Animatable(1f) }
    LaunchedEffect(validation) {
        delay(3_000)
        // Un petit fondu avant de disparaître.
        if (anime) opacite.animateTo(0f, tween(300))
        vm.oublierValidation()
    }
    val detail = when {
        validation.objetsNotes > 0 -> pluriel(validation.objetsNotes, "objet noté", "objets notés") +
            if (validation.termines.isEmpty()) "." else ", ${pluriel(validation.termines.size, "objectif terminé", "objectifs terminés")}."
        validation.termines.isNotEmpty() ->
            "Tout était rapporté aussi pour\u00a0: ${validation.termines.joinToString(", ") { vm.planificateur.libelle(it) }}."
        else -> "La suite s'ajoute à tes objectifs."
    }
    // Bande verte au coin coupé, comme « OBJECTIF TERMINÉ » dans le jeu.
    Surface(
        color = Signal.Vert,
        contentColor = Signal.SurCouleur,
        shape = FormeCoupee,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().graphicsLayer { alpha = opacite.value },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etiquette(if (validation.objetsNotes > 0 && validation.termines.isEmpty()) "Validé" else "Objectif terminé", Signal.SurCouleur)
                Text(validation.titre, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = vm::annulerValidation) { Text("Annuler", color = Signal.SurCouleur, fontWeight = FontWeight.Bold) }
        }
    }
}
