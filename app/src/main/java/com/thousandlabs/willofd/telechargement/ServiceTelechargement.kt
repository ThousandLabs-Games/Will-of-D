package com.thousandlabs.willofd.telechargement

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.thousandlabs.willofd.MainActivity
import com.thousandlabs.willofd.R
import com.thousandlabs.willofd.util.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Notifications : progression de chaque téléchargement, éléments prêts pour Rio Poneglyph, échecs. */
object Notifications {
    const val CANAL_COURS = "telechargements"
    const val CANAL_FIN = "termines"
    const val ID_SERVICE = 7100

    fun init(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_COURS, "Téléchargements en cours", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progression de chaque téléchargement"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_FIN, "Téléchargements terminés", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Éléments prêts pour Rio Poneglyph et téléchargements en échec"
        })
    }

    fun autorisees(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ouvrir(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ECRAN, "telechargements")
        return PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun idPour(t: Tache) = 7200 + (t.id.hashCode() and 0x7FFF)

    fun resume(context: Context, enCours: Int, attente: Int) =
        NotificationCompat.Builder(context, CANAL_COURS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Will of D")
            .setContentText(
                buildString {
                    append(if (enCours > 0) "$enCours téléchargement(s) en cours" else "Préparation")
                    if (attente > 0) append(" · $attente en attente")
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup("wod")
            .setGroupSummary(true)
            .setContentIntent(ouvrir(context))
            .build()

    fun progression(context: Context, t: Tache) =
        NotificationCompat.Builder(context, CANAL_COURS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t.titre)
            .setContentText(
                listOfNotNull(
                    t.detail.takeIf { it.isNotBlank() },
                    t.octets.takeIf { it > 0 }?.let { Formats.taille(it) }
                ).joinToString(" · ")
            )
            .setProgress(100, (t.progression.coerceIn(0f, 1f) * 100).toInt(), t.progression < 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup("wod")
            .setContentIntent(ouvrir(context))
            .build()

    @SuppressLint("MissingPermission")
    fun termine(context: Context, titre: String, pages: Boolean) {
        if (!autorisees(context)) return
        val texte = (if (pages) "Chapitre" else "Vidéo") + " prêt pour Rio Poneglyph."
        val n = NotificationCompat.Builder(context, CANAL_FIN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(titre)
            .setContentText(texte)
            .setAutoCancel(true)
            .setContentIntent(ouvrir(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(titre.hashCode(), n) }
    }

    @SuppressLint("MissingPermission")
    fun echec(context: Context, titre: String, message: String) {
        if (!autorisees(context)) return
        val n = NotificationCompat.Builder(context, CANAL_FIN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Échec : $titre")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(ouvrir(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(titre.hashCode(), n) }
    }
}

/**
 * Service de premier plan : garde les téléchargements actifs écran éteint
 * (indispensable sous HiOS, qui coupe vite les applications en arrière-plan).
 */
class ServiceTelechargement : Service() {

    companion object {
        fun demarrer(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ServiceTelechargement::class.java)) }
        }
    }

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suivi: Job? = null
    private var verrouVeille: PowerManager.WakeLock? = null
    private var verrouWifi: WifiManager.WifiLock? = null
    private val affichees = HashSet<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.resume(this, 0, 0)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        val ok = runCatching { ServiceCompat.startForeground(this, Notifications.ID_SERVICE, n, type) }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquerir()
        if (suivi == null) suivi = portee.launch { Telechargeur.taches.collectLatest { mettreAJour(it) } }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun acquerir() {
        if (verrouVeille == null) {
            verrouVeille = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WillOfD:telechargements").apply { setReferenceCounted(false); acquire() }
        }
        if (verrouWifi == null) {
            @Suppress("DEPRECATION")
            verrouWifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WillOfD:wifi").apply { setReferenceCounted(false); acquire() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mettreAJour(taches: List<Tache>) {
        val enCours = taches.filter { it.etat == EtatTache.EN_COURS }
        val attente = taches.count { it.etat == EtatTache.ATTENTE }
        val nm = NotificationManagerCompat.from(this)
        if (enCours.isEmpty() && attente == 0) {
            affichees.forEach { nm.cancel(it) }
            affichees.clear()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        runCatching { nm.notify(Notifications.ID_SERVICE, Notifications.resume(this, enCours.size, attente)) }
        if (!Notifications.autorisees(this)) return
        val actuelles = HashSet<Int>()
        enCours.forEach { t ->
            val id = Notifications.idPour(t)
            actuelles.add(id)
            runCatching { nm.notify(id, Notifications.progression(this, t)) }
        }
        (affichees - actuelles).forEach { nm.cancel(it) }
        affichees.clear()
        affichees.addAll(actuelles)
    }

    override fun onTimeout(startId: Int) { stopSelf() }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        portee.cancel()
        runCatching { verrouVeille?.release() }
        runCatching { verrouWifi?.release() }
        verrouVeille = null
        verrouWifi = null
        val nm = NotificationManagerCompat.from(this)
        affichees.forEach { nm.cancel(it) }
        super.onDestroy()
    }
}
