package com.thousandlabs.willofd.donnees

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Réglages de Will of D. Tout reste sur le téléphone ; chaque option est activable et désactivable. */
object Reglages {
    private lateinit var sp: SharedPreferences
    @Volatile private var pret = false

    /** Incrémenté à chaque changement, pour que l'interface se mette à jour. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (pret) return
        sp = context.applicationContext.getSharedPreferences("willofd", Context.MODE_PRIVATE)
        pret = true
    }

    private fun bump() { _version.value = _version.value + 1 }

    fun bool(cle: String, def: Boolean) = sp.getBoolean(cle, def)
    fun ecrire(cle: String, v: Boolean) { sp.edit().putBoolean(cle, v).apply(); bump() }
    fun entier(cle: String, def: Int) = sp.getInt(cle, def)
    fun ecrire(cle: String, v: Int) { sp.edit().putInt(cle, v).apply(); bump() }
    fun long(cle: String, def: Long) = sp.getLong(cle, def)
    fun ecrire(cle: String, v: Long) { sp.edit().putLong(cle, v).apply(); bump() }
    fun texte(cle: String, def: String) = sp.getString(cle, def) ?: def
    fun ecrire(cle: String, v: String) { sp.edit().putString(cle, v).apply(); bump() }
    fun retirer(cle: String) { sp.edit().remove(cle).apply(); bump() }

    fun liste(cle: String): List<String> = runCatching {
        val a = JSONArray(sp.getString(cle, "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    fun ecrireListe(cle: String, l: List<String>) { sp.edit().putString(cle, JSONArray(l).toString()).apply(); bump() }

    // ---------------------------------------------------------------- Protection
    var blocagePub: Boolean get() = bool("pub", true); set(v) = ecrire("pub", v)
    var antiPistage: Boolean get() = bool("pistage", true); set(v) = ecrire("pistage", v)
    var httpsSeulement: Boolean get() = bool("https", true); set(v) = ecrire("https", v)
    var navigationSure: Boolean get() = bool("safe", true); set(v) = ecrire("safe", v)
    var bloquerRedirections: Boolean get() = bool("redir", true); set(v) = ecrire("redir", v)
    var bloquerFenetres: Boolean get() = bool("popups", true); set(v) = ecrire("popups", v)
    var antiCalques: Boolean get() = bool("calques", true); set(v) = ecrire("calques", v)
    var bloquerApplis: Boolean get() = bool("applis", true); set(v) = ecrire("applis", v)
    var majListesAuto: Boolean get() = bool("listes_auto", true); set(v) = ecrire("listes_auto", v)
    var sitesSansBlocage: List<String> get() = liste("sans_blocage"); set(v) = ecrireListe("sans_blocage", v)

    // ---------------------------------------------------------------- Navigation
    var modeOrdinateur: Boolean get() = bool("ordi", false); set(v) = ecrire("ordi", v)
    var moteur: Int get() = entier("moteur", 0); set(v) = ecrire("moteur", v)
    var historiqueActif: Boolean get() = bool("historique", true); set(v) = ecrire("historique", v)
    var effacerALaSortie: Boolean get() = bool("effacer_sortie", false); set(v) = ecrire("effacer_sortie", v)
    var masquerBarres: Boolean get() = bool("barres", true); set(v) = ecrire("barres", v)

    // ---------------------------------------------------------------- Téléchargements
    /** 1 = en série ; 2 à 4 = en parallèle. */
    var simultanes: Int get() = entier("simultanes", 2).coerceIn(1, 4); set(v) = ecrire("simultanes", v.coerceIn(1, 4))
    /** 0 = meilleure qualité, 1 = 720p maximum, 2 = 480p maximum. */
    var qualite: Int get() = entier("qualite", 0); set(v) = ecrire("qualite", v)
    var wifiSeulement: Boolean get() = bool("wifi", false); set(v) = ecrire("wifi", v)
    var contournement: Boolean get() = bool("contournement", true); set(v) = ecrire("contournement", v)
    var extractionPage: Boolean get() = bool("extraction", true); set(v) = ecrire("extraction", v)
    var petitesImages: Boolean get() = bool("petites", false); set(v) = ecrire("petites", v)
    var sensPages: String get() = texte("sens", "vertical"); set(v) = ecrire("sens", v)

    // ---------------------------------------------------------------- Sécurité
    var verrouActif: Boolean get() = bool("verrou", false); set(v) = ecrire("verrou", v)
    /** 0 = seulement à l'ouverture de l'application, 1 = après 5 minutes, 2 = après 30 minutes en arrière-plan. */
    var verrouDelai: Int get() = entier("verrou_delai", 0); set(v) = ecrire("verrou_delai", v)
    var biometrie: Boolean get() = bool("biometrie", false); set(v) = ecrire("biometrie", v)
    var capturesBloquees: Boolean get() = bool("captures", false); set(v) = ecrire("captures", v)

    // ---------------------------------------------------------------- Divers
    var nouveautesVues: String get() = texte("nouveautes_vues", ""); set(v) = ecrire("nouveautes_vues", v)
    var totalBloques: Long get() = long("total_bloques", 0L); set(v) = ecrire("total_bloques", v)

    /** Dernière partie envoyée pour chaque chapitre (continuité des suites téléchargées plus tard). */
    fun prochainePartie(cleElement: String): Int {
        val o = runCatching { JSONObject(texte("parties", "{}")) }.getOrDefault(JSONObject())
        val p = o.optInt(cleElement, 0) + 1
        o.put(cleElement, p)
        ecrire("parties", o.toString())
        return p
    }
}
