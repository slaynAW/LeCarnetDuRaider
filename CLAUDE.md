# Consignes du projet « Le Carnet du Raider »

Application Android compagnon (non officielle) pour ARC Raiders. Tout le projet est en français :
interface, documentation, messages de commit, noms de classes et de fonctions métier.

## Règle absolue : uniquement les traductions françaises officielles d'Embark

Tout texte qui vient du jeu (cartes, objets, ARC, compétences, conditions de la carte, établis, quêtes,
commerçants, raretés, catégories…) doit être **la traduction française officielle d'Embark Studios**,
telle qu'elle apparaît dans le jeu.

- Ne jamais traduire, adapter ni « deviner » un nom du jeu, même s'il paraît évident.
- Les noms viennent uniquement de `core/src/main/resources/donnees-jeu.json`, produit par
  `scripts/importer_donnees_jeu.py` à partir des données du jeu. Ne pas modifier ce fichier à la main.
- Les icônes des objets (`app/src/main/assets/objets/<identifiant>.webp`) sont les icônes officielles
  du jeu, réduites par le même script (Pillow). Ne jamais en dessiner ni en ajouter à la main.
  De même pour les plans des cartes (`app/src/main/assets/cartes/<carte>.webp`, assemblés à partir des
  tuiles du plan en jeu de `public/data/map-data`), leurs lieux, sorties et points d'apparition des ARC
  (`Carte.plan`, noms français officiels de `map-strings.fr.json`). La recherche sur le plan
  (`RecherchePlan`) n'affiche que ces noms ; les mots de famille (« trappe », « ascenseur », « arc »…)
  servent seulement à chercher, jamais de nom affiché.
- Comment obtenir un objet (`Objet.obtention`, produit par le script) : meilleures cartes et zones (portage
  du calcul de raider-tools sur les tables de butin, condition normale, indice de 1 à 100), achats,
  fabrications, récompenses ; les sources de recyclage sont calculées dans `Catalogue`. Les vrais taux de
  drop ne sont pas dans les fichiers du jeu (tirage côté serveur) : ne jamais présenter l'indice comme un
  pourcentage ni comme un taux de drop.
- À quoi sert un objet : sa description officielle (`Objet.description`, gardée seulement si le jeu la
  traduit) et `Objet.utilisations` (fabrications, recherches, Avant-poste, échanges, amplification,
  schémas, clés, équipement), produits par le script ; les établis, quêtes et projets qui le demandent
  sont calculés dans `Usages`.
- Un nom sans traduction officielle vérifiée n'entre pas dans l'appli : le script l'exclut et le liste
  dans `docs/NOMS-A-VERIFIER.md`. Dans l'interface, un objet absent s'affiche « Nom à vérifier en jeu »
  (`Catalogue.NOM_A_VERIFIER`), jamais avec un nom inventé.
- Un nom identique en français et en anglais n'est accepté que s'il figure, avec sa justification,
  dans `scripts/noms-identiques-verifies.txt`.
- Les quêtes cachées du jeu (`hidden` : déblocages de contenu après quelques manches, un niveau, une
  carte visitée) ne sont pas importées : faites d'office, leurs suites ne les attendent pas. Les
  objectifs ne demandent que de vrais objets, jamais un compteur (« Manches disputées »).
- Les libellés fixes du code qui désignent un élément du jeu (raretés dans `Rarete`, « Atelier »,
  « Recyclage en », « Conditions de la carte »…) reprennent le texte officiel du jeu.
- Le reste du texte de l'interface (phrases d'aide, boutons propres à l'appli) est en français courant.

