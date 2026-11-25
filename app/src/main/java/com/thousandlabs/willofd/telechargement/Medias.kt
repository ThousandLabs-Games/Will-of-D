package com.thousandlabs.willofd.telechargement

enum class GenreMedia { DIRECT, HLS, DASH, FLUX }

/** Média repéré sur une page : par le réseau, par le lecteur de la page, ou par un lien de téléchargement. */
data class MediaDetecte(
    val url: String,
    val genre: GenreMedia,
    /** Page (ou cadre du lecteur) d'où vient la demande : envoyée comme Referer. */
    val referer: String,
    val origine: String,
    val titrePage: String,
    val nomFichier: String = "",
    val taille: Long = -1L,
    val vu: Long = System.currentTimeMillis()
) {
    val hote: String get() = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")

    val libelle: String
        get() = when (genre) {
            GenreMedia.HLS -> "Flux vidéo HLS"
            GenreMedia.DASH -> "Flux vidéo DASH (non pris en charge)"
            GenreMedia.FLUX -> "Flux du lecteur"
            GenreMedia.DIRECT -> {
                val ext = url.substringBefore('?').substringAfterLast('.', "").uppercase().take(4)
                if (ext.isNotEmpty()) "Vidéo $ext" else "Vidéo"
            }
        }
}

object Detection {
    private val EXT_VIDEO = listOf(".mp4", ".webm", ".mkv", ".m4v", ".mov", ".flv", ".avi", ".3gp")

    /** Hôtes de flux impossibles à récupérer proprement (découpage propriétaire ou protection). */
    private val EXCLUS = listOf("googlevideo.com", "youtube.com", "ytimg.com", "nflxvideo.net", "fbcdn.net", "cdninstagram.com")

    fun classer(url: String): GenreMedia? {
        if (!url.startsWith("http")) return null
        val chemin = url.substringBefore('#').substringBefore('?').lowercase()
        val hote = runCatching { java.net.URI(url).host?.lowercase() ?: "" }.getOrDefault("")
        if (EXCLUS.any { hote == it || hote.endsWith(".$it") }) return null
        return when {
            chemin.endsWith(".m3u8") || url.contains(".m3u8?", ignoreCase = true) -> GenreMedia.HLS
            chemin.endsWith(".mpd") -> GenreMedia.DASH
            EXT_VIDEO.any { chemin.endsWith(it) } -> GenreMedia.DIRECT
            else -> null
        }
    }

    fun estVideoMime(mime: String?): Boolean =
        mime != null && (mime.startsWith("video/") || mime == "application/x-mpegurl" || mime == "application/vnd.apple.mpegurl")
}
