package io.github.slaynaw.carnetduraider.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.slaynaw.carnetduraider.CarnetViewModel
import io.github.slaynaw.carnetduraider.core.inventaire.AnalyseInventaire
import io.github.slaynaw.carnetduraider.core.inventaire.ContenuCase
import io.github.slaynaw.carnetduraider.core.inventaire.EtiquetteCase
import io.github.slaynaw.carnetduraider.core.inventaire.TriInventaire
import io.github.slaynaw.carnetduraider.inventaire.AnalyseurCamera
import io.github.slaynaw.carnetduraider.inventaire.PreparationReconnaissance
import io.github.slaynaw.carnetduraider.ui.theme.Prompt
import io.github.slaynaw.carnetduraider.ui.theme.Signal
import java.util.Locale
import java.util.concurrent.Executors
import androidx.compose.ui.geometry.Size as TailleCompose

/** Une étiquette posée sur l'aperçu de la caméra : le rectangle de la case à l'écran, et ce qu'elle dit. */
private class EtiquetteVue(val rectangle: RectF, val etiquette: EtiquetteCase, val nombre: Int?)

/** Ce que la dernière image analysée a donné : cases vues (par leur cadre), objets reconnus, temps d'analyse. */
private class CompteImage(val vues: Int, val reconnues: Int, val dureeMs: Long)

/**
 * Filmer mon inventaire : la caméra en direct sur l'écran du jeu, une étiquette sur chaque case reconnue (VENDS,
 * VENDS n, RECYCLE, GARDE, BUILD, ou « ? » quand ce n'est pas sûr), et dessous la liste « À vendre » qui se
 * remplit pendant qu'on fait défiler l'inventaire en jeu, avec son total ; « Terminé » ouvre la liste complète
 * ([EcranInventaireFilme]). L'autorisation Caméra est demandée à la première ouverture. Rien n'est enregistré
 * ni envoyé : les images sont analysées sur le téléphone puis oubliées, et la liste s'efface à la fermeture.
 */
@Composable
fun EcranFilmerInventaire(vm: CarnetViewModel, onFermer: () -> Unit) {
    val contexte = LocalContext.current
    fun cameraAutorisee() = ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var autorisee by remember { mutableStateOf(cameraAutorisee()) }
    var refusee by remember { mutableStateOf(false) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        autorisee = ok
        refusee = !ok
    }
    // Première ouverture : l'autorisation est demandée d'elle-même ; au retour des réglages d'Android, relue.
    LaunchedEffect(Unit) {
        if (!autorisee) demande.launch(Manifest.permission.CAMERA)
    }
    LifecycleResumeEffect(Unit) {
        autorisee = cameraAutorisee()
        onPauseOrDispose { }
    }
    var liste by remember { mutableStateOf(false) }
    var bilan by remember { mutableStateOf(vm.suiviInventaire.bilan()) }
    // Retour : de la liste complète à la caméra, puis on ferme.
    BackHandler { if (liste) liste = false else onFermer() }

    // Cet écran s'ouvre par-dessus l'appli, hors de sa structure : la couleur du texte (claire) lui est donnée ici,
    // sans quoi tout texte sans couleur propre serait noir sur le fond sombre.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CompositionLocalProvider(LocalContentColor provides Signal.Texte) {
            when {
                liste -> EcranInventaireFilme(vm, bilan, onFermer = onFermer)
                autorisee -> CameraEtListe(vm, bilan, changerBilan = { bilan = it }, terminer = { liste = true }, onFermer = onFermer)
                else -> DemandeCamera(
                    refusee,
                    autoriser = { demande.launch(Manifest.permission.CAMERA) },
                    ouvrirReglages = {
                        contexte.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexte.packageName, null)))
                    },
                    onFermer = onFermer,
                )
            }
        }
    }
}