Les données sont mises à jour par le workflow « Données du jeu » (`.github/workflows/donnees.yml`,
lancé à la main : le dépôt raider-tools n'est plus public depuis le 20/10/2026 ; remettre le lancement
quotidien s'il le redevient) : import en lecture seule, tests du module core, puis commit sur
`main` des seuls fichiers de données (refus de tout autre fichier) et relance du build. Bibliothèques
Python de l'import à versions et empreintes fixées (`scripts/requirements-import.txt`). À la main :

```bash
git clone --depth 1 https://github.com/baschny/arcraiders-raider-tools /tmp/raider-tools
python3 scripts/importer_donnees_jeu.py /tmp/raider-tools/public/data/game
./gradlew :core:test
```

Puis relire `docs/NOMS-A-VERIFIER.md` : chaque nouveau nom exclu est à vérifier dans le jeu en français.

## Typographie française

Espace insécable (` ` dans le code Kotlin) avant `:`, `?`, `!`, `%` et à l'intérieur des
guillemets `« … »`. Apostrophe droite `'` acceptée dans le code.

## Architecture

- `core/` : Kotlin pur, sans Android. Données du jeu (`DonneesJeu`, `Catalogue`), liste de courses et
  objets à trouver (`Planificateur`), builds (`Builds`), à quoi peut servir un objet (`Usages`) et
  verdict « garder, recycler ou vendre » (`Conseiller`). Toute logique métier va ici, avec ses tests
  JUnit (`./gradlew :core:test`).
- Verdict (`core/Verdict.kt`) : un objet n'est à garder que s'il sert à un objectif en cours (et qu'il en
  manque encore) ou à un build du joueur (dans le build, ou pour le fabriquer ou l'améliorer, de proche
  en proche : `Builds.aGarder`). Sinon il est à vendre ou à recycler (ce qui rapporte le plus), et sa
  fiche dit quand même à quoi il peut servir (`Usages`, « À quoi il peut servir »). Le verdict s'affiche
  aussi dans la liste des objets, avec sa raison et des filtres (`FiltreVerdict`, `Conseiller.filtres` :
  « Recycler » seulement si un objet rapporte plus recyclé que vendu, aucun avec les données actuelles).
- `app/` : interface Jetpack Compose, style « Signal » (`ui/theme/Theme.kt`) : fond sombre, et une
  couleur par domaine — or pour les objets, cyan pour les cartes, vert pour les objectifs, rouge pour
  le raid et les ARC, violet pour les builds. Six onglets : Accueil, Objets, Raid, Cartes, Objectifs,
  Builds ; le joueur doit en faire le moins possible (rien à préparer, un geste pour valider ou noter un
  objet trouvé). Polices Prompt (titres) et Barlow (texte), sous licence OFL (`third_party/polices/`).
  En-tête commun à toutes les sections (`EnTete` : logo, nom, réglages) ; objets affichés avec leur
  icône sur une tuile aux couleurs de leur rareté (`IconeObjet`). Chaque onglet s'ouvre sur un bandeau de
  sa couleur (`BandeauSection` : code « 0N / … » de `Onglet.code`, « 01 / SPERANZA » pour l'Accueil, nom en capitales, motif propre à la
  section, coin coupé ; le motif vit en boucle, lentement : reflet, cases de l'inventaire, cible qui cherche puis
  verrouille, repère et son onde, impulsion des chevrons, ligne de scan ; le texte jamais, et rien avec « Supprimer
  les animations »), l'onglet actif prend la même couleur (`BarreOnglets` : une seule pastille glisse d'un onglet à l'autre en prenant sa couleur, le nouvel écran apparaît en fondu) ; il se replie en barre de 56 dp dès que la liste
  défile (`EcranDeSection` : posé par-dessus la liste, qui garde sa hauteur et réserve la place du bandeau
  déplié ; le repli ne la fait jamais sauter, même quand elle dépasse à peine de l'écran). Cartes à coins presque droits (6 dp), cartes mises en avant au coin coupé
  (`FormeCoupee`), petites étiquettes en capitales (`Etiquette`). Écrans compacts (refonte UX 0.14) : le
  bouton principal fixé en bas (`BoutonFixe`), le secondaire en accordéons (`EnTeteAccordeon` : objectifs et
  détail des cartes du Raid, parties de la fiche d'un objet, « Autres » des builds), listes d'une ligne par
  objet ; la liste des objets met « À garder » en tête, puis le reste par lettre.
- **Pas de réserve** (rien de ce que le joueur a « en stock ») : il ne note que ce qu'il rapporte de raid,
  au retour de raid. Ce qu'il a en cours, ce qui a été rapporté pour chaque objectif et ses builds sont
  gardés dans l'appli (`Memoire`) : l'appli ne se connecte jamais au compte Embark et ne touche jamais au
  jeu (pas de lecture de mémoire, de trafic ou de jeton).
  **Tout est fait d'office, et à l'installation rien n'est en cours** : aucun établi
  (`Progression.etablisSuivis`), aucune quête (`quetesIndiquees`), aucun projet ni Expédition. Les
  objectifs saisis avant la 0.10 repartent de zéro une fois (`Memoire.nouveauDepart`).
- Sauvegarde chiffrée (`core/…/sauvegarde`, `app/…/sauvegarde`) : fichiers de réglages « carnet » et
  « alertes » écrits automatiquement (quelques secondes après chaque changement) dans un fichier choisi
  par le joueur avec le sélecteur d'Android (Google Drive, dossier du téléphone). PBKDF2-SHA256
  (600 000 tours) + AES-256-GCM, en-tête authentifié ; mot de passe jamais enregistré, clé dérivée gardée
  chiffrée par le Keystore d'Android. L'appli n'ouvre aucune connexion pour cela. Pas de sauvegarde
  sur GitHub : écrire sur GitHub exige un jeton, qu'on ne peut pas mettre dans un APK public.
- Objectifs (`core/Progression.kt`, `ui/EcranObjectifs.kt`) : quatre tuiles seulement au départ, sur une
  ligne (nombre en cours et « Quêtes / Ateliers / Projets / Expéditions », libellé accessible « Indiquer
  mes … en cours »), chacune vers la liste complète ; dessous, ce qu'il reste à trouver (vers le Raid) et
  les objectifs groupés (ateliers, projets, Expéditions, quêtes), objets demandés en pastilles.
  Le joueur coche ses quêtes en cours (tout le reste est fait d'office, sauf ce qui vient après :
  `SuiviProgression.indiquerQuetesEnCours`), choisit le niveau en cours de chaque établi
  (`indiquerNiveauEnCours`) et l'étape en cours de chaque projet ou Expédition (`indiquerEtapeEnCours`,
  `Projet.expedition`), ou « Terminé » (`indiquerEtabliTermine`, `indiquerProjetTermine` : niveau maximum,
  toutes les étapes ; piste « — 1 2 3 ✓ » sur la largeur, `Piste` : « ✓ » en dernier, seul le choix plein de
  vert ; terminé, la carte se réduit à une ligne sobre, `CarteChoix` : nom, « ✓ Terminé » et « Modifier », qui
  rouvre la piste ; au passage à « Terminé » sous les yeux du joueur, et seulement là, le « balayage » : une
  fine ligne verte traverse la carte, le fond se teinte derrière elle, puis la carte se replie en sa ligne,
  la teinte s'efface et « ✓ Terminé » apparaît, à peine plus d'une seconde, coupé avec « Supprimer les
  animations »). Ce qui est en cours s'affiche sous les tuiles. Un objectif dont tout a été
  rapporté se valide tout seul (`SuiviProgression.terminerLesComplets`) et la suite s'ajoute (niveau
  suivant, quêtes suivantes d'après l'enchaînement `requiert`, étape suivante). Ce qui est fait autrement
  se valide en un geste (« C'est fait », `CarnetViewModel.valider`, après le même balayage sur sa carte,
  `dessinBalayage` ; pour la dernière étape ou le dernier niveau, `Planificateur.derniereEtape`, la clôture
  suit : un bandeau sombre recouvre la carte, « TERMINÉ » s'y resserre en vert, une fine ligne verte s'étire
  et les quatre bandes du logo glissent en place, puis tout reste affiché 1 seconde, `Cloture`, et la carte s'efface en fondu), avec « Annuler » (3 secondes puis fondu, `BandeauValidation`) : jamais de saisie obligatoire.
- Mes builds (`core/Builds.kt`, `ui/EcranBuilds.kt`) : six builds (en onglets de même largeur sur une ligne, `OngletBuild` :
  nom, cases remplies ou « vide », trait violet sous le choisi ; le build préféré, un seul, `Build.prefere`, étoile, en premier et ouvert à l'arrivée :
  `Builds.ordre`, `Builds.ouvert` ; le build choisi dessous, nom changé au crayon, cases groupées
  Équipement / Armes / Consommables (au changement de build, il glisse légèrement depuis le côté de son onglet), « Autres » repliées), un nom et un objet par case (Module, Bouclier,
  Arme 1, Arme 2, Soins, Grenades, puis six « Autres » : `CaseBuild.AUTRE_1` garde le nom enregistré
  « DIVERS » de l'ancienne case ; la fiche et « Vider la case » sont dans le choix de l'objet),
  d'après `Objet.emplacement` (famille de l'objet dans le jeu, produit par le script) ; matériaux d'après
  les recettes et `Obtention.amelioration` (arme du niveau d'avant et coût de l'amélioration) ; « Pour le
  fabriquer » avec les quantités (`Builds.aFabriquer`). `Memoire`, clé « builds » (JSON).
- Préparer mon raid, onglet Raid (`core/PreparationRaid.kt`, `ui/EcranRaid.kt`) : rien à préparer. Il
  reprend les objectifs en cours, avec la même carte que l'onglet Objectifs (`CarteObjectif`) : ce sont
  les mêmes objectifs, y compris ceux qui ne demandent aucun objet. Ce
  qu'il faut trouver (`Planificateur.aTrouver`) = ce qui manque aux objectifs en cours ; cartes classées
  par la somme des indices de butin des objets encore à trouver (chaque objet compte au plus 1 par
  carte), zones où chercher, et parcours de 1 à 3 cartes pour tout trouver. L'accueil (`EcranAccueil`) montre les cinq
  sections sans défiler : le prochain raid sur toute la largeur (meilleure carte, objets à trouver, sa meilleure zone et
  son nombre d'objets en pastille ; capture qui bouge lentement « à l'épaule » avec deux glitchs très brefs par
  boucle de 16 s, `FondEnJeuAnime`, immobile avec « Supprimer les animations » ; bandes du logo en pied), puis la liste de bord, une seule carte à une ligne par section (`LigneDeBord` : icône sur une pastille de la
  couleur de la section, titre, précision, chiffre à droite) : objets à garder et le plus demandé ; condition de la carte
  en cours, sa carte et ce qu'il lui reste (sinon la prochaine) ; objectifs en cours par sorte ; build préféré, ses
  matériaux et ses cases remplies. En tête du Raid, la meilleure carte (« Où chercher » déplie ses zones et la carte
  suivante du parcours), puis ce qu'il te faut en liste compacte ; « Mes objectifs » et « Où aller »
  repliés ; « Retour de raid » en bas à droite (`BoutonRetourRaid`) : petite pastille en verre rouge
  opaque à 84 %, la liste passe dessous en filigrane ; en boucle, lentement, les bandes du logo s'allument puis
  le texte bascule de « RETOUR DE RAID » à « INDIQUER MON BUTIN » (rien avec « Supprimer les animations »). « Meilleure carte » (et « Ton prochain raid » à l'accueil) s'affiche sur une capture en jeu de la carte (`FondEnJeu`,
  `app/src/main/assets/fonds/<carte>.webp`, « ombre latérale » : assombrie à gauche sous le texte), sinon
  sur le rouge du Raid ; l'onglet Cartes montre chaque carte sur la sienne. Ces captures sont les seules images qui ne viennent pas du script d'import : prises
  en jeu et fournies par le mainteneur, jamais d'un site tiers.
- Butin recherché (`core/ButinRecherche.kt`, section de l'onglet Raid, `ui/EcranChoixButin.kt`, bouton sur
  la fiche d'un objet) : des objets que le joueur veut rapporter pour lui, sans objectif du jeu (objet →
  quantité voulue, `Memoire`, clé « butinRecherche »). Ils deviennent des `Objectif.ObjetRecherche` dans la
  liste du raid (à trouver, meilleure carte, retour de raid, verdict « Garder »), jamais dans l'onglet
  Objectifs ; le butin va d'abord aux objectifs du jeu, puis à eux ; un objet dont tout a été rapporté
  quitte la liste à la validation du retour de raid (`ButinRecherche.terminerLesComplets`).
- Filmer mon inventaire (`core/…/inventaire`, `app/…/inventaire`, `ui/EcranFilmerInventaire.kt`,
  `ui/EcranInventaireFilme.kt`, proposition C du canevas) : pastille dorée « FILMER MON INVENTAIRE » de l'onglet
  Objets (`BoutonFilmerInventaire`) ; caméra en direct (CameraX, autorisation Caméra demandée à la première
  ouverture), une étiquette sur chaque case reconnue (VENDS, VENDS n, RECYCLE, GARDE, BUILD, « ? »), et dessous la
  liste « À vendre » qui se remplit avec son total ; « Terminé » ouvre la liste complète (à garder par objectif
  puis par build). Reconnaissance sur le téléphone, jamais en ligne : grille des cases (`DetectionGrille` : bords
  qui reviennent à pas fixe, téléphone en face de l'écran), icône comparée aux icônes officielles de l'appli
  (`Reconnaisseur` : sous le détourage, puis posée sur le fond de la case ; « sûr » seulement au-dessus d'un seuil
  et nettement devant la suivante, sinon « Pas sûr », jamais compté à vendre), nombre lu dans le coin de la case
  (`LectureNombre`, illisible plutôt que faux). Seuils à régler sur de vraies images filmées. Verdict de l'onglet
  Objets (`TriInventaire`) : d'une pile que les objectifs demandent encore, garder ce qui manque et vendre le
  reste ; nombre illisible ou build : tout garder. Cumul pendant qu'on fait défiler (`SuiviInventaire` : un objet
  compte après deux images, son nombre est le plus souvent lu). Rien n'est enregistré ni envoyé : les images sont
  analysées puis oubliées, la liste s'efface à la fermeture (pas de réserve).
- Retour de raid (`core/RetourDeRaid.kt`, `ui/EcranRetourRaid.kt`), le seul endroit où le joueur note
  des objets : seulement ce qui manque, sans pouvoir dépasser. `Planificateur.repartir` donne le butin
  aux objectifs qui le demandent (objectifs du jeu, puis butin recherché), le plus près d'être terminé
  d'abord (`Memoire`, clé « apports » : clé de l'objectif → objets rapportés) ; ce qui dépasse n'est gardé
  nulle part. Une ligne par objet : toucher l'icône en ajoute un (appui long : cinq), « − n/voulu + ». À la validation (bouton,
  retour ou autre onglet, jamais une rotation), les objectifs complets se terminent et
  `RetourDeRaid.bilan` donne les objectifs terminés, les objets qui ont atteint le nombre demandé et si
  toute la liste est trouvée : animation `ui/CelebrationRaid.kt` (objectif terminé à la manière d'ARC
  Raiders : bande verte, « OBJECTIF TERMINÉ », tampon « VALIDÉ » ; « Raid terminé, objets trouvés »),
  puis « Annuler » pendant 3 secondes.
