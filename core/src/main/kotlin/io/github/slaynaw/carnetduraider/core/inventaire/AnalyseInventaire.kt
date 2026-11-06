package io.github.slaynaw.carnetduraider.core.inventaire

import java.util.stream.Collectors
import kotlin.math.sqrt

/**
 * Ce que l'analyse d'une image a vu : le cadre de chaque case trouvée ([cadres], même vide ou à moitié cachée), et
 * ce que contiennent les cases d'objet ([cases]).
 */
data class AnalyseImage(val cadres: List<Zone>, val cases: List<CaseVue>)

/**
 * Une image de la caméra → les cases de l'inventaire qu'elle montre : chaque case trouvée par son cadre, sa
 * rareté lue sur son halo, son objet reconnu parmi les icônes de cette rareté, et la bande du bas lue (nombre
 * d'objets, niveau d'une arme). [piles] : combien d'exemplaires de chaque objet tiennent dans une case (un objet
 * qui ne s'empile pas compte toujours pour un ; un nombre lu plus grand qu'une pile pleine est faux). Tout se
 * fait sur le téléphone : aucune image ne quitte l'appli, et aucune n'est gardée. Seule la taille des cases est
 * retenue d'une image à l'autre, pour les chercher plus vite.
 */
class AnalyseInventaire(
    private val reconnaisseur: Reconnaisseur,
    private val nombres: LectureNombre? = null,
    private val piles: Map<String, Int> = emptyMap(),
) {

    @Volatile
    private var tailleCases: Int? = null

    /**
     * Les cases non vides de [image], de haut en bas et de gauche à droite. Les cases sont reconnues en même temps,
     * sur tous les cœurs du téléphone (l'image n'est que lue).
     */
    fun analyser(image: ImageRgb): List<CaseVue> = analyserImage(image).cases

    /** Toutes les cases trouvées dans [image] par leur cadre, et ce que contiennent celles qui ne sont pas vides. */
    fun analyserImage(image: ImageRgb): AnalyseImage {
        val cases = DetectionGrille.detecter(image, tailleCases)
        tailleCases = cases.map { it.zone.largeur }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val vues = cases.parallelStream().map { case -> analyserCase(image, case.zone) }.collect(Collectors.toList()).filterNotNull()
        return AnalyseImage(cases.map { it.zone }, vues)
    }

    /** Ce que contient la case [zone] ; `null` si elle est vide, ou si ce n'est pas une case d'objet entière. */
    private fun analyserCase(image: ImageRgb, zone: Zone): CaseVue? {
        if (vide(image, zone)) return null
        // Un reflet (une lampe sur l'écran) blanchit l'icône : rien de sûr ne s'y lit.
        if (reflet(image, zone)) return CaseVue(zone, ContenuCase.PasSur)
        val r = reconnaisseur.reconnaitre(image, zone, HaloRarete.lire(image, zone))
        // Aucune icône n'y ressemble même un peu : pas une case d'objet entière (à moitié cachée par le bord).
        if (r.score < Reconnaisseur.SEUIL_CASE) return null
        if (!r.sur) return CaseVue(zone, ContenuCase.PasSur)
        val bande = nombres?.lire(image, zone)
        val ids = auNiveau(r.objetIds, bande?.niveau)
        return CaseVue(zone, ContenuCase.Objet(ids, quantite(ids, bande)))
    }

    /**
     * Combien la case contient : un, pour un objet qui ne s'empile pas ; sinon le nombre lu, s'il tient dans une
     * pile (un niveau lu sur un objet qui s'empile est un « × » mal vu : illisible).
     */
    private fun quantite(ids: List<String>, bande: Bande?): Int? {
        val pile = ids.maxOf { piles[it] ?: Int.MAX_VALUE }
        return when {
            pile <= 1 -> 1
            bande == null || bande.niveau != null -> null
            else -> bande.quantite?.takeIf { it <= pile }
        }
    }

    companion object {
        /** Une icône presque unie (écart de luminance faible) : la case est vide. */
        private const val ECART_VIDE = 8.0

        /** Part de l'icône presque blanche (les trois canaux au-dessus de [BLANC]) au-delà de laquelle c'est un reflet. */
        private const val PART_REFLET = 0.12
        private const val BLANC = 235

        /**
         * Un reflet sur l'icône de la case [zone] : une grande tache presque blanche. Les icônes du jeu, même
         * claires, n'en ont presque pas (quelques pour cent au plus, filmées).
         */
        fun reflet(image: ImageRgb, zone: Zone): Boolean {
            val icone = Reconnaisseur.zoneIcone(zone)
            var blancs = 0
            var total = 0
            for (y in maxOf(0, icone.y) until minOf(image.hauteur, icone.bas)) for (x in maxOf(0, icone.x) until minOf(image.largeur, icone.droite)) {
                val p = image.pixel(x, y)
                total++
                if (((p shr 16) and 0xFF) >= BLANC && ((p shr 8) and 0xFF) >= BLANC && (p and 0xFF) >= BLANC) blancs++
            }
            return total > 0 && blancs >= PART_REFLET * total
        }

        fun vide(image: ImageRgb, zone: Zone): Boolean {
            val petite = image.echantillon(Reconnaisseur.zoneIcone(zone), 12, 12)
            val lum = petite.pixels.map { ImageRgb.luminance(it).toDouble() }
            val moyenne = lum.average()
            return sqrt(lum.sumOf { (it - moyenne) * (it - moyenne) } / lum.size) < ECART_VIDE
        }

        private val ROMAINS = listOf("i", "ii", "iii", "iv", "v")

        /**
         * Parmi des objets à la même icône (les niveaux d'une arme : `anvil_i` à `anvil_iv`), ceux du [niveau] lu
         * dans la case ; tous si le niveau n'est pas lu ou ne correspond à aucun.
         */
        fun auNiveau(objetIds: List<String>, niveau: Int?): List<String> {
            if (niveau == null || objetIds.size < 2) return objetIds
            val suffixe = "_" + (ROMAINS.getOrNull(niveau - 1) ?: return objetIds)
            return objetIds.filter { it.endsWith(suffixe) }.ifEmpty { objetIds }
        }
    }
}
