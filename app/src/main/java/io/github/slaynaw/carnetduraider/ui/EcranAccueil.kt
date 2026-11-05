package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.CaseBuild
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.core.calendrier.Calendrier
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.time.LocalTime

/**
 * Accueil : les cinq sections d'un coup d'œil, sans faire défiler. En tête, le prochain raid (la carte, sa
 * meilleure zone), puis la liste de bord : une ligne par section (objets à garder, condition de la carte en
 * cours, objectifs en cours, build préféré), son chiffre à droite. Chaque bloc mène à son onglet.
 */
@Composable
fun EcranAccueil(
    vm: CarnetViewModel,
    aller: (Onglet) -> Unit,
    nouvelleVersion: String?,
    /** Calendrier officiel des conditions de la carte, ou `null` s'il n'a pas encore été lu. */
    calendrier: Calendrier?,
    afficherMiseAJour: () -> Unit,
) {
    val donnees = vm.catalogue.donnees
    val heure = LocalTime.now().hour
    val salutation = if (heure >= 18 || heure < 5) "Bonsoir, Raider" else "Bonjour, Raider"

    // Le bandeau se replie quand on fait défiler, comme dans les autres onglets.
    EcranDeSection(Onglet.ACCUEIL, salutation) { defilement, haut ->
        LazyColumn(Modifier.fillMaxSize(), state = defilement, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 14.dp, bottom = 16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nouvelleVersion != null) {
                        Surface(onClick = afficherMiseAJour, color = Signal.Releve, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Signal.Or)
                                Column {
                                    Text("Mise à jour disponible", style = MaterialTheme.typography.titleSmall)
                                    Text("Version $nouvelleVersion : touche pour voir les nouveautés", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                                }
                            }
                        }
                    }

                    ProchainRaid(vm, aller)

                    // Une seule carte, une ligne par section : objets, cartes, objectifs, build.
                    Surface(color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column {
                            LigneObjets(vm, onClick = { aller(Onglet.OBJETS) })
                            HorizontalDivider(color = Signal.Releve)
                            LigneCartes(vm, calendrier, onClick = { aller(Onglet.CARTES) })
                            HorizontalDivider(color = Signal.Releve)
                            LigneObjectifs(vm, onClick = { aller(Onglet.OBJECTIFS) })
                            HorizontalDivider(color = Signal.Releve)
                            LigneBuild(vm, onClick = { aller(Onglet.BUILDS) })
                        }
                    }

                    Text(
                        "Données du jeu ${donnees.versionJeu}, noms français officiels uniquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDiscret,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Où aller au prochain raid pour trouver ce qui manque : la carte, combien d'objets on y trouve, puis sa
 * meilleure zone avec son nombre d'objets bien en vue.
 */
@Composable
private fun ProchainRaid(vm: CarnetViewModel, aller: (Onglet) -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }
    val conseil = remember(restants.map { it.objetId }) { vm.conseilRaid() }
    val premiere = conseil.cartes.firstOrNull()
    // La carte où aller, sur sa capture en jeu (comme « Meilleure carte »), sinon sur le rouge du Raid.
    val fond = rememberFondCarte(premiere?.carteId)
    val surRouge = premiere != null && fond == null
    Surface(
        onClick = { aller(if (restants.isEmpty() && vm.objectifs.isEmpty()) Onglet.OBJECTIFS else Onglet.RAID) },
        color = when {
            premiere == null -> Signal.Surface
            fond != null -> Signal.Fond
            else -> Signal.Rouge
        },
        contentColor = if (surRouge) Signal.SurCouleur else Signal.Texte,
        // Sur toute la largeur de l'écran, par-delà les marges de la liste.
        modifier = Modifier.pleineLargeur(20.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (premiere != null && fond != null) FondEnJeuAnime(fond)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Etiquette("Ton prochain raid", if (premiere != null && fond != null) Signal.Rouge else LocalContentColor.current)
                when {
                    premiere != null -> {
                        Text("Va sur ${vm.catalogue.carte(premiere.carteId)?.nom ?: Catalogue.NOM_A_VERIFIER}", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${premiere.objets.size} de tes ${pluriel(restants.size, "objet", "objets")} à trouver",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val zone = premiere.zones.firstOrNull()
                        if (zone != null) {
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(zone.zone, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                PastilleNombre(pluriel(zone.objets.size, "objet", "objets"), surRouge)
                            }
                        }
                    }
                    restants.isNotEmpty() -> {
                        Text("Rien dans le butin des cartes", style = MaterialTheme.typography.titleMedium)
                        Text("Ce qu'il te faut s'obtient autrement : regarde l'onglet Raid.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                    }
                    vm.objectifs.isNotEmpty() -> {
                        Text("Rien à chercher en raid", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tes objectifs en cours ne demandent aucun objet : valide-les quand c'est fait en jeu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                    else -> {
                        Text("Rien à chercher pour l'instant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Indique ce que tu as en cours (quêtes, ateliers, projets, expéditions) : l'appli te dira où aller.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
            // Les quatre bandes du logo en pied.
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)) {
                listOf(Signal.Cyan, Signal.Vert, Signal.Or, Signal.Rouge).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
            }
        }
    }
}

/** Le nombre d'objets d'une zone, en pastille rouge bien lisible sur la capture (ou sombre sur le rouge). */
@Composable
private fun PastilleNombre(texte: String, surRouge: Boolean) {
    val forme = RoundedCornerShape(50)
    Text(
        texte,
        style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
        color = if (surRouge) Signal.SurCouleur else Signal.Texte,
        maxLines = 1,
        modifier = Modifier
            .background(if (surRouge) Signal.SurCouleur.copy(alpha = 0.14f) else Signal.Rouge.copy(alpha = 0.30f), forme)
            .border(1.dp, if (surRouge) Signal.SurCouleur.copy(alpha = 0.35f) else Signal.Rouge, forme)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Ce qui est à garder (objectifs et builds), avec l'objet le plus demandé encore à trouver. */
@Composable
private fun LigneObjets(vm: CarnetViewModel, onClick: () -> Unit) {
    val restants = vm.aTrouver().filter { !it.trouve }.sortedByDescending { it.reste }
    val aGarder = remember(restants, vm.builds) { vm.catalogue.tous.count { vm.verdict(it.id) !is Verdict.Inutile } }
    val premier = restants.firstOrNull()
    LigneDeBord(
        Onglet.OBJETS,
        titre = "Objets à garder",
        detail = if (premier != null) "Le plus demandé : ${vm.catalogue.nomObjet(premier.objetId)}" else "Garder, recycler ou vendre ?",
        chiffre = "$aGarder",
        onClick = onClick,
    )
}

/**
 * La condition de la carte en cours, sa carte et ce qu'il lui reste ; sinon la prochaine des six heures à venir.
 * Avant la lecture du calendrier officiel, la ligne le dit.
 */
@Composable
private fun LigneCartes(vm: CarnetViewModel, calendrier: Calendrier?, onClick: () -> Unit) {
    val t = maintenant(30_000)
    val enCours = calendrier?.enCours(vm.region, t)?.firstOrNull()
    val suivante = if (calendrier != null && enCours == null) calendrier.aVenir(vm.region, t, 6 * Calendrier.UNE_HEURE).firstOrNull() else null
    when {
        calendrier == null -> LigneDeBord(Onglet.CARTES, "Conditions de la carte", "Lecture du calendrier officiel…", onClick = onClick)
        enCours != null -> LigneDeBord(Onglet.CARTES, enCours.condition, enCours.carte, chiffre = duree(enCours.fin - t), onClick = onClick)
        suivante != null -> LigneDeBord(Onglet.CARTES, suivante.condition, "${suivante.carte} · dans ${duree(suivante.debut - t)}", onClick = onClick)
        else -> LigneDeBord(Onglet.CARTES, "Conditions de la carte", "Rien d'annoncé dans les prochaines heures.", onClick = onClick)
    }
}

/** Les objectifs en cours, par sorte (quêtes, ateliers, projets, expéditions). */
@Composable
private fun LigneObjectifs(vm: CarnetViewModel, onClick: () -> Unit) {
    val objectifs = vm.objectifs
    val quetes = objectifs.count { it is Objectif.QueteATerminer }
    val ateliers = objectifs.count { it is Objectif.NiveauDEtabli }
    val projets = objectifs.filterIsInstance<Objectif.EtapeDeProjet>()
    val expeditions = projets.count { vm.catalogue.projet(it.projetId)?.expedition == true }
    val sortes = listOfNotNull(
        quetes.takeIf { it > 0 }?.let { pluriel(it, "quête", "quêtes") },
        ateliers.takeIf { it > 0 }?.let { pluriel(it, "atelier", "ateliers") },
        (projets.size - expeditions).takeIf { it > 0 }?.let { pluriel(it, "projet", "projets") },
        expeditions.takeIf { it > 0 }?.let { pluriel(it, "expédition", "expéditions") },
    ).joinToString(" · ")
    LigneDeBord(
        Onglet.OBJECTIFS,
        titre = "Objectifs en cours",
        detail = sortes.ifEmpty { "Touche pour indiquer tes quêtes, ateliers, projets ou expéditions" },
        chiffre = "${objectifs.size}",
        onClick = onClick,
    )
}

/** Le build préféré (sinon le premier rempli) : les matériaux pour le fabriquer et ses cases remplies. */
@Composable
private fun LigneBuild(vm: CarnetViewModel, onClick: () -> Unit) {
    val rang = vm.builds.indexOfFirst { it.prefere && !it.vide }.takeIf { it >= 0 } ?: vm.builds.indexOfFirst { !it.vide }
    val build = vm.builds.getOrNull(rang)
    if (build == null) {
        LigneDeBord(Onglet.BUILDS, "Aucun build", "Touche pour préparer ton équipement", onClick = onClick)
        return
    }
    val materiaux = remember(build) { vm.outilsBuilds.aFabriquer(build) }
    LigneDeBord(
        Onglet.BUILDS,
        titre = vm.outilsBuilds.nom(build, rang),
        etoile = build.prefere,
        detail = if (materiaux.isEmpty()) "Rien à fabriquer" else "${pluriel(materiaux.size, "matériau", "matériaux")} à garder",
        chiffre = "${build.objets.size}/${CaseBuild.entries.size}",
        onClick = onClick,
    )
}

/**
 * Une ligne de la liste de bord : l'icône de la section sur une pastille de sa couleur, un titre et sa précision,
 * puis le chiffre à retenir à droite (rien quand il n'y en a pas).
 */
@Composable
private fun LigneDeBord(
    onglet: Onglet,
    titre: String,
    detail: String,
    chiffre: String? = null,
    etoile: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).background(onglet.couleur.copy(alpha = 0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(onglet.icone, contentDescription = null, tint = onglet.couleur, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append(titre)
                    if (etoile) withStyle(SpanStyle(color = Signal.Violet)) { append(" ★") }
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chiffre != null) {
            Text(chiffre, style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp), maxLines = 1)
        }
    }
}

/** « 1 objet », « 3 objets » (le nombre est inclus). */
fun pluriel(n: Int, singulier: String, pluriel: String): String = "$n ${if (n > 1) pluriel else singulier}"
