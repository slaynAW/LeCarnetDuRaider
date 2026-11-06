package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.Besoin
import io.github.slaynaw.carnetduraider.core.ButinRecherche
import io.github.slaynaw.carnetduraider.core.Catalogue
import io.github.slaynaw.carnetduraider.core.FiltreVerdict
import io.github.slaynaw.carnetduraider.core.Objectif
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.Sortie
import io.github.slaynaw.carnetduraider.core.UsageBuild
import io.github.slaynaw.carnetduraider.core.UsagesObjet
import io.github.slaynaw.carnetduraider.core.Verdict
import io.github.slaynaw.carnetduraider.ui.theme.Signal

@Composable
fun EcranObjets(vm: CarnetViewModel, recherche: String, onRecherche: (String) -> Unit, ouvrir: (String) -> Unit, filmer: () -> Unit) {
    var filtre by rememberSaveable { mutableStateOf(FiltreVerdict.TOUS) }
    // Sans recherche : tout le catalogue, par ordre alphabétique.
    val resultats = remember(recherche) {
        if (recherche.isBlank()) vm.catalogue.tous else vm.catalogue.rechercher(recherche, limite = 200)
    }
    val verdicts = resultats.associate { it.id to vm.verdict(it.id) }
    val affiches = if (filtre == FiltreVerdict.TOUS) resultats else resultats.filter { filtre.accepte(verdicts.getValue(it.id)) }
    // Sans recherche ni filtre : ce qui est à garder d'abord, puis le reste par lettre.
    val parSections = recherche.isBlank() && filtre == FiltreVerdict.TOUS
    val aGarder = if (parSections) affiches.filter { verdicts.getValue(it.id) !is Verdict.Inutile } else emptyList()
    val reste = if (parSections) affiches.filter { verdicts.getValue(it.id) is Verdict.Inutile } else affiches

    EcranDeSection(Onglet.OBJETS, "Garder, vendre ou recycler") { defilement, haut ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = defilement,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = haut + 12.dp, bottom = PLACE_BOUTON_RETOUR),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        OutlinedTextField(
                            value = recherche,
                            onValueChange = onRecherche,
                            placeholder = { Text("Cherche un objet…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Signal.Or) },
                            trailingIcon = if (recherche.isNotEmpty()) {
                                { IconButton(onClick = { onRecherche("") }) { Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche", tint = Signal.TexteDoux) } }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Signal.Or,
                                unfocusedBorderColor = Signal.Trait,
                                focusedContainerColor = Signal.Surface,
                                unfocusedContainerColor = Signal.Surface,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.filtresObjets.forEach { f ->
                                val n = if (f == FiltreVerdict.TOUS) resultats.size else resultats.count { f.accepte(verdicts.getValue(it.id)) }
                                PuceFiltre("${f.libelle} · $n", choisie = f == filtre, couleur = Signal.Or, onClick = { filtre = f })
                            }
                        }
                        if (affiches.isEmpty()) {
                            Text("Aucun objet ne correspond.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
                        } else if (!parSections) {
                            Text(pluriel(affiches.size, "objet trouvé", "objets trouvés"), style = MaterialTheme.typography.bodySmall, color = Signal.TexteDiscret)
                        }
                    }
                }
                if (aGarder.isNotEmpty()) {
                    item(key = "titre-garder") { Etiquette("À garder · ${aGarder.size}", Signal.Vert, Modifier.padding(top = 4.dp)) }
                    items(aGarder, key = { it.id }) { objet -> LigneObjet(vm, objet, verdicts.getValue(objet.id), onClick = { ouvrir(objet.id) }) }
                }
                if (parSections && reste.isNotEmpty()) {
                    item(key = "titre-reste") { Etiquette("À vendre ou recycler · ${reste.size}", Signal.Or, Modifier.padding(top = 10.dp)) }
                    reste.groupBy { initiale(it.nom) }.forEach { (lettre, objets) ->
                        item(key = "lettre-$lettre") {
                            Text(
                                lettre,
                                style = MaterialTheme.typography.titleSmall,
                                color = Signal.Or,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).semantics { heading() },
                            )
                        }
                        items(objets, key = { it.id }) { objet -> LigneObjet(vm, objet, verdicts.getValue(objet.id), onClick = { ouvrir(objet.id) }) }
                    }
                } else if (!parSections) {
                    items(reste, key = { it.id }) { objet -> LigneObjet(vm, objet, verdicts.getValue(objet.id), onClick = { ouvrir(objet.id) }) }
                }
            }

            // Filmer son inventaire en jeu : ce qui se vend sans souci, en direct. Toujours à portée de pouce.
            BoutonFilmerInventaire(filmer, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** Première lettre du nom, sans accent (« É » → « E ») ; « # » pour un chiffre ou un signe. */
private fun initiale(nom: String): String =
    Catalogue.normaliser(nom).firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

@Composable
private fun LigneObjet(vm: CarnetViewModel, objet: Objet, verdict: Verdict, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Signal.Surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeObjet(objet.id, objet.rarete, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(objet.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(raisonVerdict(vm, objet, verdict), style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PuceVerdict(verdict)
        }
    }
}

/** Pourquoi ce verdict, en une ligne : pour quel objectif ou quel build, ou ce qu'il rapporte. */
private fun raisonVerdict(vm: CarnetViewModel, objet: Objet, verdict: Verdict): String = when (verdict) {
    is Verdict.Garder -> verdict.pour.firstOrNull()?.let { b ->
        (if (b.objectif is Objectif.ObjetRecherche) "Dans ton butin recherché" else "Pour ${b.libelle}") +
            if (verdict.pour.size > 1) " et ${pluriel(verdict.pour.size - 1, "autre", "autres")}" else ""
    } ?: "Pour tes objectifs en cours"
    is Verdict.PourBuild -> "Pour le build «\u00a0${verdict.builds.first().build}\u00a0»"
    is Verdict.Inutile ->
        if (verdict.conseil == Sortie.RECYCLER) "Ses matériaux valent ${vm.catalogue.valeurRecyclage(objet)} pièces" else "Se vend ${objet.valeur} pièces"
}

/** Le verdict en un mot, pour la liste : Garder (objectifs), Build, Vendre ou Recycler, encadré de sa couleur. */
@Composable
fun PuceVerdict(verdict: Verdict) {
    val (texte, couleur) = when (verdict) {
        is Verdict.Garder -> "Garder" to Signal.Vert
        is Verdict.PourBuild -> "Build" to Signal.Violet
        is Verdict.Inutile -> if (verdict.conseil == Sortie.VENDRE) "Vendre" to Signal.Or else "Recycler" to Signal.Cyan
    }
    Etiquette(
        texte,
        couleur,
        Modifier
            .border(1.5.dp, couleur, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Fiche d'un objet, dans l'ordre où le joueur se pose les questions : le verdict (garder, vendre ou
 * recycler), à quoi il peut servir, où le trouver, puis ce qu'il rapporte. [ouvrirZone] ouvre le plan sur une zone de butin.
 */
@Composable
fun EcranFiche(
    vm: CarnetViewModel,
    objetId: String,
    onRetour: () -> Unit,
    ouvrir: (String) -> Unit = {},
    ouvrirZone: (carteId: String, zone: String) -> Unit = { _, _ -> },
) {
    val objet = vm.catalogue.objet(objetId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Bandeau("Objet", Signal.Or)
        }
        if (objet == null) {
            // Nom français officiel pas encore vérifié : l'objet est exclu des données, jamais affiché avec un nom inventé.
            Text(Catalogue.NOM_A_VERIFIER, style = MaterialTheme.typography.headlineMedium)
            Text(
                "Le nom français officiel de cet objet n'est pas encore vérifié\u00a0: l'appli ne l'affiche pas, " +
                    "pour ne jamais inventer de nom. Il le sera dès qu'il aura été vérifié dans le jeu en français.",
                style = MaterialTheme.typography.bodyLarge,
                color = Signal.TexteDoux,
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconeObjet(objet.id, objet.rarete, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(objet.nom, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(objet.rarete?.libelle, objet.categorie, "se vend ${objet.valeur}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }

        val usages = vm.usagesDe(objet.id)
        CarteVerdict(vm.verdict(objet.id), objet, usages, vm)
        BoutonButinRecherche(vm, objet.id)
        SectionUsages(vm, usages, ouvrir)
        CommentLObtenir(vm, objet, ouvrir, ouvrirZone)
        Section("Vente et recyclage", "Se vend ${objet.valeur} pièces" + if (objet.recyclage.isEmpty()) " · ne se recycle pas" else " · se recycle") {
            Text("Se vend ${objet.valeur} pièces l'unité.", style = MaterialTheme.typography.bodyLarge)
            if (objet.recyclage.isEmpty()) {
                Text("Ne se recycle pas.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
            } else {
                Text(
                    "Recyclage en (matériaux\u00a0: ${vm.catalogue.valeurRecyclage(objet)} pièces)\u00a0:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Signal.TexteDoux,
                )
                objet.recyclage.forEach { LigneQuantite(vm, it.id, it.quantite) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Ajoute l'objet au butin recherché de l'onglet Raid (un de plus s'il y est déjà). */
@Composable
private fun BoutonButinRecherche(vm: CarnetViewModel, objetId: String) {
    val voulu = vm.butinRecherche[objetId]
    OutlinedButton(
        onClick = { vm.ajouterAuButin(objetId) },
        border = BorderStroke(1.5.dp, Signal.Rouge),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Signal.Rouge),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            if (voulu == null) "Ajouter au ${ButinRecherche.TITRE.lowercase()}" else "${ButinRecherche.TITRE}\u00a0: $voulu · en ajouter un",
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Tout ce que les données du jeu disent sur la façon d'obtenir l'objet, du butin aux récompenses. */
@Composable
private fun CommentLObtenir(vm: CarnetViewModel, objet: Objet, ouvrir: (String) -> Unit, ouvrirZone: (String, String) -> Unit) {
    val o = objet.obtention
    val recyclage = vm.catalogue.sourcesRecyclage(objet.id)
    if (objet.lieux.isNotEmpty() || o.cartes.isNotEmpty() || o.zones.isNotEmpty()) {
        Section(
            "Où le trouver",
            o.cartes.firstOrNull()?.let { "Surtout ${vm.catalogue.carte(it.carte)?.nom ?: Catalogue.NOM_A_VERIFIER}" } ?: objet.lieux.joinToString(" · "),
        ) {
            if (objet.lieux.isNotEmpty()) {
                Text("Dans les zones\u00a0: ${objet.lieux.joinToString(" · ")}", style = MaterialTheme.typography.bodyLarge)
            }
            if (o.cartes.isNotEmpty()) {
                SousTitreFiche("Meilleures cartes")
                o.cartes.forEach { c -> LigneIndice(vm.catalogue.carte(c.carte)?.nom ?: Catalogue.NOM_A_VERIFIER, null, c.indice) }
            }
            if (o.zones.isNotEmpty()) {
                SousTitreFiche("Meilleures zones")
                o.zones.take(5).forEach { z ->
                    LigneIndice(z.zone, vm.catalogue.carte(z.carte)?.nom, z.indice, onClick = { ouvrirZone(z.carte, z.zone) })
                }
                Text(
                    "Classement d'après les tables de butin du jeu (condition normale), 100 pour le meilleur endroit. " +
                        "Les taux de drop exacts ne sont pas publiés\u00a0: le jeu tire le butin sur ses serveurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Signal.TexteDoux,
                )
            }
        }
    }
    if (o.achats.isNotEmpty()) {
        Section("Chez les commerçants", o.achats.map { it.commercant }.distinct().joinToString(" · ")) {
            o.achats.forEach { a ->
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val prix = buildList {
                        if (a.pieces > 0) add("${a.pieces} pièces")
                        if (a.valeurObjets > 0) add("des objets valant ${a.valeurObjets} pièces")
                    }.joinToString(" + ")
                    val pour = if (a.quantite > 1) "${a.quantite} pour " else ""
                    Text("${a.commercant}${if (prix.isNotEmpty()) "\u00a0: $pour$prix" else ""}", style = MaterialTheme.typography.bodyLarge)
                    a.objets.forEach { LigneQuantite(vm, it.id, it.quantite) }
                    a.limite?.let { n ->
                        Text(
                            if (a.parJour) "$n par jour au plus" else pluriel(n, "achat possible", "achats possibles"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
        }
    }
    if (o.fabrications.isNotEmpty()) {
        Section("Fabrication", o.fabrications.first().let { f -> f.etabli?.let { e -> f.niveau?.let { "$e · niveau $it" } ?: e } ?: "Pendant un raid" }) {
            o.fabrications.forEach { f ->
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val ou = f.etabli?.let { e -> f.niveau?.let { "$e · niveau $it" } ?: e } ?: "Pendant un raid"
                    Text(if (f.quantite > 1) "$ou (en donne ${f.quantite})" else ou, style = MaterialTheme.typography.bodyLarge)
                    f.plan?.let { Text("Plan à débloquer\u00a0: $it", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux) }
                    f.ingredients.forEach { LigneQuantite(vm, it.id, it.quantite) }
                }
            }
        }
    }
    if (recyclage.isNotEmpty()) {
        Section("En recyclant", pluriel(recyclage.size, "objet en donne", "objets en donnent")) {
            recyclage.take(6).forEach { q ->
                Surface(onClick = { ouvrir(q.id) }, color = Signal.Fond, shape = RoundedCornerShape(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconeObjet(q.id, vm.catalogue.objet(q.id)?.rarete, 32.dp)
                        Text(vm.catalogue.nomObjet(q.id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("en donne ${q.quantite}", style = MaterialTheme.typography.labelMedium, color = Signal.TexteDoux)
                    }
                }
            }
            if (recyclage.size > 6) {
                Text("Et ${pluriel(recyclage.size - 6, "autre objet", "autres objets")}.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
    }
    if (o.recompenses.isNotEmpty()) {
        Section("En récompense", pluriel(o.recompenses.size, "quête", "quêtes")) {
            o.recompenses.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun SousTitreFiche(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleSmall, color = Signal.Or, modifier = Modifier.padding(top = 6.dp))
}

/** Un endroit et son indice (1 à 100), avec une barre ; cliquable pour ouvrir le plan. */
@Composable
private fun LigneIndice(nom: String, sousTitre: String?, indice: Int, onClick: (() -> Unit)? = null) {
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null, color = Signal.Fond, shape = RoundedCornerShape(4.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nom, style = MaterialTheme.typography.bodyLarge)
                    sousTitre?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux) }
                }
                Text("$indice", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Signal.Or)
                if (onClick != null) Text("  Plan ›", style = MaterialTheme.typography.labelMedium, color = Signal.Cyan)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Signal.Releve)) {
                Box(Modifier.fillMaxWidth(indice / 100f).height(4.dp).background(Signal.Or))
            }
        }
    }
}

/**
 * Une partie de la fiche, en accordéon : son titre et un [resume] d'une ligne ; le détail se déplie d'un
 * geste. La fiche tient ainsi sur un écran, l'essentiel (le verdict) en tête.
 */
@Composable
private fun Section(titre: String, resume: String = "", ouvertAuDepart: Boolean = false, contenu: @Composable () -> Unit) {
    var ouvert by rememberSaveable(titre) { mutableStateOf(ouvertAuDepart) }
    Column {
        EnTeteAccordeon(titre, resume, ouvert, Signal.Or, onClick = { ouvert = !ouvert })
        if (ouvert) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contenu() }
        }
    }
}

/** Ce que dit la carte du verdict : titre, explication, couleur, objectifs et builds concernés, note. */
private data class ContenuVerdict(
    val titre: String,
    val texte: String,
    val fond: Color,
    val pour: List<Besoin> = emptyList(),
    val builds: List<UsageBuild> = emptyList(),
    val note: String? = null,
)

@Composable
private fun CarteVerdict(verdict: Verdict, objet: Objet, usages: UsagesObjet, vm: CarnetViewModel) {
    val valeurRecyclage = vm.catalogue.valeurRecyclage(objet)
    fun sortie(sortie: Sortie): String = when {
        sortie == Sortie.RECYCLER -> "recycle-le\u00a0: ses matériaux valent $valeurRecyclage pièces, plus que l'objet (${objet.valeur})."
        objet.recyclage.isEmpty() -> "vends-le\u00a0: il vaut ${objet.valeur} pièces et ne se recycle pas."
        else -> "vends-le\u00a0: il vaut ${objet.valeur} pièces, plus que ses matériaux ($valeurRecyclage)."
    }
    val c = when (verdict) {
        is Verdict.Garder -> ContenuVerdict(
            "Garde-le",
            "Il en faut encore ${verdict.manquant} pour " + when {
                verdict.pour.all { it.objectif is Objectif.ObjetRecherche } -> "ton butin recherché."
                verdict.pour.any { it.objectif is Objectif.ObjetRecherche } -> "tes objectifs en cours et ton butin recherché."
                else -> "tes objectifs en cours."
            },
            Signal.Vert,
            verdict.pour,
            verdict.builds,
        )
        is Verdict.PourBuild -> ContenuVerdict(
            "Garde-le",
            if (verdict.builds.all { it.direct }) "Il fait partie de tes builds." else "Il sert à fabriquer ce qu'il y a dans tes builds.",
            Signal.Violet,
            builds = verdict.builds,
        )
        is Verdict.Inutile -> ContenuVerdict(
            if (verdict.conseil == Sortie.VENDRE) "Vends-le" else "Recycle-le",
            "Aucun objectif en cours ni aucun build n'en a besoin\u00a0: ${sortie(verdict.conseil)}",
            Signal.Releve,
            note = if (usages.aucun) null else "Il peut quand même servir\u00a0: ${usages.resume().joinToString(", ")}. Le détail est plus bas.",
        )
    }
    val texte = if (c.fond == Signal.Releve) Signal.Texte else Signal.SurCouleur
    Surface(color = c.fond, contentColor = texte, shape = FormeCoupee, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiquette("Verdict", texte)
            Text(c.titre, style = MaterialTheme.typography.headlineLarge)
            Text(c.texte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (c.pour.isNotEmpty() || c.builds.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = texte.copy(alpha = 0.3f))
                c.pour.forEach { besoin ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(besoin.libelle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${minOf(besoin.apporte, besoin.quantite)}/${besoin.quantite}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                c.builds.forEach { b ->
                    Text(
                        if (b.direct) "Build «\u00a0${b.build}\u00a0»" else "Build «\u00a0${b.build}\u00a0»\u00a0: pour ${vm.catalogue.nomObjet(b.objetId)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** À quoi peut servir l'objet dans le jeu : description officielle, puis tout ce qui le demande. */
@Composable
private fun SectionUsages(vm: CarnetViewModel, u: UsagesObjet, ouvrir: (String) -> Unit) {
    val ut = u.utilisations
    Section(
        "À quoi il peut servir",
        u.resume().joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Vente ou recyclage seulement" },
        ouvertAuDepart = true,
    ) {
        u.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (u.aucun) {
            Text("Rien d'autre que la vente ou le recyclage, d'après les données du jeu.", style = MaterialTheme.typography.bodyMedium, color = Signal.TexteDoux)
        }
        if (ut.cle) LigneUsage("Clé", "Elle ouvre une porte ou une salle\u00a0: voir la description.")
        if (ut.equipement) LigneUsage("Équipement", "Il s'utilise en raid.")
        if (u.ameliorations.isNotEmpty()) {
            LigneUsage("Établis", u.ameliorations.joinToString(" · ") { "${it.etabli.nom} niveau ${it.niveau} (${it.quantite})" })
        }
        if (u.quetes.isNotEmpty()) {
            LigneUsage("Quêtes", u.quetes.joinToString(" · ") { "«\u00a0${it.quete.nom}\u00a0» (${it.quantite})" })
        }
        if (u.projets.isNotEmpty()) {
            LigneUsage("Projets", u.projets.joinToString(" · ") { "${it.projet.nom}, ${vm.planificateur.nomEtape(it.projet, it.etape)} (${it.quantite})" })
        }
        if (ut.fabrication.isNotEmpty()) {
            SousTitreFiche("Sert à fabriquer")
            ut.fabrication.take(6).forEach { f ->
                Surface(onClick = { ouvrir(f.objet) }, color = Signal.Fond, shape = RoundedCornerShape(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconeObjet(f.objet, vm.catalogue.objet(f.objet)?.rarete, 32.dp)
                        Text(vm.catalogue.nomObjet(f.objet), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            f.etabli?.let { e -> f.niveau?.let { "$e · niv. $it" } ?: e } ?: "en raid",
                            style = MaterialTheme.typography.labelMedium,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
            if (ut.fabrication.size > 6) {
                Text("Et ${pluriel(ut.fabrication.size - 6, "autre objet", "autres objets")}.", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
        if (ut.amplification.isNotEmpty()) {
            LigneUsage("Amplification", ut.amplification.joinToString(" · ") { vm.catalogue.nomObjet(it) })
        }
        if (ut.aApprendre) {
            val quoi = ut.debloque.map { vm.catalogue.nomObjet(it) } + ut.meubles
            LigneUsage("À apprendre", if (quoi.isEmpty()) "Il débloque une fabrication." else "Il débloque\u00a0: ${quoi.joinToString(", ")}.")
        }
        if (ut.apparence) LigneUsage("Apparence", "Une apparence à débloquer.")
        if (ut.recherches.isNotEmpty()) LigneUsage("Station de recherche", abrege(ut.recherches))
        if (ut.avantPoste.isNotEmpty()) LigneUsage("Avant-poste", abrege(ut.avantPoste))
        if (ut.echanges.isNotEmpty()) {
            LigneUsage("Échanges", ut.echanges.take(4).joinToString(" · ") { "${it.commercant}\u00a0: ${it.donne} contre ${it.quantite} × ${vm.catalogue.nomObjet(it.objet)}" } +
                if (ut.echanges.size > 4) " · et ${ut.echanges.size - 4} autres" else "")
        }
        if (ut.echangesValeur.isNotEmpty()) {
            LigneUsage("Échanges", "${ut.echangesValeur.joinToString(", ")} l'accepte en échange, au prix de sa valeur.")
        }
    }
}

/** « A, B, C et 4 autres ». */
private fun abrege(noms: List<String>, nombre: Int = 3): String =
    noms.take(nombre).joinToString(", ") + if (noms.size > nombre) " et ${noms.size - nombre} autres" else ""

@Composable
private fun LigneUsage(titre: String, texte: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(titre, style = MaterialTheme.typography.titleSmall, color = Signal.Cyan)
        Text(texte, style = MaterialTheme.typography.bodyMedium)
    }
}

/** « 3 × Pièces métalliques » avec l'icône de l'objet. */
@Composable
fun LigneQuantite(vm: CarnetViewModel, objetId: String, quantite: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconeObjet(objetId, vm.catalogue.objet(objetId)?.rarete, 32.dp)
        Text("$quantite × ${vm.catalogue.nomObjet(objetId)}", style = MaterialTheme.typography.bodyLarge)
    }
}
