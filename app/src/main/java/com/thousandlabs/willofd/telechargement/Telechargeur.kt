package com.thousandlabs.willofd.telechargement

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.thousandlabs.willofd.depot.Depot
import com.thousandlabs.willofd.donnees.Reglages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * File des téléchargements : en série (un à la fois) ou en parallèle (jusqu'à 4),
 * reprise après interruption, publication atomique dans le dépôt une fois terminé.
 */
object Telechargeur {
    private lateinit var appli: Context
    private lateinit var fichierFile: File
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val travaux = HashMap<String, Job>()
    private val verrou = Any()
    @Volatile private var pret = false

    private val _taches = MutableStateFlow<List<Tache>>(emptyList())
    val taches: StateFlow<List<Tache>> = _taches.asStateFlow()

    /** Raison d'une mise en pause demandée (pour ne pas la confondre avec une annulation). */
    private val pauses = HashSet<String>()
    private val annulations = HashSet<String>()
    private val derniereMaj = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (pret) return
        appli = context.applicationContext
        fichierFile = File(appli.filesDir, "taches.json")
        Depot.init(appli)
        // Après un arrêt du téléphone ou de l'application, les tâches interrompues reprennent.
        _taches.value = lire().map { if (it.etat == EtatTache.EN_COURS) it.copy(etat = EtatTache.ATTENTE, detail = "Reprise") else it }
        pret = true
        runCatching {
            val cm = appli.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { planifier() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { planifier() }
            })
        }
        planifier()
    }

    private fun lire(): List<Tache> = runCatching {
        val a = JSONArray(fichierFile.readText())
        List(a.length()) { Tache.depuisJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun sauver() {
        val a = JSONArray()
        _taches.value.forEach { a.put(it.versJson()) }
        runCatching {
            val tmp = File(fichierFile.path + ".tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(fichierFile)
        }
    }

    private fun modifier(id: String, sauvegarder: Boolean = true, f: (Tache) -> Tache) {
        synchronized(verrou) {
            _taches.value = _taches.value.map { if (it.id == id) f(it) else it }
            if (sauvegarder) sauver()
        }
    }

    fun nouvelId(): String = UUID.randomUUID().toString()

    fun ajouter(t: Tache) {
        synchronized(verrou) {
            _taches.value = _taches.value + t
            sauver()
        }
        ServiceTelechargement.demarrer(appli)
        planifier()
    }

    private fun wifiDisponible(): Boolean = runCatching {
        val cm = appli.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)

    /** Lance autant de tâches que le mode choisi l'autorise (1 en série, jusqu'à 4 en parallèle). */
    fun planifier() {
        if (!pret) return
        val aLancer = ArrayList<Tache>()
        synchronized(verrou) {
            val wifi = !Reglages.wifiSeulement || wifiDisponible()
            if (!wifi) {
                _taches.value = _taches.value.map { if (it.etat == EtatTache.ATTENTE) it.copy(detail = "En attente du Wi-Fi") else it }
                return
            }
            var libres = Reglages.simultanes - travaux.size
            for (t in _taches.value.sortedBy { it.cree }) {
                if (libres <= 0) break
                if (t.etat == EtatTache.ATTENTE && t.id !in travaux) {
                    aLancer.add(t)
                    libres--
                }
            }
            aLancer.forEach { t ->
                _taches.value = _taches.value.map { if (it.id == t.id) it.copy(etat = EtatTache.EN_COURS, detail = "Démarrage") else it }
                travaux[t.id] = portee.launch { executer(t.id) }
            }
            if (aLancer.isNotEmpty()) sauver()
        }
        if (aLancer.isNotEmpty()) ServiceTelechargement.demarrer(appli)
    }

    private suspend fun executer(id: String) {
        val t = _taches.value.firstOrNull { it.id == id } ?: return
        val dossier = Depot.dossierEnCours(id)
        val avance: Avancement = { fraction, octets, texte ->
            val maintenant = System.currentTimeMillis()
            val dernier = derniereMaj[id] ?: 0L
            if (maintenant - dernier > 350 || fraction == 1f) {
                derniereMaj[id] = maintenant
                modifier(id, sauvegarder = false) {
                    it.copy(progression = fraction ?: -1f, octets = octets, detail = texte)
                }
            }
        }
        try {
            val fichiers: List<String> = if (t.pages) {
                MoteurPages.telecharger(t, dossier, avance)
            } else when (t.genreMedia) {
                GenreMedia.HLS -> listOf(MoteurHls.telecharger(t, dossier, avance))
                GenreMedia.DASH -> throw RefusSite("Les flux DASH ne sont pas pris en charge : lancez la lecture, puis choisissez un autre flux s'il apparaît.")
                else -> listOf(MoteurDirect.telecharger(t, dossier, avance))
            }
            Depot.publier(
                id = id,
                type = if (t.pages) "pages" else "video",
                titre = t.titre,
                serie = t.serie,
                cleSerie = t.cleSerie,
                cleElement = t.cleElement,
                numero = t.numero,
                partie = t.partie,
                sens = t.sens,
                fichiers = fichiers
            )
            synchronized(verrou) {
                _taches.value = _taches.value.filter { it.id != id }
                sauver()
            }
            Notifications.termine(appli, t.titre, t.pages)
        } catch (e: CancellationException) {
            val pause = synchronized(verrou) { pauses.remove(id) }
            val annule = synchronized(verrou) { annulations.remove(id) }
            if (annule) {
                Depot.effacerEnCours(id)
            } else if (pause) {
                modifier(id) { it.copy(etat = EtatTache.PAUSE, detail = "En pause") }
            }
            throw e
        } catch (e: Throwable) {
            val message = when (e) {
                is RefusSite -> e.message ?: "Le site a refusé le téléchargement."
                is java.net.UnknownHostException -> "Pas de connexion Internet."
                is java.net.SocketTimeoutException -> "Le site ne répond plus. Réessayez plus tard."
                is java.io.IOException -> "Connexion interrompue (${e.message ?: "erreur réseau"}). Touchez Réessayer : le téléchargement reprendra."
                else -> "Échec : ${e.message ?: e.javaClass.simpleName}"
            }
            modifier(id) { it.copy(etat = EtatTache.ECHEC, detail = message) }
            Notifications.echec(appli, t.titre, message)
        } finally {
            synchronized(verrou) { travaux.remove(id) }
            derniereMaj.remove(id)
            planifier()
        }
    }

    fun pause(id: String) {
        val job = synchronized(verrou) {
            pauses.add(id)
            travaux[id]
        }
        if (job != null) job.cancel() else {
            synchronized(verrou) { pauses.remove(id) }
            modifier(id) { if (it.etat == EtatTache.ATTENTE) it.copy(etat = EtatTache.PAUSE, detail = "En pause") else it }
        }
    }

    fun reprendre(id: String) {
        modifier(id) { it.copy(etat = EtatTache.ATTENTE, detail = "En attente") }
        ServiceTelechargement.demarrer(appli)
        planifier()
    }

    fun annuler(id: String) {
        val job = synchronized(verrou) {
            annulations.add(id)
            travaux[id]
        }
        synchronized(verrou) {
            _taches.value = _taches.value.filter { it.id != id }
            sauver()
        }
        if (job != null) job.cancel() else {
            synchronized(verrou) { annulations.remove(id) }
            Depot.effacerEnCours(id)
        }
    }

    fun toutReprendre() {
        synchronized(verrou) {
            _taches.value = _taches.value.map {
                if (it.etat == EtatTache.PAUSE || it.etat == EtatTache.ECHEC) it.copy(etat = EtatTache.ATTENTE, detail = "En attente") else it
            }
            sauver()
        }
        ServiceTelechargement.demarrer(appli)
        planifier()
    }

    val actives: Int get() = _taches.value.count { it.etat == EtatTache.EN_COURS || it.etat == EtatTache.ATTENTE }
}