- Calendrier des conditions de la carte (`core/…/calendrier`, `app/…/calendrier`) : lu sur la page
  officielle d'Embark (arcraiders.com/map-conditions, JSON embarqué dans la page, noms anglais), puis
  affiché avec les noms français officiels des données du jeu (correspondance par `cleCalendrier`).
  Un nom inconnu s'affiche « Nom à vérifier en jeu ». Onglet Cartes : « En ce moment » (chaque carte, ses
  conditions et « Plan ») ou « À venir » (prochaines heures et prochaine fois, une cloche par ligne) ;
  région en pastille. Relu à chaque lancement de l'appli (au plus toutes
  les 5 minutes), comme la recherche de mise à jour. Le workflow « Calendrier officiel » vérifie
  chaque jour que la page est toujours lisible.
- Alertes (`core/…/calendrier/Alertes.kt`, `app/…/alertes`) : un seul réveil Android programmé à la
  fois (le prochain), calculé sur le calendrier gardé sur le téléphone ; reprogrammé au démarrage du
  téléphone, à la mise à jour de l'appli et quand le calendrier ou les réglages changent.
- Connexions de l'appli : uniquement GitHub (mises à jour) et arcraiders.com (calendrier). Jamais le
  compte Embark ni les serveurs du jeu, jamais de jeton de connexion du joueur. La caméra (Filmer mon
  inventaire) n'envoie rien : la reconnaissance se fait sur le téléphone.
