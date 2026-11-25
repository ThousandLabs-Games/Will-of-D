package com.thousandlabs.willofd.navigateur

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.thousandlabs.willofd.donnees.Carnet
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.protection.Bloqueur
import com.thousandlabs.willofd.protection.Confidentialite
import com.thousandlabs.willofd.telechargement.Detection
import com.thousandlabs.willofd.telechargement.GenreMedia
import com.thousandlabs.willofd.telechargement.MediaDetecte
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** Moteurs de recherche respectueux de la vie privée. */
val MOTEURS = listOf(
    "DuckDuckGo" to "https://duckduckgo.com/?q=",
    "Brave Search" to "https://search.brave.com/search?q=",
    "Startpage" to "https://www.startpage.com/do/search?q=",
    "Qwant" to "https://www.qwant.com/?q=",
    "Google" to "https://www.google.com/search?q="
)

/** Un onglet. Une adresse vide correspond à la page d'accueil de Will of D. */
class Onglet(val id: Long, adresse: String, val prive: Boolean = false) {
    var url by mutableStateOf(adresse)
    var titre by mutableStateOf("")
    var progression by mutableIntStateOf(100)
    var bloques by mutableIntStateOf(0)
    var ordinateur by mutableStateOf(Reglages.modeOrdinateur)
    var peutReculer by mutableStateOf(false)
    var peutAvancer by mutableStateOf(false)
    var icone by mutableStateOf<ImageBitmap?>(null)
    var apercu by mutableStateOf<ImageBitmap?>(null)
    var drm by mutableStateOf(false)
    var fluxLecteur by mutableStateOf(false)
    var dernierUsage by mutableLongStateOf(System.currentTimeMillis())
    val medias = mutableStateListOf<MediaDetecte>()
    /** Domaines autorisés à la main après une redirection bloquée. */
    val autorises = HashSet<String>()

    @Volatile var pageUrl: String? = adresse.ifBlank { null }
    internal val compteur = AtomicInteger(0)
    internal var vue: WebView? = null
    internal var script: ScriptHandler? = null
    internal var configScript: String = ""

    val estAccueil: Boolean get() = url.isBlank()
    val hote: String get() = runCatching { Uri.parse(url).host?.removePrefix("www.") ?: "" }.getOrDefault("")
}

/** Indication discrète affichée en bas de l'écran (élément bloqué, redirection refusée…). */
data class Evenement(
    val texte: String,
    val action: String? = null,
    val faire: (() -> Unit)? = null,
    val id: Long = System.nanoTime()
)

class PleinEcran(val vue: View, val rappel: WebChromeClient.CustomViewCallback)

data class ImagePage(val url: String, val largeur: Int, val hauteur: Int)
data class ResultatPages(val titre: String, val url: String, val images: List<ImagePage>, val ignorees: Int)

/** Réception des images récupérées depuis la page elle-même (repli face aux sites qui bloquent). */
interface RecepteurExtraction {
    fun image(index: Int, octets: ByteArray, mime: String)
    fun echec(index: Int)
    fun fini()
}

object Navigateur {
    val onglets = mutableStateListOf<Onglet>()
    var courant by mutableIntStateOf(0)
    var evenement by mutableStateOf<Evenement?>(null)
    var pleinEcran by mutableStateOf<PleinEcran?>(null)
    var selection by mutableStateOf(false)
    var choix by mutableStateOf<Triple<String, String, Int>?>(null)
    /** Demande d'ouverture du panneau de téléchargement (lien de téléchargement direct reçu du site). */
    var propositionDirecte by mutableStateOf<MediaDetecte?>(null)

    private const val PROFIL_PRIVE = "prive"
    private val principal = Handler(Looper.getMainLooper())
    private var prochainId = 1L
    private var restaure = false
    private val extractions = HashMap<String, RecepteurExtraction>()

    val onglet: Onglet
        get() {
            if (!restaure) restaurer()
            if (onglets.isEmpty()) onglets.add(Onglet(prochainId++, ""))
            return onglets[courant.coerceIn(0, onglets.lastIndex)]
        }

