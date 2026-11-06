package io.github.slaynaw.carnetduraider.inventaire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.slaynaw.carnetduraider.R
import io.github.slaynaw.carnetduraider.core.Objet
import io.github.slaynaw.carnetduraider.core.inventaire.AnalyseInventaire
import io.github.slaynaw.carnetduraider.core.inventaire.ImageRgb
import io.github.slaynaw.carnetduraider.core.inventaire.LectureNombre
import io.github.slaynaw.carnetduraider.core.inventaire.Reconnaisseur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.SoftReference

/**
 * La reconnaissance de l'inventaire filmé, préparée à la première ouverture de la caméra (quelques secondes) :
 * les icônes officielles de l'appli (`assets/objets`, les mêmes que partout ailleurs) servent de références, avec
 * la rareté de chaque objet (la couleur du halo de sa case) et combien en tient une case ; les chiffres 0 à 9,
 * dessinés en clair sur fond sombre avec Barlow (proche de la police des nombres du jeu), servent de modèles pour
 * lire les nombres. Elle reste prête tant que le téléphone n'a pas besoin de la place.
 */
object PreparationReconnaissance {
    private val verrou = Mutex()

    @Volatile
    private var prete: SoftReference<AnalyseInventaire>? = null

    suspend fun obtenir(contexte: Context, objets: List<Objet>): AnalyseInventaire {
        prete?.get()?.let { return it }
        return verrou.withLock {
            prete?.get() ?: withContext(Dispatchers.Default) { construire(contexte.applicationContext, objets) }.also { prete = SoftReference(it) }
        }
    }

    private fun construire(contexte: Context, objets: List<Objet>): AnalyseInventaire {
        val icones = objets.mapNotNull { o -> icone(contexte, o.id)?.let { o.id to it } }.toMap()
        return AnalyseInventaire(
            Reconnaisseur(icones, objets.associate { it.id to it.rarete }),
            LectureNombre(modelesChiffres(contexte)),
            objets.associate { it.id to it.pile },
        )
    }

    private fun icone(contexte: Context, id: String): ImageRgb? {
        val bitmap = try {
            contexte.assets.open("objets/$id.webp").use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            null // pas d'icône pour cet objet : il ne peut pas être reconnu
        } ?: return null
        return versImage(bitmap).also { bitmap.recycle() }
    }

    /** Les chiffres 0 à 9, en blanc sur fond sombre, avec la police Barlow de l'appli (proche de celle du jeu). */
    private fun modelesChiffres(contexte: Context): Map<Int, ImageRgb> {
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = ResourcesCompat.getFont(contexte, R.font.barlow_semibold) ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        return (0..9).associateWith { chiffre ->
            val bitmap = Bitmap.createBitmap(56, 80, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.rgb(10, 10, 12))
                drawText(chiffre.toString(), 28f, 64f, pinceau)
            }
            versImage(bitmap).also { bitmap.recycle() }
        }
    }

    /** Les pixels d'un bitmap, en image pour la reconnaissance (ARGB, non prémultipliés). */
    fun versImage(bitmap: Bitmap): ImageRgb {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ImageRgb(bitmap.width, bitmap.height, pixels)
    }
}