- Mises à jour intégrées (`core/…/miseajour`, `app/…/miseajour`) : vers les Releases GitHub du dépôt,
  qui doit être **public** (sinon l'appli ne peut pas lire les fichiers publiés). Le manifeste
  `update.json` est signé par la CI avec la clé de l'APK et vérifié avec le certificat de l'appli
  installée ; l'APK doit avoir la taille et l'empreinte annoncées.
  Ne jamais affaiblir ces contrôles ni ajouter d'autre source de mise à jour (docs/MISES-A-JOUR.md).

## Aperçu web (à tenir à jour)

`apercu/` contient une version web de l'appli (mêmes données, mêmes règles que `core/`, réécrites en
JavaScript dans `apercu/modele.html`), publiée comme artifact claude.ai :
https://claude.ai/artifact/5pwyAtzHArvPaTgxtnU3ek. **À chaque fonction ajoutée ou modifiée dans
l'appli, reporter le changement dans `apercu/modele.html`**, puis `python3 apercu/construire.py`
et republier `apercu/index.html` sur la même URL, avec en fichiers joints `apercu/icones.json` et les
plans `apercu/cartes/<carte>.webp`. Ce qui
ne peut pas tourner dans une page web (calendrier en direct, alertes, mises à jour, caméra de « Filmer mon
inventaire », dont l'aperçu trie un exemple d'inventaire) y est signalé comme propre à l'appli Android.

