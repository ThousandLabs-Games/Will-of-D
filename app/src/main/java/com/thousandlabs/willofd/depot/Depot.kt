package com.thousandlabs.willofd.depot

import android.content.Context
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class FichierDepot(val nom: String, val taille: Long, val mime: String)

/** Élément terminé, en attente de récupération par Rio Poneglyph. */
data class ElementDepot(
    val id: String,
    val type: String,
    val titre: String,
    val serie: String,
    val cleSerie: String,
    val cleElement: String,
    val numero: Double,
    val partie: Int,
    val sens: String,
    val ajoute: Long,
    val fichiers: List<FichierDepot>
) {
    val taille: Long get() = fichiers.sumOf { it.taille }
    val estPages: Boolean get() = type == "pages"
}

/**
 * Zone privée de Will of D, invisible pour la Galerie, Google Files et les autres applications :
 * dossier interne de l'application. Seule Rio Poneglyph (même signature) peut la lire, via le fournisseur.
 */
object Depot {
    private const val META = "element.json"
    const val MANIFESTE = "poneglyph.json"
    private lateinit var racine: File
    private lateinit var encours: File
    @Volatile private var pret = false

    private val _elements = MutableStateFlow<List<ElementDepot>>(emptyList())
    val elements: StateFlow<List<ElementDepot>> = _elements.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (pret) return
        val base = context.applicationContext.filesDir
        racine = File(base, "depot").apply { mkdirs() }
        encours = File(base, "encours").apply { mkdirs() }
        pret = true
        rafraichir()
    }

    fun dossierEnCours(id: String): File = File(encours, id).apply { mkdirs() }

    fun effacerEnCours(id: String) { File(encours, id).deleteRecursively() }

    /** Relit la zone de dépôt : la liste reflète toujours l'état réel du disque. */
    @Synchronized
    fun rafraichir() {
        val liste = racine.listFiles()?.filter { it.isDirectory }?.mapNotNull { lire(it) }.orEmpty()
        _elements.value = liste.sortedByDescending { it.ajoute }
    }

    private fun lire(d: File): ElementDepot? = runCatching {
        val o = JSONObject(File(d, META).readText())
        val fichiers = o.optJSONArray("fichiers") ?: JSONArray()
        ElementDepot(
            id = d.name,
            type = o.optString("type", "video"),
            titre = o.optString("titre"),
            serie = o.optString("serie"),
            cleSerie = o.optString("cle_serie"),
            cleElement = o.optString("cle_element"),
            numero = o.optDouble("numero", -1.0).takeUnless { it.isNaN() } ?: -1.0,
            partie = o.optInt("partie", 0),
            sens = o.optString("sens"),
            ajoute = o.optLong("ajoute"),
            fichiers = List(fichiers.length()) { i ->
                val f = fichiers.getJSONObject(i)
                val nom = f.getString("nom")
                // Taille exacte relue sur le disque : Rio Poneglyph la vérifie octet par octet.
                FichierDepot(nom, File(d, nom).length(), f.optString("mime"))
            }.filter { File(d, it.nom).isFile }
        )
    }.getOrNull()

    fun mimeDe(nom: String): String {
        val ext = nom.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "ts" -> "video/mp2t"
            "mkv" -> "video/x-matroska"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    /**
     * Publie un élément terminé : description écrite, puis déplacement atomique du dossier de travail
     * vers la zone de dépôt. Un élément à moitié écrit n'est donc jamais visible par Rio Poneglyph.
     */
    @Synchronized
    fun publier(
        id: String, type: String, titre: String, serie: String, cleSerie: String, cleElement: String,
        numero: Double, partie: Int, sens: String, fichiers: List<String>
    ) {
        val travail = File(encours, id)
        if (!travail.isDirectory) throw IOException("dossier de travail introuvable")
        val liste = JSONArray()
        fichiers.forEach { nom ->
            val f = File(travail, nom)
            if (!f.isFile || f.length() <= 0L) throw IOException("fichier manquant : $nom")
            liste.put(JSONObject().put("nom", nom).put("taille", f.length()).put("mime", mimeDe(nom)))
        }
        val meta = JSONObject()
            .put("format", 1)
            .put("type", type)
            .put("titre", titre)
            .put("serie", serie)
            .put("cle_serie", cleSerie)
            .put("cle_element", cleElement)
            .put("numero", numero)
            .put("partie", partie)
            .put("sens", sens)
            .put("ajoute", System.currentTimeMillis())
            .put("fichiers", liste)
        val tmp = File(travail, "$META.tmp")
        tmp.writeText(meta.toString())
        if (!tmp.renameTo(File(travail, META))) throw IOException("description impossible à écrire")
        // Ménage du dossier de travail : seuls les fichiers publiés et les descriptions restent.
        val gardes = fichiers.toHashSet() + setOf(META, MANIFESTE)
        travail.listFiles()?.forEach { if (it.name !in gardes) it.deleteRecursively() }
        val cible = File(racine, id)
        if (cible.exists()) cible.deleteRecursively()
        if (!travail.renameTo(cible)) throw IOException("publication impossible")
        rafraichir()
    }

    fun element(id: String): ElementDepot? = _elements.value.firstOrNull { it.id == id } ?: run {
        val d = File(racine, nettoyerId(id) ?: return null)
        if (d.isDirectory) lire(d) else null
    }

    private fun nettoyerId(id: String): String? = id.takeIf { it.matches(Regex("[A-Za-z0-9-]{6,64}")) }

    /** Fichier d'un élément, protégé contre toute sortie de la zone de dépôt. */
    fun fichier(id: String, nom: String): File? {
        val sid = nettoyerId(id) ?: return null
        if (nom.isBlank() || nom.contains('/') || nom.contains('\\') || nom.startsWith(".")) return null
        if (nom == META) return null
        val d = File(racine, sid)
        val f = File(d, nom)
        return f.takeIf { it.isFile && it.canonicalPath.startsWith(d.canonicalPath + File.separator) }
    }

    /** Retire un élément (récupéré par Rio Poneglyph, ou retiré à la main) : il disparaît de la liste, nom compris. */
    @Synchronized
    fun supprimer(id: String): Boolean {
        val sid = nettoyerId(id) ?: return false
        val d = File(racine, sid)
        if (!d.exists()) return false
        val ok = d.deleteRecursively()
        rafraichir()
        return ok
    }

    fun espaceLibre(): Long = racine.usableSpace
}
