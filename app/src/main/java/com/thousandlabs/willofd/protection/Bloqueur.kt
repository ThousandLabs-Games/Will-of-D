package com.thousandlabs.willofd.protection

import android.content.Context
import android.webkit.WebResourceResponse
import com.thousandlabs.willofd.donnees.Reglages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Bloqueur de publicités et de pisteurs (Will of D), inspiré de uBlock Origin / Brave :
 * - les mêmes listes publiques (EasyList, EasyPrivacy, liste de Peter Lowe), mises à jour chaque semaine ;
 * - une liste intégrée qui fonctionne dès l'installation, même sans connexion ;
 * - le masquage d'éléments (règles des listes pour le site visité + éléments masqués par le lecteur).
 */
object Bloqueur {
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var blockedDomains: Set<String> = BUILTIN_DOMAINS
    @Volatile private var allowedDomains: Set<String> = emptySet()
    @Volatile private var domainPaths: Map<String, List<Pattern>> = emptyMap()
    @Volatile private var generic: List<Pattern> = emptyList()
    @Volatile private var exceptions: List<Pattern> = emptyList()
    @Volatile private var cosmetic: Map<String, List<String>> = emptyMap()
    @Volatile private var cosmeticExceptions: Map<String, Set<String>> = emptyMap()

    private val _ruleCount = MutableStateFlow(BUILTIN_DOMAINS.size)
    val ruleCount: StateFlow<Int> = _ruleCount.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    @Volatile private var enAttente = 0

    val LISTS = linkedMapOf(
        "easylist" to "https://easylist.to/easylist/easylist.txt",
        "easyprivacy" to "https://easylist.to/easylist/easyprivacy.txt",
        "peterlowe" to "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext",
        "annoyances" to "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
        "urlhaus" to "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-online.txt"
    )

    fun init(context: Context) {
        dir = File(context.filesDir, "bloqueur").apply { mkdirs() }
        scope.launch {
            compile()
            val week = 7L * 24 * 3600 * 1000
            if (Reglages.majListesAuto && System.currentTimeMillis() - lastUpdate() > week) updateLists()
        }
    }

    fun lastUpdate(): Long = Reglages.long("listes_maj", 0L)

