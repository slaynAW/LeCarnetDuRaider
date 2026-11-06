package io.github.slaynaw.carnetduraider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.inventaire.TriInventaire
import io.github.slaynaw.carnetduraider.ui.theme.Signal

/**
 * « Terminé » : tout ce que la caméra a vu, en une liste. Ce qui se vend sans souci, du plus rentable au moins
 * rentable, avec le total ; puis ce qu'il faut garder, par objectif et par build ; puis ce qui n'a pas été
 * reconnu sûrement (jamais compté à vendre). Rien n'est gardé : la liste s'efface quand on la ferme ; le retour
 * ramène à la caméra pour continuer.
 */
@Composable
fun EcranInventaireFilme(vm: CarnetViewModel, bilan: TriInventaire.Bilan, onFermer: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Signal.Fond).windowInsetsPadding(WindowInsets.safeDrawing)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = PLACE_BOUTON_FIXE)) {
            item {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onFermer) { Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Signal.Texte) }
                    Text("Ce que tu peux vendre", style = MaterialTheme.typography.titleLarge, color = Signal.Texte)
                }
            }
            item {
                Surface(color = Signal.Or, contentColor = Signal.SurCouleur, shape = FormeCoupee, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (bilan.aVendre.isEmpty()) "Rien à vendre" else "${pluriel(bilan.aVendre.size, "objet", "objets")} à vendre",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (bilan.aVendre.isNotEmpty()) {
                            Text("≈ ${milliers(bilan.total)} pièces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Rien n'est gardé : cette liste s'efface quand tu la fermes.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            itemsIndexed(bilan.aVendre, key = { _, l -> "vendre-${l.objetId}" }) { i, ligne ->
                if (i > 0) HorizontalDivider(color = Signal.Releve)
                LigneVenteFilmee(vm, ligne)
            }
            if (bilan.aGarder.isNotEmpty()) {
                item(key = "garder") { Etiquette("À garder · ${bilan.objetsAGarder}", Signal.Vert, Modifier.padding(top = 18.dp)) }
                bilan.aGarder.forEach { groupe -> item(key = "groupe-${groupe.libelle}") { GroupeAGarder(vm, groupe) } }
            }
            if (bilan.pasSur > 0) {
                item(key = "pas-sur") {
                    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Etiquette("Pas sûr · ${bilan.pasSur}", Signal.TexteDoux)
                        Text(
                            (if (bilan.pasSur > 1) "Ces cases n'ont" else "Cette case n'a") +
                                " pas été reconnue${if (bilan.pasSur > 1) "s" else ""} sûrement : rien n'est compté à vendre pour elle${if (bilan.pasSur > 1) "s" else ""}. " +
                                "Refilme de plus près, sans reflet sur l'écran.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Signal.TexteDoux,
                        )
                    }
                }
            }
        }
        BoutonFixe("Fermer", Signal.Or, onFermer, Modifier.align(Alignment.BottomCenter))
    }
}

/** Ce qu'un objectif (en vert) ou un build (en violet) demande de garder : chaque objet et combien. */
@Composable
private fun GroupeAGarder(vm: CarnetViewModel, groupe: TriInventaire.GroupeGarde) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(groupe.libelle, style = MaterialTheme.typography.labelMedium, color = if (groupe.build) Signal.Violet else Signal.Vert)
        groupe.objets.forEach { (id, quantite) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconeObjet(id, vm.catalogue.objet(id)?.rarete, 28.dp)
                Text(vm.catalogue.nomObjet(id), style = MaterialTheme.typography.bodyMedium, color = Signal.Texte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(quantite?.let { "×$it" } ?: "tout", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
            }
        }
    }
}
