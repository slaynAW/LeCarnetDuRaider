package io.github.slaynaw.carnetduraider.inventaire

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.slaynaw.carnetduraider.core.inventaire.AnalyseInventaire
import io.github.slaynaw.carnetduraider.core.inventaire.CaseVue
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min

/** Une image analysée : ses cases, et leur rectangle dans l'aperçu de la caméra à l'écran (en pixels). */
class ImageAnalysee(val cases: List<CaseVue>, val rectangles: List<RectF>)

/**
 * L'analyse des images de la caméra, en arrière-plan : quelques images par seconde au plus, chacune redressée
 * (le capteur est souvent tourné par rapport à l'écran) et réduite, puis passée à [analyse] ; le résultat est
 * rendu sur [principal] (le fil de l'interface). Les zones des cases sont ramenées dans le repère de l'aperçu à
 * l'écran, pour y poser les étiquettes. Aucune image n'est gardée ni envoyée.
 */
class AnalyseurCamera(
    private val analyse: AnalyseInventaire,
    private val principal: Executor,
    private val surImage: (ImageAnalysee) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Du capteur à l'aperçu à l'écran : donnée par la caméra quand l'aperçu est prêt (et à chaque changement). */
    @Volatile
    private var capteurVersVue: Matrix? = null
    private var derniere = 0L

    override fun getTargetCoordinateSystem(): Int = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

    override fun updateTransform(matrix: Matrix?) {
        capteurVersVue = matrix?.let { Matrix(it) }
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            val maintenant = SystemClock.elapsedRealtime()
            val versVue = capteurVersVue
            if (versVue == null || maintenant - derniere < INTERVALLE_MS) return
            derniere = maintenant
            val resultat = try {
                analyser(image, versVue)
            } catch (e: RuntimeException) {
                null // image illisible (rare) : on attend la suivante
            } ?: return
            principal.execute { surImage(resultat) }
        }
    }

    private fun analyser(image: ImageProxy, versVue: Matrix): ImageAnalysee {
        val tampon = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        // Redressée (tournée comme l'écran), puis réduite à LARGEUR_MAX pixels au plus sur son grand côté.
        val grandCote = max(tampon.width, tampon.height)
        val echelle = min(1f, LARGEUR_MAX.toFloat() / grandCote)
        val tamponVersAnalyse = rotation(rotation, tampon.width, tampon.height).apply { postScale(echelle, echelle) }
        val droite = Bitmap.createBitmap(tampon, 0, 0, tampon.width, tampon.height, tamponVersAnalyse, true)
        val cases = analyse.analyser(PreparationReconnaissance.versImage(droite))
        if (droite !== tampon) droite.recycle()
        tampon.recycle()

        // Image analysée → tampon de la caméra → capteur → aperçu à l'écran.
        val versEcran = Matrix()
        tamponVersAnalyse.invert(versEcran)
        val tamponVersCapteur = Matrix()
        image.imageInfo.sensorToBufferTransformMatrix.invert(tamponVersCapteur)
        versEcran.postConcat(tamponVersCapteur)
        versEcran.postConcat(versVue)
        val rectangles = cases.map { c ->
            RectF(c.zone.x.toFloat(), c.zone.y.toFloat(), c.zone.droite.toFloat(), c.zone.bas.toFloat()).also { versEcran.mapRect(it) }
        }
        return ImageAnalysee(cases, rectangles)
    }

    /** La rotation qui redresse un tampon de [l] × [h] pixels de [degres] (sens des aiguilles), sans sortir du cadre. */
    private fun rotation(degres: Int, l: Int, h: Int): Matrix = Matrix().apply {
        postRotate(degres.toFloat())
        when (degres) {
            90 -> postTranslate(h.toFloat(), 0f)
            180 -> postTranslate(l.toFloat(), h.toFloat())
            270 -> postTranslate(0f, l.toFloat())
        }
    }

    private companion object {
        /** Au plus une image analysée tous les quarts de seconde : assez pour suivre, sans chauffer le téléphone. */
        const val INTERVALLE_MS = 250L

        /** Grand côté de l'image analysée : les nombres des cases restent lisibles. */
        const val LARGEUR_MAX = 1280
    }
}
