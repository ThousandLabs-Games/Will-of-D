package com.thousandlabs.willofd.donnees

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Lien(val titre: String, val url: String, val date: Long = System.currentTimeMillis())

/** Favoris et historique, enregistrés dans la zone privée de l'application. */
object Carnet {
    private lateinit var dossier: File
    private const val MAX_HISTORIQUE = 500

    private val _favoris = MutableStateFlow<List<Lien>>(emptyList())
    val favoris: StateFlow<List<Lien>> = _favoris.asStateFlow()
    private val _historique = MutableStateFlow<List<Lien>>(emptyList())
    val historique: StateFlow<List<Lien>> = _historique.asStateFlow()

    fun init(context: Context) {
        dossier = File(context.filesDir, "carnet").apply { mkdirs() }
        _favoris.value = lire("favoris.json")
        _historique.value = lire("historique.json")
        if (_favoris.value.isEmpty() && !File(dossier, "favoris.json").exists()) {
            _favoris.value = listOf(
                Lien("Thousand Labs Games", "https://thousandlabs-games.github.io"),
                Lien("DuckDuckGo", "https://duckduckgo.com")
            )
            ecrire("favoris.json", _favoris.value)
        }
    }

    private fun lire(nom: String): List<Lien> = runCatching {
        val a = JSONArray(File(dossier, nom).readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Lien(o.optString("t"), o.optString("u"), o.optLong("d"))
        }
    }.getOrDefault(emptyList())

    private fun ecrire(nom: String, l: List<Lien>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.titre).put("u", it.url).put("d", it.date)) }
        val f = File(dossier, nom)
        val tmp = File(dossier, "$nom.tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun estFavori(url: String): Boolean = _favoris.value.any { it.url == url }

    fun basculerFavori(titre: String, url: String): Boolean {
        if (url.isBlank()) return false
        val ajoute = !estFavori(url)
        _favoris.value = if (ajoute) _favoris.value + Lien(titre.ifBlank { url }, url) else _favoris.value.filter { it.url != url }
        ecrire("favoris.json", _favoris.value)
        return ajoute
    }

    fun renommerFavori(url: String, titre: String) {
        _favoris.value = _favoris.value.map { if (it.url == url) it.copy(titre = titre) else it }
        ecrire("favoris.json", _favoris.value)
    }

    fun ajouterHistorique(titre: String, url: String) {
        if (!Reglages.historiqueActif || !url.startsWith("http")) return
        val l = _historique.value.filter { it.url != url }
        _historique.value = (listOf(Lien(titre.ifBlank { url }, url)) + l).take(MAX_HISTORIQUE)
        ecrire("historique.json", _historique.value)
    }

    fun retirerHistorique(url: String) {
        _historique.value = _historique.value.filter { it.url != url }
        ecrire("historique.json", _historique.value)
    }

    fun effacerHistorique() {
        _historique.value = emptyList()
        ecrire("historique.json", emptyList())
    }

    /** Suggestions pour la barre d'adresse : favoris puis historique. */
    fun suggestions(saisie: String): List<Lien> {
        val q = saisie.trim().lowercase()
        if (q.length < 2) return emptyList()
        val tous = _favoris.value + _historique.value
        return tous.filter { it.url.lowercase().contains(q) || it.titre.lowercase().contains(q) }
            .distinctBy { it.url }.take(6)
    }
}
