package io.github.slaynaw.carnetduraider.core.inventaire

import java.util.stream.Collectors
import kotlin.math.sqrt

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
    fun analyser(image: ImageRgb): List<CaseVue> {
        val cases = DetectionGrille.detecter(image, tailleCases)
        tailleCases = cases.map { it.zone.largeur }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        return cases.parallelStream().map { case -> analyserCase(image, case.zone) }.collect(Collectors.toList()).filterNotNull()
    }

    /** Ce que contient la case [zone] ; `null` si elle est vide, ou si ce n'est pas une case d'objet entière. */
    private fun analyserCase(image: ImageRgb, zone: Zone): CaseVue? {
        if (vide(image, zone)) return null
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