@Composable
private fun CameraEtListe(
    vm: CarnetViewModel,
    bilan: TriInventaire.Bilan,
    changerBilan: (TriInventaire.Bilan) -> Unit,
    terminer: () -> Unit,
    onFermer: () -> Unit,
) {
    val contexte = LocalContext.current
    val cycle = LocalLifecycleOwner.current
    // La reconnaissance se prépare pendant que l'aperçu démarre (quelques secondes la première fois).
    val analyse by produceState<AnalyseInventaire?>(null) {
        value = PreparationReconnaissance.obtenir(contexte, vm.catalogue.tous)
    }
    var etiquettes by remember { mutableStateOf(emptyList<EtiquetteVue>()) }
    var cadres by remember { mutableStateOf(emptyList<RectF>()) }
    var compte by remember { mutableStateOf<CompteImage?>(null) }
    var imagesSansGrille by remember { mutableIntStateOf(0) }
    var imagesSansObjet by remember { mutableIntStateOf(0) }
    val controleur = remember {
        LifecycleCameraController(contexte).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
        }
    }
    // L'écran reste allumé pendant qu'on filme.
    val vue = LocalView.current
    DisposableEffect(vue) {
        vue.keepScreenOn = true
        onDispose { vue.keepScreenOn = false }
    }
    DisposableEffect(cycle) {
        if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            controleur.bindToLifecycle(cycle)
        }
        onDispose { controleur.unbind() }
    }
    val pret = analyse
    DisposableEffect(pret) {
        if (pret == null) return@DisposableEffect onDispose { }
        val executeur = Executors.newSingleThreadExecutor()
        controleur.setImageAnalysisAnalyzer(
            executeur,
            AnalyseurCamera(pret, ContextCompat.getMainExecutor(contexte)) { image ->
                vm.suiviInventaire.observer(image.cases)
                changerBilan(vm.suiviInventaire.bilan())
                etiquettes = vm.triInventaire.etiqueter(image.cases).zip(image.rectangles) { e, r -> EtiquetteVue(r, e.etiquette, e.nombre) }
                cadres = image.cadres
                val reconnues = image.cases.count { it.contenu is ContenuCase.Objet }
                compte = CompteImage(image.cadres.size, reconnues, image.dureeMs)
                imagesSansGrille = if (image.cadres.isEmpty()) imagesSansGrille + 1 else 0
                imagesSansObjet = if (image.cadres.isNotEmpty() && reconnues == 0) imagesSansObjet + 1 else 0
            },
        )
        onDispose {
            controleur.clearImageAnalysisAnalyzer()
            executeur.shutdown()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = controleur
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            CalqueEtiquettes(etiquettes, cadres, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(12.dp)) {
                Surface(
                    onClick = onFermer,
                    shape = CircleShape,
                    color = Signal.Fond.copy(alpha = 0.6f),
                    contentColor = Signal.Texte,
                    modifier = Modifier.size(44.dp).align(Alignment.TopStart).semantics { contentDescription = "Fermer la caméra" },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PuceDirect()
                    val indication = when {
                        pret == null -> "Préparation de la reconnaissance…"
                        imagesSansGrille >= 8 -> "Mets toute la grille dans l'image, téléphone bien en face de l'écran"
                        imagesSansObjet >= 6 -> "Je vois les cases, pas les objets : touche l'image pour faire la mise au point, et tiens le téléphone immobile un instant"
                        else -> "Fais défiler ton inventaire : j'ajoute ce que je vois"
                    }
                    Text(
                        indication,
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.Texte,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 40.dp)
                            .background(Signal.Fond.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    compte?.let { c ->
                        Text(
                            "${c.vues} case${if (c.vues > 1) "s" else ""} vue${if (c.vues > 1) "s" else ""} · ${c.reconnues} reconnue${if (c.reconnues > 1) "s" else ""} · ${"%.1f".format(Locale.FRANCE, c.dureeMs / 1000.0)}\u00a0s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Signal.TexteDoux,
                            modifier = Modifier
                                .background(Signal.Fond.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        FeuilleAVendre(vm, bilan, preparation = pret == null, terminer = terminer, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.46f))
    }
}

/** « En direct · rien n'est enregistré », avec un point doré qui bat doucement (immobile sans animations). */
@Composable
private fun PuceDirect() {
    val battement = if (animationsActives()) {
        rememberInfiniteTransition(label = "direct").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "point",
        ).value
    } else {
        1f
    }
    Row(
        Modifier.background(Signal.Fond.copy(alpha = 0.62f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).alpha(battement).background(Signal.Or, CircleShape))
        Text("En direct · rien n'est enregistré", style = MaterialTheme.typography.labelMedium, color = Signal.Texte)
    }
}

/**
 * Les étiquettes des cases, posées sur l'aperçu : un fin cadre clair sur chaque case trouvée (reconnue ou non : le
 * joueur voit que la grille est vue), un cadre doré autour de ce qui se vend, un mot sous chaque case reconnue.
 */
@Composable
private fun CalqueEtiquettes(etiquettes: List<EtiquetteVue>, cadres: List<RectF>, modifier: Modifier) {
    val mesure = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.6.sp)
    Canvas(modifier) {
        val fin = 1.dp.toPx()
        for (r in cadres) {
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(r.left, r.top), TailleCompose(r.width(), r.height()), CornerRadius(4.dp.toPx()), style = Stroke(fin))
        }
        val trait = 1.5.dp.toPx()
        for (e in etiquettes) {
            val (texte, couleur) = libelle(e)
            val r = e.rectangle
            if (e.etiquette == EtiquetteCase.VENDS || e.etiquette == EtiquetteCase.VENDS_EN_PARTIE || e.etiquette == EtiquetteCase.RECYCLE) {
                drawRoundRect(couleur, Offset(r.left, r.top), TailleCompose(r.width(), r.height()), CornerRadius(4.dp.toPx()), style = Stroke(trait))
            }
            val mise = mesure.measure(texte, style)
            val largeur = mise.size.width + 8.dp.toPx()
            val hauteur = mise.size.height + 2.dp.toPx()
            val x = r.centerX() - largeur / 2
            val y = r.bottom - hauteur * 0.6f
            drawRoundRect(couleur, Offset(x, y), TailleCompose(largeur, hauteur), CornerRadius(2.dp.toPx()))
            drawText(mise, color = Signal.SurCouleur, topLeft = Offset(x + 4.dp.toPx(), y + 1.dp.toPx()))
        }
    }
}

private fun libelle(e: EtiquetteVue): Pair<String, Color> = when (e.etiquette) {
    EtiquetteCase.VENDS -> "VENDS" to Signal.Or
    EtiquetteCase.VENDS_EN_PARTIE -> "VENDS ${e.nombre ?: ""}".trim() to Signal.Or
    EtiquetteCase.RECYCLE -> "RECYCLE" to Signal.Cyan
    EtiquetteCase.GARDE -> "GARDE" to Signal.Vert
    EtiquetteCase.BUILD -> "BUILD" to Signal.Violet
    EtiquetteCase.PAS_SUR -> "?" to Signal.TexteDoux
}

/** La feuille sous la caméra : ce qui est à vendre jusqu'ici, avec le total, puis ce qui se garde et ce qui n'est pas sûr. */
@Composable
private fun FeuilleAVendre(vm: CarnetViewModel, bilan: TriInventaire.Bilan, preparation: Boolean, terminer: () -> Unit, modifier: Modifier) {
    Surface(color = Signal.Surface, contentColor = Signal.Texte, shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp), modifier = modifier) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 8.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(Signal.Trait, RoundedCornerShape(2.dp)))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Etiquette("À vendre · ${bilan.aVendre.size}", Signal.Or, Modifier.weight(1f))
                Text("≈ ${milliers(bilan.total)} pièces", style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 18.sp), color = Signal.Or)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when {
                    preparation -> Text("Préparation de la reconnaissance…", style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, modifier = Modifier.padding(vertical = 8.dp))
                    bilan.aVendre.isEmpty() -> Text(
                        "Rien pour l'instant : vise ton inventaire en jeu, la liste se remplit d'elle-même.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal.TexteDoux,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    else -> {
                        bilan.aVendre.take(3).forEachIndexed { i, ligne ->
                            if (i > 0) HorizontalDivider(color = Signal.Releve)
                            LigneVenteFilmee(vm, ligne)
                        }
                        val autres = bilan.aVendre.size - 3
                        if (autres > 0) {
                            Text(
                                "et ${pluriel(autres, "autre", "autres")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Signal.TexteDiscret,
                                modifier = Modifier.padding(start = 46.dp, top = 2.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
                RangeeFeuille("À garder · ${bilan.objetsAGarder}", Signal.Vert, "objectifs et build", terminer)
                RangeeFeuille("Pas sûr · ${bilan.pasSur}", Signal.TexteDoux, "approche-toi", terminer)
            }
            Surface(
                onClick = terminer,
                color = Signal.Or,
                contentColor = Signal.SurCouleur,
                shape = CutCornerShape(bottomEnd = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp).heightIn(min = 52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                    Text("Terminé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RangeeFeuille(titre: String, couleur: Color, detail: String, onClick: () -> Unit) {
    Column {
        HorizontalDivider(color = Signal.Releve)
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiquette(titre, couleur, Modifier.weight(1f))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Signal.TexteDiscret)
            }
        }
    }
}

/** Une ligne « À vendre » : l'icône, le nom, combien (et ce qu'on en garde), et ce que ça rapporte. */
@Composable
fun LigneVenteFilmee(vm: CarnetViewModel, ligne: TriInventaire.LigneVente, modifier: Modifier = Modifier) {
    val sur = ligne.sur
    val detail = when {
        sur != null -> "${ligne.quantite} sur $sur · garde ${sur - ligne.quantite}"
        ligne.recycler -> if (ligne.quantite > 1) "À recycler · ×${ligne.quantite}" else "À recycler"
        ligne.quantite > 1 -> "×${ligne.quantite}"
        else -> null
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconeObjet(ligne.objetId, vm.catalogue.objet(ligne.objetId)?.rarete, 34.dp)
        Column(Modifier.weight(1f)) {
            Text(ligne.nom, style = MaterialTheme.typography.titleSmall, color = Signal.Texte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Signal.TexteDoux, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(milliers(ligne.total), style = TextStyle(fontFamily = Prompt, fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Signal.Or)
    }
}

/** Sans autorisation : à quoi sert la caméra, et comment l'autoriser. */
@Composable
private fun DemandeCamera(refusee: Boolean, autoriser: () -> Unit, ouvrirReglages: () -> Unit, onFermer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Signal.Fond).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(IconeCamera, contentDescription = null, tint = Signal.Or, modifier = Modifier.size(44.dp))
        Text("Filmer ton inventaire", style = MaterialTheme.typography.headlineSmall, color = Signal.Texte)
        Text(
            "Pour reconnaître tes objets, l'appli a besoin de la caméra. Les images sont analysées sur le téléphone puis oubliées : rien n'est enregistré ni envoyé.",
            style = MaterialTheme.typography.bodyMedium,
            color = Signal.TexteDoux,
        )
        if (refusee) {
            Text(
                "Tu peux aussi l'autoriser dans les réglages d'Android, à « Autorisations ».",
                style = MaterialTheme.typography.bodyMedium,
                color = Signal.TexteDoux,
            )
        }
        Surface(
            onClick = autoriser,
            color = Signal.Or,
            contentColor = Signal.SurCouleur,
            shape = CutCornerShape(bottomEnd = 12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                Text("Autoriser la caméra", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (refusee) TextButton(onClick = ouvrirReglages) { Text("Ouvrir les réglages d'Android", color = Signal.Or) }
        TextButton(onClick = onFermer) { Text("Fermer", color = Signal.TexteDoux) }
        Spacer(Modifier.height(8.dp))
    }
}

/** La pastille « Filmer mon inventaire » de l'onglet Objets : verre doré sombre, en bas à droite, la liste passe dessous. */
@Composable
fun BoutonFilmerInventaire(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = Signal.Texte,
        shape = CircleShape,
        modifier = modifier.padding(end = 16.dp, bottom = 14.dp).shadow(8.dp, CircleShape, clip = false),
    ) {
        Row(
            Modifier
                .background(FondVerreOr)
                .border(1.dp, Signal.Or.copy(alpha = 0.32f), CircleShape)
                .heightIn(min = 44.dp)
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(IconeCamera, contentDescription = null, tint = Signal.Or, modifier = Modifier.size(20.dp))
            Text("FILMER MON INVENTAIRE", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp), color = Signal.Texte, maxLines = 1)
        }
    }
}

/** Verre doré sombre, opaque à 86 % : la liste se devine dessous. */
private val FondVerreOr = Brush.verticalGradient(listOf(Color(0xDB42341A), Color(0xDB2C2313)))

/** Un appareil photo, l'objectif évidé : la pastille « Filmer mon inventaire ». */
val IconeCamera: ImageVector by lazy {
    ImageVector.Builder(name = "Camera", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(4f, 6f)
            lineTo(7.2f, 6f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(16.8f, 6f)
            lineTo(20f, 6f)
            arcTo(2f, 2f, 0f, false, true, 22f, 8f)
            lineTo(22f, 18f)
            arcTo(2f, 2f, 0f, false, true, 20f, 20f)
            lineTo(4f, 20f)
            arcTo(2f, 2f, 0f, false, true, 2f, 18f)
            lineTo(2f, 8f)
            arcTo(2f, 2f, 0f, false, true, 4f, 6f)
            close()
            moveTo(12f, 8.6f)
            arcTo(4.4f, 4.4f, 0f, false, true, 12f, 17.4f)
            arcTo(4.4f, 4.4f, 0f, false, true, 12f, 8.6f)
            close()
        }
        .build()
}

/** « 53 840 » : les milliers séparés par une espace insécable. */
fun milliers(n: Int): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()