    fun parId(id: Long): Onglet? = onglets.firstOrNull { it.id == id }

    fun signaler(texte: String, action: String? = null, faire: (() -> Unit)? = null) {
        principal.post { evenement = Evenement(texte, action, faire) }
    }

    // ---------------------------------------------------------------- Onglets

    fun restaurer() {
        if (restaure) return
        restaure = true
        Reglages.liste("onglets").forEach { onglets.add(Onglet(prochainId++, it)) }
        if (onglets.isEmpty()) onglets.add(Onglet(prochainId++, ""))
        courant = Reglages.entier("onglet_courant", 0).coerceIn(0, onglets.lastIndex)
    }

    fun enregistrer() {
        val gardes = onglets.filter { !it.prive }
        Reglages.ecrireListe("onglets", gardes.map { it.url })
        Reglages.ecrire("onglet_courant", gardes.indexOf(onglets.getOrNull(courant)).coerceAtLeast(0))
    }

    fun nouvelOnglet(url: String = "", prive: Boolean = false, selectionner: Boolean = true): Onglet {
        restaurer()
        val o = Onglet(prochainId++, url, prive)
        onglets.add(o)
        if (selectionner) choisir(onglets.lastIndex)
        enregistrer()
        return o
    }

    fun choisir(i: Int) {
        courant = i.coerceIn(0, onglets.lastIndex)
        onglet.dernierUsage = System.currentTimeMillis()
        liberer()
        enregistrer()
    }

    fun fermer(o: Onglet) {
        val i = onglets.indexOf(o)
        if (i < 0) return
        detruire(o)
        onglets.removeAt(i)
        if (onglets.isEmpty()) onglets.add(Onglet(prochainId++, ""))
        if (courant >= i) courant = (courant - 1).coerceAtLeast(0)
        courant = courant.coerceIn(0, onglets.lastIndex)
        if (o.prive && onglets.none { it.prive }) oublierPrive()
        enregistrer()
    }

    fun fermerTout() {
        onglets.toList().forEach { detruire(it) }
        val prive = onglets.any { it.prive }
        onglets.clear()
        onglets.add(Onglet(prochainId++, ""))
        courant = 0
        if (prive) oublierPrive()
        enregistrer()
    }

