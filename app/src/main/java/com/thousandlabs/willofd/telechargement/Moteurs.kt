package com.thousandlabs.willofd.telechargement

import com.thousandlabs.willofd.donnees.Reglages
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

/** Avancement : fraction (ou null si inconnue), octets reçus, texte. */
typealias Avancement = (Float?, Long, String) -> Unit

private fun resoudre(base: String, chemin: String): String = URL(URL(base), chemin.trim()).toString()

// ====================================================================== Vidéo directe

object MoteurDirect {

    /** Téléchargement classique avec reprise (en-tête Range) ; renvoie le nom du fichier final. */
    suspend fun telecharger(t: Tache, dossier: File, avance: Avancement): String {
        val partiel = File(dossier, "video.part")
        var deja = if (partiel.exists()) partiel.length() else 0L
        var c = Reseau.ouvrir(t.url, t.referer, t.origine, t.agent, if (deja > 0) "bytes=$deja-" else null)
        try {
            var code = c.responseCode
            if (code == 416 && deja > 0) {
                // Le fichier était déjà complet.
                return finaliser(partiel, dossier, c.contentType, t.url)
            }
            if (code == 403 || code == 401) {
                c.disconnect()
                // Deuxième essai sans Origin, avec la page comme Referer (certains lecteurs l'exigent).
                c = Reseau.ouvrir(t.url, t.pageUrl.ifBlank { t.referer }, null, t.agent, if (deja > 0) "bytes=$deja-" else null)
                code = c.responseCode
            }
            if (code !in 200..299) throw RefusSite("Le site a refusé le téléchargement (code $code).", code)
            val type = c.contentType ?: ""
            if (type.startsWith("text/html")) throw RefusSite("Le site a renvoyé une page web au lieu de la vidéo.")
            if (code == 200 && deja > 0) { deja = 0; partiel.delete() }
            val longueur = c.contentLengthLong
            val total = if (longueur > 0) deja + longueur else -1L
            verifierEspace(dossier, if (longueur > 0) longueur else 0L)
            c.inputStream.use { entree ->
                FileOutputStream(partiel, deja > 0).use { sortie ->
                    val tampon = ByteArray(128 * 1024)
                    var recus = deja
                    var dernier = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        recus += n
                        val maintenant = System.currentTimeMillis()
                        if (maintenant - dernier > 400) {
                            dernier = maintenant
                            avance(if (total > 0) recus.toFloat() / total else null, recus, "Téléchargement de la vidéo")
                        }
                    }
                    sortie.flush()
                    sortie.fd.sync()
                    if (total > 0 && recus < total) throw IOException("connexion interrompue, reprise possible")
                }
            }
            return finaliser(partiel, dossier, type, t.url)
        } finally {
            c.disconnect()
        }
    }

    private fun finaliser(partiel: File, dossier: File, type: String?, url: String): String {
        if (partiel.length() < 32 * 1024) throw RefusSite("Fichier trop petit : ce n'est probablement pas la vidéo.")
        val ext = when {
            type?.contains("webm") == true -> "webm"
            type?.contains("matroska") == true -> "mkv"
            type?.contains("mp2t") == true -> "ts"
            else -> url.substringBefore('?').substringAfterLast('.', "mp4").lowercase().takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp4"
        }
        val nom = "video.$ext"
        if (!partiel.renameTo(File(dossier, nom))) throw IOException("enregistrement impossible")
        return nom
    }
}

// ====================================================================== Flux HLS

object MoteurHls {

    private class Variante(val url: String, val bande: Long, val hauteur: Int, val audio: String?)
    private class Cle(val methode: String, val uri: String?, val iv: ByteArray?)
    private class Segment(val url: String, val cle: Cle?, val sequence: Long, val plage: String?)