    /** Télécharge les listes à jour, puis les applique. */
    fun updateLists(onDone: ((Boolean) -> Unit)? = null) {
        if (_updating.value) return
        _updating.value = true
        scope.launch {
            var ok = 0
            for ((name, url) in LISTS) {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000; readTimeout = 60000
                        setRequestProperty("User-Agent", "WillOfD")
                    }
                    try {
                        if (conn.responseCode == 200) {
                            val text = conn.inputStream.bufferedReader().readText()
                            if (text.length > 1000) { File(dir, "$name.txt").writeText(text); ok++ }
                        }
                    } finally { conn.disconnect() }
                }
            }
            if (ok > 0) Reglages.ecrire("listes_maj", System.currentTimeMillis())
            compile()
            _updating.value = false
            onDone?.invoke(ok > 0)
        }
    }

    // ---------- Règles ----------

    /** Motif simplifié des listes (« * » = n'importe quoi, « ^ » = séparateur). */
    class Pattern(raw: String, val thirdPartyOnly: Boolean, val domains: Set<String>, val notDomains: Set<String>) {
        private val parts: List<String> = raw.split('*').filter { it.isNotEmpty() }
        private val anchoredStart = raw.startsWith("|") && !raw.startsWith("||")
        private val anchoredEnd = raw.endsWith("|")

        fun matches(url: String): Boolean {
            var pos = 0
            for ((i, part0) in parts.withIndex()) {
                var part = part0.trimStart('|').trimEnd('|')
                if (part.isEmpty()) continue
                val endsWithSep = part.endsWith("^")
                part = part.replace("^", "")
                val idx = if (i == 0 && anchoredStart) (if (url.startsWith(part)) 0 else -1) else url.indexOf(part, pos)
                if (idx < 0) return false
                pos = idx + part.length
                if (endsWithSep && pos < url.length) {
                    val c = url[pos]
                    if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%') return false
                }
            }
            return !anchoredEnd || pos == url.length
        }

        fun appliesOn(pageHost: String?): Boolean {
            if (domains.isEmpty() && notDomains.isEmpty()) return true
            val h = pageHost ?: return domains.isEmpty()
            if (notDomains.any { h == it || h.endsWith(".$it") }) return false
            return domains.isEmpty() || domains.any { h == it || h.endsWith(".$it") }
        }
    }

    private fun compile() {
        val blocked = HashSet<String>(BUILTIN_DOMAINS)
        val allowed = HashSet<String>()
        val paths = HashMap<String, MutableList<Pattern>>()
        val gen = ArrayList<Pattern>()
        val exc = ArrayList<Pattern>()
        val cos = HashMap<String, MutableList<String>>()
        val cosExc = HashMap<String, MutableSet<String>>()
        val files = dir.listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        for (f in files) runCatching {
            f.forEachLine { line0 ->
                val line = line0.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEachLine
                parseLine(line, blocked, allowed, paths, gen, exc, cos, cosExc)
            }
        }
        blockedDomains = blocked
        allowedDomains = allowed
        domainPaths = paths
        generic = gen
        exceptions = exc
        cosmetic = cos
        cosmeticExceptions = cosExc
        _ruleCount.value = blocked.size + paths.values.sumOf { it.size } + gen.size + cos.values.sumOf { it.size }
    }

    private val UNSUPPORTED_OPTIONS = setOf(
        "popup", "csp", "redirect", "redirect-rule", "removeparam", "rewrite", "replace", "document", "elemhide",
        "generichide", "specifichide", "genericblock", "header", "permissions", "urltransform", "~third-party", "1p", "first-party", "badfilter"
    )

    private fun parseLine(
        line: String, blocked: HashSet<String>, allowed: HashSet<String>, paths: HashMap<String, MutableList<Pattern>>,
        gen: ArrayList<Pattern>, exc: ArrayList<Pattern>, cos: HashMap<String, MutableList<String>>, cosExc: HashMap<String, MutableSet<String>>
    ) {
        // Masquage d'éléments
        val hideIdx = line.indexOf("##")
        val hideExcIdx = line.indexOf("#@#")
        if (hideExcIdx >= 0) {
            val sel = line.substring(hideExcIdx + 3)
            line.substring(0, hideExcIdx).split(',').filter { it.isNotBlank() }.forEach { d -> cosExc.getOrPut(d.trim()) { HashSet() }.add(sel) }
            return
        }
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#%#") || line.contains("#+js")) return
        if (hideIdx >= 0) {
            val sel = line.substring(hideIdx + 2)
            if (sel.contains(":-abp") || sel.contains(":has-text") || sel.contains(":xpath") || sel.contains(":upward") ||
                sel.contains(":style") || sel.contains(":remove") || sel.contains(":matches-") || sel.contains(":min-text")
            ) return
            val doms = line.substring(0, hideIdx)
            if (doms.isEmpty()) {
                // les règles génériques sont trop nombreuses pour être toutes appliquées : on garde les plus sûres
                if (GENERIC_HIDE_KEEP.any { sel.contains(it) }) cos.getOrPut("*") { ArrayList() }.add(sel)
            } else doms.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("~") }
                .forEach { d -> cos.getOrPut(d) { ArrayList() }.add(sel) }
            return
        }
        // Règles réseau
        var rule = line
        val isException = rule.startsWith("@@")
        if (isException) rule = rule.substring(2)
        var third = false
        var domains = emptySet<String>()
        var notDomains = emptySet<String>()
        val dollar = rule.lastIndexOf('$')
        if (dollar > 0 && !rule.substring(dollar).contains('/')) {
            val opts = rule.substring(dollar + 1).split(',')
            rule = rule.substring(0, dollar)
            for (o in opts) {
                val name = o.substringBefore('=')
                if (name in UNSUPPORTED_OPTIONS) return
                if (o == "third-party" || o == "3p") third = true
                if (name == "domain" || name == "from") {
                    val ds = o.substringAfter('=').split('|')
                    domains = ds.filterNot { it.startsWith("~") }.toSet()
                    notDomains = ds.filter { it.startsWith("~") }.map { it.drop(1) }.toSet()
                }
            }
        }
        if (rule.length < 4 || rule.startsWith("/") && rule.endsWith("/")) return // expressions régulières : ignorées
        if (rule.startsWith("||")) {
            val body = rule.substring(2)
            val end = body.indexOfFirst { it == '/' || it == '^' || it == '*' || it == ':' || it == '?' }
            val host = (if (end < 0) body else body.substring(0, end)).lowercase()
            val rest = if (end < 0) "" else body.substring(end)
            if (host.isEmpty() || host.contains('*')) return
            if (isException) {
                if (rest.isEmpty() || rest == "^") allowed.add(host) else exc.add(Pattern(rule, false, domains, notDomains))
                return
            }
            if ((rest.isEmpty() || rest == "^" || rest == "^|") && domains.isEmpty() && notDomains.isEmpty()) {
                if (third) paths.getOrPut(host) { ArrayList() }.add(Pattern("^", true, emptySet(), emptySet()))
                else blocked.add(host)
            } else paths.getOrPut(host) { ArrayList() }.add(Pattern(rest.ifEmpty { "^" }, third, domains, notDomains))
            return
        }
        if (isException) { exc.add(Pattern(rule, false, domains, notDomains)); return }
        // motifs génériques : seulement ceux qui sont assez précis pour ne rien casser
        if (rule.replace("*", "").length >= 7) gen.add(Pattern(rule, third, domains, notDomains))
    }

    // ---------- Décision ----------

    fun host(url: String?): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    /** Domaine principal (« ads.exemple.co.uk » → « exemple.co.uk »). */
    fun baseDomain(h: String): String {
        val p = h.split('.')
        if (p.size <= 2) return h
        val two = p.takeLast(2).joinToString(".")
        return if (p[p.size - 2].length <= 3 && p.last().length == 2) p.takeLast(3).joinToString(".") else two
    }

    fun enabledOn(pageUrl: String?): Boolean {
        if (!Reglages.blocagePub) return false
        val h = host(pageUrl) ?: return true
        return Reglages.sitesSansBlocage.none { h == it || h.endsWith(".$it") }
    }

    fun shouldBlock(url: String, pageUrl: String?): Boolean {
        if (!enabledOn(pageUrl)) return false
        val h = host(url) ?: return false
        val pageHost = host(pageUrl)
        val thirdParty = pageHost == null || baseDomain(h) != baseDomain(pageHost)
        if (!url.startsWith("http")) return false
        // le site lui-même n'est jamais bloqué pour ses propres pages essentielles
        if (!thirdParty && h == pageHost && url.endsWith(".html")) return false
        // domaine bloqué (ou un de ses parents)
        var d = h
        var blocked = false
        while (true) {
            if (allowedDomains.contains(d)) return false
            if (blockedDomains.contains(d)) { blocked = true; break }
            domainPaths[d]?.let { list ->
                val path = url.substringAfter(d, "")
                if (list.any { (!it.thirdPartyOnly || thirdParty) && it.appliesOn(pageHost) && (it.matches(path) || it.matches(url)) }) { blocked = true }
            }
            if (blocked) break
            val dot = d.indexOf('.')
            if (dot < 0 || d.indexOf('.', dot + 1) < 0) break
            d = d.substring(dot + 1)
        }
        if (!blocked) {
            val g = generic
            blocked = g.any { (!it.thirdPartyOnly || thirdParty) && it.appliesOn(pageHost) && it.matches(url) }
        }
        if (blocked && exceptions.any { it.appliesOn(pageHost) && it.matches(url) }) blocked = false
        if (blocked) compter()
        return blocked
    }

    /** Domaine (ou un de ses parents) présent dans les listes de blocage : sert au contrôle des redirections. */
    fun domaineBloque(url: String): Boolean {
        if (!Reglages.blocagePub) return false
        var d = host(url) ?: return false
        while (true) {
            if (allowedDomains.contains(d)) return false
            if (blockedDomains.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot < 0 || d.indexOf('.', dot + 1) < 0) return false
            d = d.substring(dot + 1)
        }
    }

    private fun compter() {
        enAttente++
        if (enAttente >= 20) enregistrerCompteur()
    }

    fun enregistrerCompteur() {
        if (enAttente > 0) { Reglages.totalBloques = Reglages.totalBloques + enAttente; enAttente = 0 }
    }

    /** Total affiché (enregistré + en attente d'écriture). */
    val totalBloques: Long get() = Reglages.totalBloques + enAttente

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-cache"), ByteArrayInputStream(ByteArray(0)))

    // ---------- Masquage d'éléments ----------

    /** CSS qui masque les éléments indésirables sur ce site (listes + choix du lecteur). */
    fun cosmeticCss(pageUrl: String?): String {
        val h = host(pageUrl) ?: return ""
        val selectors = LinkedHashSet<String>()
        if (enabledOn(pageUrl)) {
            selectors += BUILTIN_HIDE
            cosmetic["*"]?.let { selectors += it }
            var d = h
            while (true) {
                cosmetic[d]?.let { selectors += it }
                val dot = d.indexOf('.')
                if (dot < 0) break
                d = d.substring(dot + 1)
            }
            cosmeticExceptions.entries.filter { h == it.key || h.endsWith(".${it.key}") }.forEach { selectors -= it.value }
        }
        selectors += UserHiding.selectors(h)
        if (selectors.isEmpty()) return ""
        return selectors.chunked(400).joinToString("\n") { chunk -> chunk.joinToString(",\n") + " { display: none !important; }" }
    }

    /**
     * Éléments masqués à la main par le lecteur (mode « Masquer un élément », comme sur Brave).
     */
    object UserHiding {
        private fun all(): JSONObject = runCatching { JSONObject(Reglages.texte("masques", "{}")) }.getOrDefault(JSONObject())

        fun selectors(host: String): List<String> {
            val o = all()
            val out = ArrayList<String>()
            for (k in o.keys()) if (host == k || host.endsWith(".$k")) {
                val a = o.getJSONArray(k)
                for (i in 0 until a.length()) out += a.getString(i)
            }
            return out
        }

        fun add(host: String, selector: String) {
            val key = host.removePrefix("www.").removePrefix("m.")
            val o = all()
            val a = o.optJSONArray(key) ?: org.json.JSONArray()
            for (i in 0 until a.length()) if (a.getString(i) == selector) return
            a.put(selector)
            o.put(key, a)
            Reglages.ecrire("masques", o.toString())
        }

        fun remove(host: String, selector: String) {
            val o = all()
            val a = o.optJSONArray(host) ?: return
            val kept = org.json.JSONArray()
            for (i in 0 until a.length()) if (a.getString(i) != selector) kept.put(a.getString(i))
            if (kept.length() == 0) o.remove(host) else o.put(host, kept)
            Reglages.ecrire("masques", o.toString())
        }

        fun clear(host: String) {
            val o = all(); o.remove(host); Reglages.ecrire("masques", o.toString())
        }

        /** Éléments masqués qui s'appliquent à ce site : (clé du site, sélecteur). */
        fun entries(host: String): List<Pair<String, String>> {
            val o = all()
            val out = ArrayList<Pair<String, String>>()
            for (k in o.keys()) if (host == k || host.endsWith(".$k")) {
                val a = o.getJSONArray(k)
                for (i in 0 until a.length()) out += k to a.getString(i)
            }
            return out
        }

        fun sites(): List<Pair<String, Int>> {
            val o = all()
            return o.keys().asSequence().map { it to (o.optJSONArray(it)?.length() ?: 0) }.toList()
        }
    }
}

