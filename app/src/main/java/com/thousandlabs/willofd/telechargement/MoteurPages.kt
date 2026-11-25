package com.thousandlabs.willofd.telechargement

import com.thousandlabs.willofd.depot.Depot
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.navigateur.RecepteurExtraction
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Téléchargement des planches d'un chapitre au format Rio Poneglyph
 * (images numérotées + poneglyph.json), avec repli progressif face aux sites qui bloquent.
 */
object MoteurPages {

    /** Extension déduite du contenu réel ; null si ce n'est pas une image. */
    fun extensionImage(o: ByteArray): String? {
        if (o.size < 12) return null
        fun b(i: Int) = o[i].toInt() and 0xFF
        return when {
            b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF -> "jpg"
            b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> "png"
            b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46 -> "gif"
            b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 && b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> "webp"
            String(o, 4, 8, Charsets.ISO_8859_1).let { it == "ftypavif" || it == "ftypavis" } -> "avif"
            String(o, 4, 8, Charsets.ISO_8859_1).let { it.startsWith("ftyphei") || it.startsWith("ftypmif") } -> "heic"
            b(0) == 0x42 && b(1) == 0x4D -> "bmp"
            else -> null
        }
    }

    private fun largeur(n: Int) = maxOf(3, n.toString().length)

    private fun existant(dossier: File, index: Int, n: Int): File? {
        val prefixe = index.plus(1).toString().padStart(largeur(n), '0') + "."
        return dossier.listFiles()?.firstOrNull { it.name.startsWith(prefixe) && !it.name.endsWith(".part") && it.length() > 0 }
    }

    private fun enregistrer(dossier: File, index: Int, n: Int, octets: ByteArray): Boolean {
        val ext = extensionImage(octets) ?: return false
        if (octets.size < 1500) return false
        val nom = index.plus(1).toString().padStart(largeur(n), '0') + "." + ext
        val tmp = File(dossier, "$nom.part")
        tmp.writeBytes(octets)
        return tmp.renameTo(File(dossier, nom))
    }

    suspend fun telecharger(t: Tache, dossier: File, avance: Avancement): List<String> {
        val n = t.images.size
        if (n == 0) throw RefusSite("Aucune planche à télécharger.")
        val manquantes = ArrayList<Int>()
        var octets = 0L

        // 1. Téléchargement classique, avec les cookies et la page d'origine du navigateur.
        for (i in 0 until n) {
            coroutineContext.ensureActive()
            existant(dossier, i, n)?.let { octets += it.length() }
            if (existant(dossier, i, n) != null) continue
            avance(i.toFloat() / n, octets, "Page ${i + 1} sur $n")
            var ok = false
            for (essai in 1..2) {
                try {
                    val donnees = Reseau.lireOctets(t.images[i], t.pageUrl, null, t.agent)
                    if (enregistrer(dossier, i, n, donnees)) { ok = true; octets += donnees.size; break }
                } catch (e: IOException) {
                    if (e is RefusSite && (e.code == 403 || e.code == 401)) break
                }
            }
            if (!ok) manquantes.add(i)
        }

        // 2. Repli : récupération depuis la page elle-même (scripts gênants neutralisés au préalable).
        if (manquantes.isNotEmpty() && Reglages.extractionPage) {
            avance(null, octets, "Le site bloque ${manquantes.size} page(s) : récupération depuis la page")
            val recues = extraire(t, dossier, n, manquantes)
            manquantes.removeAll(recues.toSet())
        }

        // 3. Constat : le site résiste, mieux vaut passer à un autre site.
        if (manquantes.isNotEmpty()) {
            throw RefusSite(
                "Ce site résiste au téléchargement : ${manquantes.size} page(s) sur $n n'ont pas pu être récupérées. " +
                    "Gardez la page ouverte et réessayez, ou changez de site."
            )
        }

        val noms = (0 until n).map { existant(dossier, it, n)?.name ?: throw IOException("page ${it + 1} manquante") }
        val manifeste = JSONObject()
            .put("format", "rio-poneglyph-pages")
            .put("version", 2)
            .put("titre", t.titre)
            .put("serie", t.serie)
            .put("numero", t.numero)
            .put("partie", t.partie)
            .put("cle_serie", t.cleSerie)
            .put("cle_element", t.cleElement)
            .put("sens", t.sens)
            .put("source", t.pageUrl)
            .put("pages", JSONArray(noms))
        File(dossier, Depot.MANIFESTE).writeText(manifeste.toString(2))
        return noms
    }

    private suspend fun extraire(t: Tache, dossier: File, n: Int, indices: List<Int>): List<Int> {
        val recues = ArrayList<Int>()
        val urls = indices.map { t.images[it] }
        val cle = t.id + "-" + System.currentTimeMillis()
        val fin = withTimeoutOrNull(180_000L) {
            suspendCancellableCoroutine<Boolean> { suite ->
                val recepteur = object : RecepteurExtraction {
                    override fun image(index: Int, octets: ByteArray, mime: String) {
                        val i = indices.getOrNull(index) ?: return
                        if (enregistrer(dossier, i, n, octets)) synchronized(recues) { recues.add(i) }
                    }
                    override fun echec(index: Int) = Unit
                    override fun fini() { if (suite.isActive) suite.resume(true) }
                }
                val lance = Navigateur.extraireDansLaPage(t.ongletId, t.pageUrl, cle, urls, recepteur)
                if (!lance) suite.resume(false)
                suite.invokeOnCancellation { Navigateur.oublierExtraction(cle) }
            }
        }
        Navigateur.oublierExtraction(cle)
        if (fin == null) return synchronized(recues) { recues.toList() }
        return synchronized(recues) { recues.toList() }
    }
}
