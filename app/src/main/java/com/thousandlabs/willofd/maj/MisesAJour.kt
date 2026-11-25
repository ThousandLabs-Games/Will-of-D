package com.thousandlabs.willofd.maj

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

data class VersionDistante(
    val version: String,
    val notes: String,
    val urlApk: String,
    val nomApk: String,
    val taille: Long
)

enum class Signature { CONFORME, DIFFERENTE, INDETERMINEE }

/** Recherche, téléchargement et installation des versions publiées sur le dépôt officiel. */
object MisesAJour {

    private fun ouvrir(url: String): java.net.HttpURLConnection =
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "WillOfD")
            setRequestProperty("Accept", "application/vnd.github+json, */*")
        }

    private const val URL_DERNIERE = "https://api.github.com/repos/ThousandLabs-Games/Will-of-D/releases/latest"
    const val URL_RELEASES = "https://github.com/ThousandLabs-Games/Will-of-D/releases"

    fun derniereVersion(): VersionDistante {
        val connexion = ouvrir(URL_DERNIERE)
        try {
            val code = connexion.responseCode
            if (code == 404) throw IOException("Aucune version publiée pour le moment.")
            if (code != 200) throw IOException("GitHub a répondu avec le code $code.")
            val texte = connexion.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            val json = JSONObject(texte)
            val assets = json.optJSONArray("assets")
            var url = ""
            var nom = ""
            var taille = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        nom = a.optString("name")
                        taille = a.optLong("size")
                        break
                    }
                }
            }
            if (url.isEmpty()) throw IOException("La dernière version ne contient pas d'APK.")
            return VersionDistante(
                version = json.optString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                urlApk = url,
                nomApk = nom,
                taille = taille
            )
        } finally {
            connexion.disconnect()
        }
    }

    /** Vrai si [distante] est strictement plus récente que [locale] (format 1.2.3). */
    fun plusRecente(distante: String, locale: String): Boolean {
        val a = distante.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = locale.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun dossier(context: Context): File = File(context.cacheDir, "maj").apply { mkdirs() }

    fun nettoyer(context: Context) {
        dossier(context).listFiles()?.forEach { it.delete() }
    }

    fun telecharger(context: Context, v: VersionDistante, progression: (Float) -> Unit): File {
        nettoyer(context)
        val cible = File(dossier(context), "Will-of-D-${v.version}.apk")
        val temporaire = File(cible.path + ".part")
        val connexion = ouvrir(v.urlApk)
        try {
            if (connexion.responseCode != 200) throw IOException("Téléchargement refusé (code ${connexion.responseCode}).")
            val total = if (v.taille > 0) v.taille else connexion.contentLengthLong
            connexion.inputStream.use { entree ->
                FileOutputStream(temporaire).use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var faits = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        faits += n
                        if (total > 0) progression(faits.toFloat() / total)
                    }
                    sortie.flush()
                    sortie.fd.sync()
                }
            }
            if (v.taille > 0 && temporaire.length() != v.taille) throw IOException("Téléchargement incomplet.")
            if (!temporaire.renameTo(cible)) throw IOException("Enregistrement de la mise à jour impossible.")
            return cible
        } catch (e: Exception) {
            temporaire.delete()
            throw e
        } finally {
            connexion.disconnect()
        }
    }

    /** Compare le certificat de signature de l'APK téléchargé avec celui de l'application installée. */
    @Suppress("DEPRECATION")
    fun verifierSignature(context: Context, apk: File): Signature {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return Signature.INDETERMINEE
        if (archive.packageName != context.packageName) return Signature.DIFFERENTE
        val installe = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val a = empreintes(archive)
        val b = empreintes(installe)
        return when {
            a.isEmpty() || b.isEmpty() -> Signature.INDETERMINEE
            a == b -> Signature.CONFORME
            else -> Signature.DIFFERENTE
        }
    }

    private fun empreintes(info: PackageInfo): Set<String> {
        val signataires = info.signingInfo?.apkContentsSigners ?: return emptySet()
        return signataires.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun peutInstaller(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun ouvrirAutorisationInstallation(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun installer(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
