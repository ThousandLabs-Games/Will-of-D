package com.thousandlabs.willofd.telechargement

import org.json.JSONArray
import org.json.JSONObject

enum class EtatTache { ATTENTE, EN_COURS, PAUSE, ECHEC }

/** Téléchargement en file d'attente ou en cours. Une fois terminé, il devient un élément du dépôt. */
data class Tache(
    val id: String,
    val pages: Boolean,
    val titre: String,
    val serie: String,
    val numero: Double,
    val partie: Int,
    val cleSerie: String,
    val cleElement: String,
    val sens: String,
    val agent: String,
    // Vidéo
    val url: String = "",
    val genreMedia: GenreMedia = GenreMedia.DIRECT,
    val referer: String = "",
    val origine: String = "",
    // Pages
    val images: List<String> = emptyList(),
    val pageUrl: String = "",
    val ongletId: Long = -1L,
    // Suivi
    val etat: EtatTache = EtatTache.ATTENTE,
    val progression: Float = 0f,
    val octets: Long = 0L,
    val detail: String = "En attente",
    val cree: Long = System.currentTimeMillis()
) {
    fun versJson(): JSONObject = JSONObject().apply {
        put("id", id); put("pages", pages); put("titre", titre); put("serie", serie); put("numero", numero)
        put("partie", partie); put("cle_serie", cleSerie); put("cle_element", cleElement); put("sens", sens)
        put("agent", agent); put("url", url); put("genre", genreMedia.name); put("referer", referer); put("origine", origine)
        put("images", JSONArray(images)); put("page", pageUrl); put("onglet", ongletId)
        put("etat", etat.name); put("progression", progression.toDouble()); put("octets", octets); put("detail", detail); put("cree", cree)
    }

    companion object {
        fun depuisJson(o: JSONObject): Tache {
            val im = o.optJSONArray("images") ?: JSONArray()
            return Tache(
                id = o.getString("id"),
                pages = o.optBoolean("pages"),
                titre = o.optString("titre"),
                serie = o.optString("serie"),
                numero = o.optDouble("numero", -1.0),
                partie = o.optInt("partie"),
                cleSerie = o.optString("cle_serie"),
                cleElement = o.optString("cle_element"),
                sens = o.optString("sens"),
                agent = o.optString("agent"),
                url = o.optString("url"),
                genreMedia = runCatching { GenreMedia.valueOf(o.optString("genre")) }.getOrDefault(GenreMedia.DIRECT),
                referer = o.optString("referer"),
                origine = o.optString("origine"),
                images = List(im.length()) { im.getString(it) },
                pageUrl = o.optString("page"),
                ongletId = o.optLong("onglet", -1L),
                etat = runCatching { EtatTache.valueOf(o.optString("etat")) }.getOrDefault(EtatTache.ATTENTE),
                progression = o.optDouble("progression", 0.0).toFloat(),
                octets = o.optLong("octets"),
                detail = o.optString("detail"),
                cree = o.optLong("cree", System.currentTimeMillis())
            )
        }
    }
}