/** Fragments de sélecteurs génériques jugés sûrs (on n'applique pas toutes les règles génériques). */
private val GENERIC_HIDE_KEEP = listOf(
    "adsbygoogle", "google_ads", "div-gpt-ad", "ad-slot", "ad_slot", "adslot", "AdSense", "taboola", "outbrain",
    "ad-banner", "ad_banner", "banner-ad", "sponsored-", "-sponsor", "ad-container", "ad_container", "advertisement"
)

/** Masquage intégré : emplacements publicitaires courants. */
private val BUILTIN_HIDE = listOf(
    "ins.adsbygoogle", "[id^=\"google_ads_iframe\"]", "[id^=\"div-gpt-ad\"]", "iframe[src*=\"doubleclick.net\"]",
    "iframe[src*=\"googlesyndication\"]", "div[id^=\"taboola\"]", "div[class*=\"taboola\"]", "div[class*=\"outbrain\"]",
    ".adsbox", ".ad-banner", ".ad_banner", "#ad_container", ".ad-container",
    "[class*=\"_ad_\"]", ".ad-ins", ".m-ad", "div[id^=\"div-gpt\"]", "iframe[src*=\"adsterra\"]",
    "iframe[src*=\"exoclick\"]", "iframe[src*=\"popads\"]", "a[href*=\"/redirect?ad\"]"
)

