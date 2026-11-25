package com.thousandlabs.willofd.telechargement

import android.net.Uri
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Proposition de nom, série et numéro, modifiable avant le lancement du téléchargement. */
data class Proposition(val titre: String, val serie: String, val numero: Double)

/** Lecture du titre et de l'adresse d'une page pour retrouver série et numéro (continuité des suites). */
object Analyse {

    private val MOTS_CHAPITRE = "chapitres?|chapters?|chap\\.?|ch\\.?|scans?|#"
    private val MOTS_EPISODE = "[ée]pisodes?|episodes?|ep\\.?|[ée]p\\.?|e(?=\\d)|#"
    private val SEPARATEURS = Regex("\\s+[|–—\\-:]\\s+")
    private val BRUIT = Regex(
        "(?i)\\b(lecture en ligne|lire en ligne|read online|scan vf|scans vf|vostfr|vf|streaming|en streaming|gratuit|free|hd|full|" +
            "manga|manhwa|webtoon|anime|animé|regarder|watch|online|français|francais|english sub|eng sub|sub|dub)\\b"
    )

    private fun numeroDans(texte: String, mots: String): Pair<Double, Int>? {
        val r = Regex("(?i)(?<![\\p{L}])(?:$mots)\\s*[-_.]?\\s*(\\d{1,5}(?:[.,]\\d{1,2})?)")
        val m = r.find(texte) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return n to m.range.first
    }

    private fun nettoyerTitre(titre: String, hote: String): String {
        val site = hote.removePrefix("www.").substringBefore('.').lowercase()
        val parties = titre.split(SEPARATEURS).map { it.trim() }.filter { it.isNotEmpty() }
        val gardees = parties.filterNot { p ->
            val q = normaliser(p)
            q.isEmpty() || q.replace("-", "").contains(site.replace("-", "")) && p.length < 40
        }
        return (gardees.ifEmpty { parties }).joinToString(" - ")
    }

    private fun serieDepuisUrl(url: String): String {
        val segments = runCatching { Uri.parse(url).pathSegments }.getOrNull().orEmpty()
        val slug = segments.filter { s ->
            s.length in 3..80 && s.contains('-') && s.any { it.isLetter() } &&
                !Regex("(?i)^(chapitre|chapter|chap|ch|scan|episode|ep|vf|vostfr|page)[-_]?\\d").containsMatchIn(s)
        }.maxByOrNull { it.count { c -> c.isLetter() } } ?: return ""
        return slug.replace('-', ' ').replace('_', ' ').split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.FRANCE) } }
    }

    private fun proposer(titrePage: String, url: String, mots: String, libelle: String): Proposition {
        val hote = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
        val propre = nettoyerTitre(titrePage, hote)
        val dansTitre = numeroDans(propre, mots)
        val numero = dansTitre?.first ?: numeroDans(Uri.decode(url), mots.replace("|#", ""))?.first ?: -1.0
        var serie = if (dansTitre != null) propre.substring(0, dansTitre.second) else ""
        serie = BRUIT.replace(serie, " ").replace(Regex("[\\s\\-|:–—]+$"), "").replace(Regex("\\s{2,}"), " ").trim()
        if (serie.isBlank()) serie = serieDepuisUrl(url)
        if (serie.isBlank() && dansTitre == null) serie = BRUIT.replace(propre, " ").replace(Regex("\\s{2,}"), " ").trim()
        val titre = when {
            numero >= 0 -> "$libelle ${formater(numero)}"
            propre.isNotBlank() -> propre
            else -> libelle
        }
        return Proposition(titre.take(120), serie.take(80), numero)
    }

    fun pourPages(titrePage: String, url: String) = proposer(titrePage, url, MOTS_CHAPITRE, "Chapitre")

    fun pourVideo(titrePage: String, url: String) = proposer(titrePage, url, MOTS_EPISODE, "Épisode")

    fun formater(n: Double): String = if (n == Math.floor(n)) n.toLong().toString() else n.toString().replace('.', ',')

    fun normaliser(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun cleSerie(serie: String): String = if (serie.isBlank()) "" else "serie:" + normaliser(serie)

    /** Clé stable d'un chapitre ou d'un épisode : deux téléchargements de même clé sont regroupés dans Rio Poneglyph. */
    fun cleElement(pages: Boolean, serie: String, numero: Double, url: String): String {
        if (numero >= 0) {
            val prefixe = if (pages) "chapitre" else "episode"
            return "$prefixe:${normaliser(serie).ifEmpty { "sans-serie" }}:${formater(numero).replace(',', '.')}"
        }
        val brut = url.substringBefore('#').substringBefore('?')
        val h = MessageDigest.getInstance("SHA-1").digest(brut.toByteArray()).joinToString("") { "%02x".format(it) }
        return "page:" + h.take(16)
    }
}