    private fun attributs(ligne: String): Map<String, String> {
        val r = HashMap<String, String>()
        val m = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)").findAll(ligne.substringAfter(':'))
        m.forEach { r[it.groupValues[1]] = it.groupValues[2].trim('"') }
        return r
    }

    private fun hexVersOctets(h: String): ByteArray {
        val s = h.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(16) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun choisirVariante(texte: String, base: String): String {
        val lignes = texte.lines().map { it.trim() }
        val audiosExternes = HashSet<String>()
        lignes.filter { it.startsWith("#EXT-X-MEDIA:") }.forEach { l ->
            val a = attributs(l)
            if (a["TYPE"] == "AUDIO" && !a["URI"].isNullOrBlank()) a["GROUP-ID"]?.let { audiosExternes.add(it) }
        }
        val variantes = ArrayList<Variante>()
        for (i in lignes.indices) {
            if (!lignes[i].startsWith("#EXT-X-STREAM-INF")) continue
            val a = attributs(lignes[i])
            val uri = lignes.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            val hauteur = a["RESOLUTION"]?.substringAfter('x')?.toIntOrNull() ?: 0
            variantes.add(Variante(resoudre(base, uri), a["BANDWIDTH"]?.toLongOrNull() ?: 0L, hauteur, a["AUDIO"]))
        }
        if (variantes.isEmpty()) throw RefusSite("Flux vidéo vide.")
        val muxees = variantes.filter { it.audio == null || it.audio !in audiosExternes }
        if (muxees.isEmpty()) throw RefusSite("Ce flux sépare l'image et le son : il n'est pas encore pris en charge. Essayez un autre lecteur ou un autre site.")
        val plafond = when (Reglages.qualite) { 1 -> 720; 2 -> 480; else -> Int.MAX_VALUE }
        val possibles = muxees.filter { it.hauteur == 0 || it.hauteur <= plafond }.ifEmpty { listOf(muxees.minBy { it.bande }) }
        return possibles.maxBy { it.bande }.url
    }

    private fun lireSegments(texte: String, base: String): Pair<List<Segment>, String?> {
        val lignes = texte.lines().map { it.trim() }
        if (lignes.any { it.startsWith("#EXT-X-KEY") && (it.contains("SAMPLE-AES") || it.contains("com.widevine") || it.contains("com.apple.streamingkeydelivery")) }) {
            throw RefusSite("Cette vidéo est protégée (DRM) : son téléchargement est impossible.")
        }
        if (lignes.none { it.startsWith("#EXT-X-ENDLIST") } && lignes.none { it.startsWith("#EXT-X-PLAYLIST-TYPE:VOD") }) {
            throw RefusSite("Diffusion en direct : seul un épisode ou un film complet peut être téléchargé.")
        }
        var sequence = lignes.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }?.substringAfter(':')?.toLongOrNull() ?: 0L
        var cle: Cle? = null
        var init: String? = null
        var plage: String? = null
        var finPlage = 0L
        val segments = ArrayList<Segment>()
        var attendSegment = false
        for (l in lignes) {
            when {
                l.startsWith("#EXT-X-KEY:") -> {
                    val a = attributs(l)
                    val m = a["METHOD"] ?: "NONE"
                    cle = if (m == "NONE") null else Cle(m, a["URI"]?.let { resoudre(base, it) }, a["IV"]?.let { hexVersOctets(it) })
                    if (cle != null && cle!!.methode != "AES-128") throw RefusSite("Chiffrement de flux non pris en charge (${cle!!.methode}).")
                }
                l.startsWith("#EXT-X-MAP:") -> init = attributs(l)["URI"]?.let { resoudre(base, it) }
                l.startsWith("#EXT-X-BYTERANGE:") -> {
                    val v = l.substringAfter(':')
                    val longueur = v.substringBefore('@').toLongOrNull() ?: 0L
                    val debut = if (v.contains('@')) v.substringAfter('@').toLongOrNull() ?: finPlage else finPlage
                    plage = "bytes=$debut-${debut + longueur - 1}"
                    finPlage = debut + longueur
                }
                l.startsWith("#EXTINF") -> attendSegment = true
                l.isNotEmpty() && !l.startsWith("#") && attendSegment -> {
                    segments.add(Segment(resoudre(base, l), cle, sequence, plage))
                    sequence++
                    plage = null
                    attendSegment = false
                }
            }
        }
        if (segments.isEmpty()) throw RefusSite("Aucun morceau de vidéo dans ce flux.")
        return segments to init
    }

    /** Retire un éventuel faux en-tête d'image placé devant les morceaux TS (procédé courant des sites de streaming). */
    private fun nettoyerTs(o: ByteArray): ByteArray {
        if (o.size > 400 && o[0] == 0x47.toByte() && o[188] == 0x47.toByte()) return o
        val limite = minOf(o.size - 377, 64 * 1024)
        for (i in 0 until limite.coerceAtLeast(0)) {
            if (o[i] == 0x47.toByte() && o[i + 188] == 0x47.toByte() && o[i + 376] == 0x47.toByte()) return o.copyOfRange(i, o.size)
        }
        return o
    }

    private fun dechiffrer(donnees: ByteArray, cle: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), IvParameterSpec(iv))
        return c.doFinal(donnees)
    }

    private fun ivSequence(n: Long): ByteArray = ByteArray(16).also { b -> for (i in 0 until 8) b[15 - i] = (n shr (8 * i)).toByte() }

    suspend fun telecharger(t: Tache, dossier: File, avance: Avancement): String {
        avance(null, 0L, "Lecture du flux vidéo")
        var url = t.url
        var texte = Reseau.lireTexte(url, t.referer, t.origine, t.agent)
        if (!texte.trimStart().startsWith("#EXTM3U")) throw RefusSite("Le flux n'est pas lisible (liste HLS attendue).")
        if (texte.contains("#EXT-X-STREAM-INF")) {
            url = choisirVariante(texte, url)
            texte = Reseau.lireTexte(url, t.referer, t.origine, t.agent)
        }
        val (segments, init) = lireSegments(texte, url)
        val morceaux = File(dossier, "morceaux").apply { mkdirs() }
        val cles = ConcurrentHashMap<String, ByteArray>()
        val faits = AtomicInteger(morceaux.listFiles()?.count { it.name.endsWith(".seg") } ?: 0)
        val octets = AtomicLong(morceaux.listFiles()?.filter { it.name.endsWith(".seg") }?.sumOf { it.length() } ?: 0L)
        val suivant = AtomicInteger(0)
        var erreur: Throwable? = null

        coroutineScope {
            repeat(4) {
                launch {
                    while (erreur == null) {
                        val i = suivant.getAndIncrement()
                        if (i >= segments.size) break
                        val fichier = File(morceaux, "%06d.seg".format(i))
                        if (fichier.exists()) continue
                        val s = segments[i]
                        var essai = 0
                        while (true) {
                            coroutineContext.ensureActive()
                            try {
                                var donnees = Reseau.lireOctets(s.url, t.referer, t.origine, t.agent, s.plage)
                                s.cle?.let { k ->
                                    val uri = k.uri ?: throw RefusSite("Clé du flux introuvable.")
                                    val cle = cles.getOrPut(uri) { Reseau.lireOctets(uri, t.referer, t.origine, t.agent) }
                                    if (cle.size != 16) throw RefusSite("Clé du flux refusée par le site.")
                                    donnees = dechiffrer(donnees, cle, k.iv ?: ivSequence(s.sequence))
                                }
                                if (init == null) donnees = nettoyerTs(donnees)
                                val tmp = File(morceaux, "%06d.part".format(i))
                                tmp.writeBytes(donnees)
                                if (!tmp.renameTo(fichier)) throw IOException("écriture impossible")
                                octets.addAndGet(donnees.size.toLong())
                                val n = faits.incrementAndGet()
                                avance(n.toFloat() / segments.size, octets.get(), "Morceau $n sur ${segments.size}")
                                break
                            } catch (e: RefusSite) {
                                if (++essai >= 3) { erreur = e; break }
                                delay(1500L * essai)
                            } catch (e: IOException) {
                                if (++essai >= 4) { erreur = e; break }
                                delay(1500L * essai)
                            }
                        }
                    }
                }
            }
        }
        erreur?.let { throw it }

        avance(1f, octets.get(), "Assemblage de la vidéo")
        val ext = if (init != null) "mp4" else "ts"
        val sortie = File(dossier, "video.$ext.part")
        verifierEspace(dossier, octets.get())
        FileOutputStream(sortie).use { out ->
            if (init != null) out.write(Reseau.lireOctets(init, t.referer, t.origine, t.agent))
            for (i in segments.indices) {
                coroutineContext.ensureActive()
                File(morceaux, "%06d.seg".format(i)).inputStream().use { it.copyTo(out, 256 * 1024) }
            }
            out.flush()
            out.fd.sync()
        }
        val nom = "video.$ext"
        if (!sortie.renameTo(File(dossier, nom))) throw IOException("assemblage impossible")
        morceaux.deleteRecursively()
        return nom
    }
}

internal fun verifierEspace(dossier: File, besoin: Long) {
    val libre = dossier.usableSpace
    if (libre < besoin + 200L * 1024 * 1024) throw RefusSite("Espace insuffisant sur le téléphone.")
}
