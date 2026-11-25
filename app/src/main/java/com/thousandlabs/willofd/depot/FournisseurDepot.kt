package com.thousandlabs.willofd.depot

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileNotFoundException

/**
 * Échange local à sens unique avec Rio Poneglyph.
 *
 * - Protégé par une permission de niveau signature (déclarée dans le manifeste) : seules les applications
 *   signées avec la même clé Thousand Labs peuvent l'ouvrir.
 * - Vérification supplémentaire : seule Rio Poneglyph est acceptée.
 * - Lecture seule : aucune écriture n'est possible ; la seule demande acceptée est l'effacement
 *   d'un élément que Rio Poneglyph vient de chiffrer et de vérifier.
 */
class FournisseurDepot : ContentProvider() {

    companion object {
        const val AUTORITE = "com.thousandlabs.willofd.depot"
        private const val RIO = "com.thousandlabs.rioponeglyph"
        private const val ELEMENTS = 1
        private const val ELEMENT = 2
        private const val FICHIERS = 3
        private const val FICHIER = 4
        private val regles = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTORITE, "elements", ELEMENTS)
            addURI(AUTORITE, "elements/*", ELEMENT)
            addURI(AUTORITE, "elements/*/fichiers", FICHIERS)
            addURI(AUTORITE, "elements/*/fichiers/*", FICHIER)
        }
    }

    override fun onCreate(): Boolean {
        context?.let { Depot.init(it) }
        return true
    }

    /** Refuse toute application autre que Rio Poneglyph (la permission de signature a déjà été vérifiée par Android). */
    private fun controler() {
        val uid = android.os.Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val paquets = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        if (RIO !in paquets) throw SecurityException("Accès réservé à Rio Poneglyph")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        controler()
        context?.let { Depot.init(it) }
        Depot.rafraichir()
        return when (regles.match(uri)) {
            ELEMENTS -> MatrixCursor(
                arrayOf("id", "type", "titre", "serie", "cle_serie", "cle_element", "numero", "partie", "sens", "taille", "fichiers", "ajoute")
            ).apply {
                Depot.elements.value.forEach { e ->
                    addRow(arrayOf<Any?>(e.id, e.type, e.titre, e.serie, e.cleSerie, e.cleElement, e.numero, e.partie, e.sens, e.taille, e.fichiers.size, e.ajoute))
                }
            }
            FICHIERS -> {
                val id = uri.pathSegments[1]
                val e = Depot.element(id) ?: return null
                MatrixCursor(arrayOf("nom", "taille", "mime")).apply {
                    e.fichiers.forEach { f -> addRow(arrayOf<Any?>(f.nom, f.taille, f.mime)) }
                }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = when (regles.match(uri)) {
        FICHIER -> Depot.mimeDe(uri.lastPathSegment ?: "")
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        controler()
        if (mode != "r") throw SecurityException("Lecture seule")
        if (regles.match(uri) != FICHIER) throw FileNotFoundException("Adresse inconnue")
        val f = Depot.fichier(uri.pathSegments[1], uri.pathSegments[3]) ?: throw FileNotFoundException("Fichier introuvable")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Seule opération acceptée de la part de Rio Poneglyph : retirer un élément qu'elle a récupéré. */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        controler()
        if (regles.match(uri) != ELEMENT) return 0
        return if (Depot.supprimer(uri.pathSegments[1])) 1 else 0
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Sens unique : aucun dépôt depuis une autre application")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("Sens unique : aucune modification depuis une autre application")
}
