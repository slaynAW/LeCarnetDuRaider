package io.github.slaynaw.carnetduraider.core.inventaire

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Une image de la caméra → les cases de l'inventaire qu'elle montre. La grille est cherchée sur une copie
 * réduite (plus rapide), les objets et leurs nombres sur l'image elle-même (plus nette). Tout se fait sur le
 * téléphone : aucune image ne quitte l'appli, et aucune n'est gardée.
 */
class AnalyseInventaire(private val reconnaisseur: Reconnaisseur, private val nombres: LectureNombre? = null) {

    /** Les cases non vides de [image], de gauche à droite et de haut en bas. */
    fun analyser(image: ImageRgb): List<CaseVue> {
        val facteur = max(1.0, image.largeur.toDouble() / LARGEUR_GRILLE)
        val petite = if (facteur > 1.0) {
            image.echantillon(image.zone, LARGEUR_GRILLE, max(1, (image.hauteur / facteur).roundToInt()))
        } else {
            image
        }
        return DetectionGrille.detecter(petite).mapNotNull { case ->
            val zone = if (facteur > 1.0) case.zone.agrandie(facteur) else case.zone
            if (vide(image, zone)) return@mapNotNull null
            val r = reconnaisseur.reconnaitre(image, zone)
            val contenu = if (r.sur) ContenuCase.Objet(r.objetIds, nombres?.lire(image, zone)) else ContenuCase.PasSur
            CaseVue(zone, contenu)
        }
    }

    companion object {
        /** Largeur de la copie où l'on cherche la grille. */
        const val LARGEUR_GRILLE = 480

        /** Une case presque unie (écart de luminance faible) est vide. */
        private const val ECART_VIDE = 7.0

        fun vide(image: ImageRgb, zone: Zone): Boolean {
            val petite = image.echantillon(zone.interieur(0.12), 12, 12)
            val lum = petite.pixels.map { ImageRgb.luminance(it).toDouble() }
            val moyenne = lum.average()
            return sqrt(lum.sumOf { (it - moyenne) * (it - moyenne) } / lum.size) < ECART_VIDE
        }
    }
}
