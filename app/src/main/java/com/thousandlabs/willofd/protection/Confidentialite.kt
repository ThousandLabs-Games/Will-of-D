package com.thousandlabs.willofd.protection

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.thousandlabs.willofd.donnees.Reglages

/** Réglages de confidentialité appliqués au moteur de navigation. */
object Confidentialite {

    @Volatile private var uaMobile: String? = null

    fun init(context: Context) {
        uaMobile = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
    }

    /** Agent du téléphone, sans la mention « wv » qui fait refuser certains lecteurs vidéo. */
    val agentMobile: String
        get() = (uaMobile ?: "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
            .replace("; wv)", ")")
            .replace(Regex("Version/[\\d.]+ "), "")

    val agentOrdinateur: String
        get() {
            val chrome = Regex("Chrome/[\\d.]+").find(agentMobile)?.value ?: "Chrome/130.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        }

    fun agent(ordinateur: Boolean) = if (ordinateur) agentOrdinateur else agentMobile

    @SuppressLint("SetJavaScriptEnabled")
    fun configurer(web: WebView, prive: Boolean, ordinateur: Boolean) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            // Les fenêtres surgissantes passent par onCreateWindow, où elles sont contrôlées.
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = agent(ordinateur)
            @Suppress("DEPRECATION")
            saveFormData = false
            safeBrowsingEnabled = Reglages.navigationSure
            cacheMode = if (prive) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !Reglages.antiPistage)
        // N'annonce pas aux sites qu'il s'agit d'une application (en-tête X-Requested-With).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet()) }
        }
    }

    /** En-têtes « ne me pistez pas ». */
    fun entetes(): Map<String, String> =
        if (Reglages.antiPistage) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    private val PARAMS_PISTAGE = Regex(
        "^(utm_[a-z_]+|fbclid|gclid|gclsrc|dclid|gbraid|wbraid|msclkid|mc_eid|mc_cid|igshid|yclid|_hsenc|_hsmi|" +
            "ref_src|ref_url|oly_anon_id|oly_enc_id|vero_id|wickedid|twclid|ttclid|s_cid|mkt_tok|trk|trkCampaign|sc_cid|__s|_openstat)$",
        RegexOption.IGNORE_CASE
    )

    /** Retire les paramètres de pistage d'une adresse (utm_source, fbclid…). */
    fun nettoyer(url: String): String {
        if (!Reglages.antiPistage) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val noms = runCatching { uri.queryParameterNames }.getOrNull() ?: return url
        if (noms.none { PARAMS_PISTAGE.matches(it) }) return url
        val b = uri.buildUpon().clearQuery()
        for (n in noms) if (!PARAMS_PISTAGE.matches(n)) uri.getQueryParameters(n).forEach { b.appendQueryParameter(n, it) }
        return b.build().toString()
    }

    /** Passe en connexion sécurisée (https) quand l'option est active. */
    fun securiser(url: String): String =
        if (Reglages.httpsSeulement && url.startsWith("http://") && !url.startsWith("http://localhost") && !url.startsWith("http://127.")) {
            "https://" + url.removePrefix("http://")
        } else url

    /** Efface cookies, cache, stockage des sites et formulaires. */
    fun effacerDonnees(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        runCatching { WebView(context).apply { clearCache(true); clearFormData(); clearHistory(); destroy() } }
        @Suppress("DEPRECATION")
        runCatching { WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword() }
    }
}