/** Liste intégrée de domaines de publicité et de pistage : active dès l'installation. */
private val BUILTIN_DOMAINS: Set<String> = """
doubleclick.net googlesyndication.com googleadservices.com google-analytics.com googletagmanager.com googletagservices.com
adservice.google.com pagead2.googlesyndication.com analytics.google.com stats.g.doubleclick.net fundingchoicesmessages.google.com
imasdk.googleapis.com amazon-adsystem.com aax.amazon-adsystem.com adnxs.com adsrvr.org adform.net adroll.com advertising.com
criteo.com criteo.net taboola.com outbrain.com pubmatic.com rubiconproject.com openx.net casalemedia.com indexexchange.com
smartadserver.com sharethrough.com teads.tv triplelift.com 33across.com yieldmo.com media.net contextweb.com
bidswitch.net sovrn.com lijit.com gumgum.com districtm.io emxdgt.com rhythmone.com spotxchange.com spotx.tv
springserve.com undertone.com yahoo.com.ads ads.yahoo.com adtech.de adtechus.com zedo.com revcontent.com mgid.com
propellerads.com popads.net popcash.net adsterra.com exoclick.com juicyads.com trafficjunky.net hilltopads.net
adcash.com clickadu.com onclickads.net onclkds.com admaven.com a-ads.com coinzilla.com bitmedia.io adskeeper.com
scorecardresearch.com quantserve.com quantcount.com chartbeat.com chartbeat.net comscore.com nielsen.com imrworldwide.com
hotjar.com hotjar.io mouseflow.com fullstory.com crazyegg.com luckyorange.com inspectlet.com clarity.ms
mixpanel.com segment.io segment.com amplitude.com heap.io heapanalytics.com kissmetrics.com optimizely.com
newrelic.com nr-data.net bugsnag.com sentry-cdn.com
connect.facebook.net facebook.com.tr pixel.facebook.com an.facebook.com ads.linkedin.com px.ads.linkedin.com snap.licdn.com
analytics.twitter.com ads-twitter.com static.ads-twitter.com analytics.tiktok.com ads.tiktok.com ads.pinterest.com ct.pinterest.com
bat.bing.com ads.microsoft.com yandex.ru.metrika mc.yandex.ru an.yandex.ru adfox.ru
moatads.com moatpixel.com doubleverify.com adsafeprotected.com iasds01.com integralads.com
bluekai.com krxd.net exelator.com demdex.net everesttech.net omtrdc.net 2o7.net
rlcdn.com tapad.com agkn.com adsymptotic.com crwdcntrl.net lotame.com eyeota.net mathtag.com
turn.com mookie1.com sitescout.com simpli.fi semasio.net zemanta.com
liadm.com liveintent.com id5-sync.com prebid.org cookielaw.org onetrust.com cookiebot.com
quantcast.com quantcast.mgr.consensu.org consensu.org trustarc.com truste.com
adhigh.net adkernel.com adpushup.com adthrive.com cafemedia.com mediavine.com ezoic.net ezoic.com ezojs.com
venatus.com venatusmedia.com nitropay.com freestar.com freestar.io pubfig.js publift.com fuseplatform.net
playwire.com setupad.com snigelweb.com primis.tech connatix.com vidazoo.com aniview.com
infolinks.com kontera.com vibrantmedia.com intellitxt.com skimlinks.com skimresources.com viglink.com
zergnet.com dianomi.com nativo.com plista.com ligatus.com adblade.com content.ad
pushwoosh.com onesignal.com pushcrew.com subscribers.com pushengage.com izooto.com
adtelligent.com adlightning.com confiant-integrations.net clean.gg geoedge.be
cdn.adsafeprotected.com static.adsafeprotected.com pixel.adsafeprotected.com
tags.bluekai.com ml314.com bounceexchange.com bouncex.net wunderkind.co
tsyndicate.com trafficstars.com realsrv.com galaksion.com monetag.com richads.com evadav.com clickaine.com
popmyads.com ad-maven.com bidgear.com adspyglass.com trafficfactory.biz trafficforce.com adxpremium.services
propellerclick.com propu.sh pushails.com notix.io pushprofit.net rollerads.com zeropark.com
""".trim().split(Regex("\\s+")).filter { it.contains('.') }.toSet()