    private fun oublierPrive() {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().deleteProfile(PROFIL_PRIVE)
            }
        }
    }

    private fun detruire(o: Onglet) {
        o.script = null
        o.vue?.let { v ->
            runCatching { v.stopLoading(); v.loadUrl("about:blank"); v.destroy() }
        }
        o.vue = null
    }

    /** Au-delà de 5 onglets en mémoire, les plus anciens sont libérés (rechargés quand on y revient). */
    private fun liberer() {
        val actifs = onglets.filter { it.vue != null && it !== onglet }.sortedBy { it.dernierUsage }
        if (actifs.size <= 4) return
        actifs.take(actifs.size - 4).forEach { o ->
            capturer(o)
            detruire(o)
        }
    }

    fun capturer(o: Onglet) {
        val v = o.vue ?: return
        if (v.width <= 0 || v.height <= 0) return
        runCatching {
            val l = 360
            val h = (l * v.height / v.width.toFloat()).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(l, h, Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(b)
            c.scale(l / v.width.toFloat(), l / v.width.toFloat())
            v.draw(c)
            o.apercu = b.asImageBitmap()
        }
    }

    // ---------------------------------------------------------------- Adresses

    private val DOMAINE = Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$")

    /** Ce qui est saisi dans la barre d'adresse : une adresse, ou une recherche. */
    fun versUrl(saisie: String): String? {
        val t = saisie.trim()
        if (t.isEmpty()) return null
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            DOMAINE.matches(t) && !t.contains(' ') -> "https://$t"
            else -> MOTEURS.getOrElse(Reglages.moteur) { MOTEURS[0] }.second + URLEncoder.encode(t, "UTF-8")
        }
        return Confidentialite.securiser(Confidentialite.nettoyer(url))
    }

    fun ouvrir(saisie: String) {
        val url = versUrl(saisie) ?: return
        val o = onglet
        o.url = url
        o.vue?.let { charger(o, it, url) }
    }

    fun charger(o: Onglet, v: WebView, url: String) {
        val agent = Confidentialite.agent(o.ordinateur)
        if (v.settings.userAgentString != agent) v.settings.userAgentString = agent
        rafraichirScript(o, v)
        v.loadUrl(url, Confidentialite.entetes())
    }

    fun accueil() {
        val o = onglet
        o.url = ""
        o.titre = ""
        o.medias.clear()
        enregistrer()
    }

    fun modeOrdinateur(o: Onglet, actif: Boolean) {
        o.ordinateur = actif
        val v = o.vue ?: return
        v.settings.userAgentString = Confidentialite.agent(actif)
        v.settings.useWideViewPort = true
        if (!o.estAccueil) v.reload()
    }

    fun recharger(o: Onglet) {
        val v = o.vue ?: return
        rafraichirScript(o, v)
        v.reload()
    }

    /** Le garde suit les réglages : il est réenregistré dès que ceux-ci ont changé. */
    private fun rafraichirScript(o: Onglet, v: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val cfg = Scripts.configGarde()
        if (o.script != null && cfg == o.configScript) return
        runCatching { o.script?.remove() }
        o.script = runCatching { WebViewCompat.addDocumentStartJavaScript(v, Scripts.garde(), setOf("*")) }.getOrNull()
        o.configScript = cfg
    }

    // ---------------------------------------------------------------- Outils de page

    fun demarrerSelection() {
        selection = true
        choix = null
        onglet.vue?.evaluateJavascript(Scripts.SELECTEUR, null)
    }

    fun arreterSelection() {
        selection = false
        choix = null
        onglet.vue?.evaluateJavascript("if(window.wodPick)wodPick.stop()", null)
    }

    fun elargirSelection() {
        onglet.vue?.evaluateJavascript("if(window.wodPick)wodPick.parent()", null)
    }

    fun masquerChoix() {
        val c = choix ?: return
        if (c.third > 6) {
            signaler("${c.third} éléments semblables : touchez « Élargir » pour choisir l'encart entier.")
            return
        }
        val hote = Bloqueur.host(onglet.url) ?: return
        Bloqueur.UserHiding.add(hote, c.first)
        onglet.vue?.evaluateJavascript("if(window.wodPick)wodPick.hide(" + JSONObject.quote(c.first) + ")", null)
        choix = null
        signaler("Élément masqué sur tout le site.")
    }

    private fun appliquerMasquage(v: WebView, url: String?) {
        val css = Bloqueur.cosmeticCss(url)
        if (css.isNotEmpty()) v.evaluateJavascript(Scripts.cssMasquage(css), null)
    }

    /** Recherche des planches d'un chapitre dans la page affichée. */
    fun analyserPages(o: Onglet, resultat: (ResultatPages?) -> Unit) {
        val v = o.vue ?: return resultat(null)
        v.evaluateJavascript(Scripts.SCAN_PAGES) { brut ->
            resultat(runCatching { lireAnalyse(JSONArray("[$brut]").getString(0)) }.getOrNull())
        }
    }

    private fun lireAnalyse(texte: String): ResultatPages {
        val o = JSONObject(texte)
        val a = o.getJSONArray("images")
        val toutes = List(a.length()) { i ->
            val x = a.getJSONObject(i)
            Triple(ImagePage(x.getString("u"), x.optInt("w"), x.optInt("h")), x.optInt("dw"), x.optBoolean("p"))
        }
        val petites = Reglages.petitesImages
        val candidates = toutes.filter { (img, dw, paresseuse) ->
            val ext = img.url.substringBefore('?').substringAfterLast('.', "").lowercase()
            if (ext == "svg" || ext == "ico") return@filter false
            when {
                petites -> true
                img.largeur > 0 -> img.largeur >= 300 && img.hauteur >= 250
                else -> paresseuse || dw >= 200
            }
        }
        // Les planches d'un chapitre ont des largeurs proches : les vignettes et bannières à part sont écartées.
        val largeurs = candidates.map { it.first.largeur }.filter { it > 0 }.sorted()
        val gardees = if (largeurs.size >= 3 && !petites) {
            val mediane = largeurs[largeurs.size / 2]
            candidates.filter { it.first.largeur == 0 || it.first.largeur >= mediane * 0.55 }
        } else candidates
        return ResultatPages(
            titre = o.optString("titre"),
            url = o.optString("url"),
            images = gardees.map { it.first },
            ignorees = toutes.size - gardees.size
        )
    }

    fun contourner(o: Onglet, fini: () -> Unit) {
        val v = o.vue ?: return fini()
        v.evaluateJavascript(Scripts.CONTOURNEMENT) { fini() }
    }

    /** Lance l'extraction des images depuis la page ; renvoie faux si l'onglet n'est plus disponible. */
    fun extraireDansLaPage(ongletId: Long, pageUrl: String, tache: String, urls: List<String>, recepteur: RecepteurExtraction): Boolean {
        val o = parId(ongletId) ?: return false
        val v = o.vue ?: return false
        if (o.pageUrl?.substringBefore('#') != pageUrl.substringBefore('#')) return false
        synchronized(extractions) { extractions[tache] = recepteur }
        principal.post {
            // Neutralisation ciblée des scripts qui bloquent l'enregistrement, puis récupération.
            if (Reglages.contournement) v.evaluateJavascript(Scripts.CONTOURNEMENT, null)
            v.evaluateJavascript(Scripts.extraction(tache, urls), null)
        }
        return true
    }

    fun oublierExtraction(tache: String) { synchronized(extractions) { extractions.remove(tache) } }

    private fun recepteur(tache: String): RecepteurExtraction? = synchronized(extractions) { extractions[tache] }

    // ---------------------------------------------------------------- Moteur de page

    private fun base(h: String?): String = if (h == null) "" else Bloqueur.baseDomain(h.lowercase())

    private fun ajouterMedia(o: Onglet, m: MediaDetecte) {
        principal.post {
            if (o.medias.any { it.url.substringBefore('?') == m.url.substringBefore('?') && it.genre == m.genre }) return@post
            o.medias.add(0, m)
            while (o.medias.size > 30) o.medias.removeAt(o.medias.lastIndex)
        }
    }

    /** Pont avec les pages : signalements uniquement. */
    private class Pont(private val o: Onglet) {
        @JavascriptInterface
        fun signaler(type: String, detail: String) {
            when (type) {
                "fenetre" -> {
                    o.compteur.incrementAndGet()
                    val h = runCatching { Uri.parse(detail).host }.getOrNull()
                    if (o === Navigateur.onglet) Navigateur.signaler(if (h.isNullOrBlank()) "Fenêtre surgissante bloquée" else "Fenêtre surgissante bloquée : $h")
                }
                "calque" -> {
                    o.compteur.incrementAndGet()
                    if (o === Navigateur.onglet) Navigateur.signaler("Calque publicitaire invisible retiré. Touchez à nouveau pour continuer.")
                }
                "drm" -> Navigateur.principal.post { o.drm = true }
            }
        }

        @JavascriptInterface
        fun media(url: String, type: String, origine: String, titre: String) {
            if (type == "flux" || url.isBlank()) {
                Navigateur.principal.post { o.fluxLecteur = true }
                return
            }
            val genre = Detection.classer(url) ?: GenreMedia.DIRECT
            Navigateur.ajouterMedia(o, MediaDetecte(url, genre, origine, "", titre.ifBlank { o.titre }))
        }

        @JavascriptInterface
        fun choisi(selecteur: String, texte: String, nombre: Int) {
            Navigateur.principal.post { if (o === Navigateur.onglet) Navigateur.choix = Triple(selecteur, texte, nombre) }
        }

        @JavascriptInterface
        fun image(tache: String, index: Int, base64: String, mime: String) {
            val r = Navigateur.recepteur(tache) ?: return
            val octets = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }.getOrNull()
            if (octets == null || octets.isEmpty()) r.echec(index) else r.image(index, octets, mime)
        }

        @JavascriptInterface
        fun imageEchec(tache: String, index: Int) { Navigateur.recepteur(tache)?.echec(index) }

        @JavascriptInterface
        fun extractionFinie(tache: String) { Navigateur.recepteur(tache)?.fini() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun vue(o: Onglet, context: Context): WebView {
        o.vue?.let { return it }
        val w = WebView(context)
        if (o.prive) runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getOrCreateProfile(PROFIL_PRIVE)
                WebViewCompat.setProfile(w, PROFIL_PRIVE)
            }
        }
        Confidentialite.configurer(w, o.prive, o.ordinateur)
        w.addJavascriptInterface(Pont(o), "WillOfD")
        rafraichirScript(o, w)

        w.setDownloadListener { url, _, disposition, mime, taille ->
            val nom = URLUtil.guessFileName(url, disposition, mime)
            if (Detection.estVideoMime(mime) || Detection.classer(url) != null || nom.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "m4v", "mov", "avi")) {
                val m = MediaDetecte(
                    url = url,
                    genre = Detection.classer(url) ?: GenreMedia.DIRECT,
                    referer = o.pageUrl ?: url,
                    origine = o.pageUrl ?: "",
                    titrePage = o.titre,
                    nomFichier = nom,
                    taille = taille
                )
                ajouterMedia(o, m)
                principal.post { propositionDirecte = m }
            } else {
                signaler("Fichier non pris en charge ($nom) : Will of D télécharge les vidéos et les pages.")
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { o.progression = p }
            override fun onReceivedTitle(view: WebView, title: String?) { if (!title.isNullOrBlank()) o.titre = title }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { if (icon != null) o.icone = icon.asImageBitmap() }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                pleinEcran?.rappel?.onCustomViewHidden()
                pleinEcran = PleinEcran(view, callback)
            }

            override fun onHideCustomView() { pleinEcran = null }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) principal.post { o.drm = true }
                request.deny()
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val temporaire = WebView(view.context)
                var decide = false
                fun decider(cible: String?) {
                    if (decide) return
                    decide = true
                    principal.post { runCatching { temporaire.stopLoading(); temporaire.destroy() } }
                    val url = cible ?: return
                    if (!url.startsWith("http")) {
                        o.compteur.incrementAndGet()
                        signaler("Fenêtre surgissante bloquée")
                        return
                    }
                    val h = Uri.parse(url).host
                    val meme = base(h) == base(Uri.parse(o.pageUrl ?: "").host)
                    when {
                        !Reglages.bloquerFenetres -> principal.post { nouvelOnglet(url, o.prive) }
                        Bloqueur.domaineBloque(url) -> {
                            o.compteur.incrementAndGet()
                            signaler("Fenêtre publicitaire bloquée : $h")
                        }
                        isUserGesture && meme -> principal.post { nouvelOnglet(url, o.prive) }
                        isUserGesture -> signaler("Nouvelle fenêtre bloquée : $h", "Ouvrir") { nouvelOnglet(url, o.prive) }
                        else -> {
                            o.compteur.incrementAndGet()
                            signaler("Fenêtre surgissante bloquée : $h")
                        }
                    }
                }
                temporaire.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        decider(r.url.toString()); return true
                    }
                    override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) {
                        if (u != null && u != "about:blank") decider(u)
                    }
                }
                transport.webView = temporaire
                resultMsg.sendToTarget()
                principal.postDelayed({ if (!decide) decider(null) }, 4000)
                return true
            }
        }

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                val schema = request.url.scheme?.lowercase() ?: ""
                val web = schema == "http" || schema == "https"
                if (!request.isForMainFrame) {
                    // Cadres (lecteurs intégrés) : libres, sauf l'ouverture d'autres applications.
                    return !web && schema !in setOf("about", "data", "blob", "javascript")
                }
                if (!web) {
                    if (schema == "about" || schema == "javascript") return false
                    if (Reglages.bloquerApplis) {
                        o.compteur.incrementAndGet()
                        signaler("Ouverture d'une autre application bloquée")
                    } else runCatching {
                        val i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME).addCategory(Intent.CATEGORY_BROWSABLE)
                        i.component = null
                        i.selector = null
                        view.context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }
                val hoteActuel = Uri.parse(o.pageUrl ?: "").host
                val hoteCible = request.url.host
                val externe = hoteActuel != null && base(hoteCible) != base(hoteActuel)
                if (externe && Reglages.bloquerRedirections) {
                    if (Bloqueur.domaineBloque(u)) {
                        o.compteur.incrementAndGet()
                        signaler("Redirection publicitaire bloquée : $hoteCible")
                        return true
                    }
                    // Sans geste de l'utilisateur, un changement de site est une redirection non sollicitée.
                    if (!request.hasGesture() && !request.isRedirect && base(hoteCible) !in o.autorises) {
                        o.compteur.incrementAndGet()
                        signaler("Redirection bloquée vers $hoteCible", "Autoriser") {
                            o.autorises.add(base(hoteCible))
                            o.vue?.let { charger(o, it, u) }
                        }
                        return true
                    }
                }
                val propre = Confidentialite.securiser(Confidentialite.nettoyer(u))
                if (propre != u) {
                    charger(o, view, propre)
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                val u = request.url.toString()
                Detection.classer(u)?.let { genre ->
                    val referer = request.requestHeaders["Referer"] ?: request.requestHeaders["referer"] ?: o.pageUrl ?: ""
                    val origine = request.requestHeaders["Origin"] ?: request.requestHeaders["origin"] ?: ""
                    ajouterMedia(o, MediaDetecte(u, genre, referer, origine, o.titre))
                }
                if (Bloqueur.shouldBlock(u, o.pageUrl)) {
                    val n = o.compteur.incrementAndGet()
                    if (n % 5 == 1) principal.post { o.bloques = o.compteur.get() }
                    return Bloqueur.emptyResponse()
                }
                return null
            }

            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                o.pageUrl = u
                if (u != null && u.startsWith("http")) o.url = u
                o.compteur.set(0)
                o.bloques = 0
                o.drm = false
                o.fluxLecteur = false
                o.medias.clear()
                if (o === onglet) { selection = false; choix = null }
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view.evaluateJavascript(Scripts.garde(), null)
                }
            }

            override fun onPageCommitVisible(view: WebView, u: String?) { appliquerMasquage(view, u) }

            override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                o.peutReculer = view.canGoBack()
                o.peutAvancer = view.canGoForward()
                if (u != null && u.startsWith("http")) { o.url = u; o.pageUrl = u }
            }

            override fun onPageFinished(view: WebView, u: String?) {
                o.bloques = o.compteur.get()
                o.peutReculer = view.canGoBack()
                o.peutAvancer = view.canGoForward()
                appliquerMasquage(view, u)
                if (u == null || !u.startsWith("http")) return
                if (!o.prive) {
                    Carnet.ajouterHistorique(view.title.orEmpty(), u)
                    enregistrer()
                }
                Bloqueur.enregistrerCompteur()
                view.postDelayed({ if (o === onglet) capturer(o) }, 1200)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                signaler("Connexion non sécurisée refusée : certificat du site invalide.")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Le moteur de la page s'est arrêté : l'onglet est recréé au lieu de fermer l'application.
                if (o.vue === view) {
                    o.vue = null
                    o.script = null
                }
                runCatching { view.destroy() }
                principal.post {
                    val i = onglets.indexOf(o)
                    if (i >= 0) {
                        onglets[i] = Onglet(prochainId++, o.url, o.prive)
                        signaler("La page a été rechargée après un incident du moteur de navigation.")
                    }
                }
                return true
            }
        }

        o.vue = w
        if (!o.estAccueil) charger(o, w, o.url)
        return w
    }

    fun partager(context: Context) {
        val u = onglet.url
        if (u.isBlank()) return
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u), "Partager")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
