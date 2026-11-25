package com.thousandlabs.willofd.telechargement

import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Refus clair d'un site (code HTTP, page web à la place du média…). */
class RefusSite(message: String, val code: Int = 0) : IOException(message)

/** Connexions de téléchargement : mêmes cookies, même agent et même page d'origine que le navigateur. */
object Reseau {

    fun ouvrir(
        url: String,
        referer: String?,
        origine: String?,
        agent: String,
        plage: String? = null,
        delai: Int = 20_000
    ): HttpURLConnection {
        var adresse = url
        repeat(6) {
            val c = (URL(adresse).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = delai
                readTimeout = 45_000
                setRequestProperty("User-Agent", agent)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7")
                setRequestProperty("Accept-Encoding", "identity")
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                if (!origine.isNullOrBlank()) setRequestProperty("Origin", origine)
                runCatching { CookieManager.getInstance().getCookie(adresse) }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                if (plage != null) setRequestProperty("Range", plage)
            }
            val code = c.responseCode
            if (code in 300..399) {
                val suite = c.getHeaderField("Location")
                c.disconnect()
                if (suite.isNullOrBlank()) throw RefusSite("Redirection sans destination", code)
                adresse = URL(URL(adresse), suite).toString()
            } else return c
        }
        throw RefusSite("Trop de redirections")
    }

    fun lireTexte(url: String, referer: String?, origine: String?, agent: String): String {
        val c = ouvrir(url, referer, origine, agent)
        try {
            if (c.responseCode !in 200..299) throw RefusSite("Le site a refusé l'accès (code ${c.responseCode}).", c.responseCode)
            return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    fun lireOctets(url: String, referer: String?, origine: String?, agent: String, plage: String? = null): ByteArray {
        val c = ouvrir(url, referer, origine, agent, plage)
        try {
            if (c.responseCode !in 200..299) throw RefusSite("Le site a refusé l'accès (code ${c.responseCode}).", c.responseCode)
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