## Publication et clé de signature

- La clé de signature n'est **jamais** dans le dépôt ni dans un job autre que « Publication
  officielle » (environnement protégé `publication`, secrets `CDR_KEYSTORE_*`). Voir docs/SIGNATURE.md.
- Une version officielle exige une section `## X.Y.Z` dans `NOUVEAUTES.md` (texte affiché dans
  l'appli) ; les noms du jeu y suivent la règle des traductions officielles.
- Actions épinglées par empreinte de commit, jeton en lecture seule par défaut. Ces règles sont vérifiées à
  chaque build par le job « Règles de sécurité » (`.github/scripts/verifier-depot.sh`) : ne jamais
  l'affaiblir, il bloque la publication. Réglages GitHub recommandés (compte, règles de `main` et des
  tags `v*`, environnement, Actions, alertes) : docs/SECURITE.md ; signalement des failles : SECURITY.md.

## Vérifications

Le SDK Android n'est pas installable dans les sessions cloud (domaine bloqué) : la CI GitHub
(`.github/workflows/build.yml`) compile l'appli, lance le lint et produit l'APK. En session cloud, les
tests du module `core` peuvent tourner sur une copie du projet limitée à `core`, avec le miroir Maven
Central de Google (`https://maven-central.storage-download.googleapis.com/maven2/`).
