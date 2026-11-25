package com.thousandlabs.willofd

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.thousandlabs.willofd.donnees.Reglages
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Les quatre icônes de Will of D et leur emploi du temps : pour chaque plage horaire,
 * vous choisissez l'icône affichée (et donc l'ordre dans lequel elles se succèdent).
 */
object IconeDuMoment {

    enum class Icone(val alias: String, val nom: String, val dessin: Int) {
        D_GRAVE("IconeA", "D gravé", R.drawable.ic_launcher_foreground),
        BOUSSOLE("IconeB", "Boussole-bouclier", R.drawable.ic_fg_b),
        STELE("IconeC", "Stèle à la flamme", R.drawable.ic_fg_c),
        LANTERNE("IconeD", "Lanterne de navire", R.drawable.ic_fg_d)
    }

    /** Une plage commence à [debut] heures et dure jusqu'au début de la plage suivante. */
    data class Plage(val debut: Int, val icone: Icone)

    private val PAR_DEFAUT = listOf(
        Plage(6, Icone.D_GRAVE),
        Plage(12, Icone.BOUSSOLE),
        Plage(18, Icone.STELE),
        Plage(21, Icone.LANTERNE)
    )

    /** -1 : selon l'heure ; sinon, numéro de l'icône gardée en permanence. */
    var fixe: Int
        get() = Reglages.entier("icone_fixe", -1)
        set(v) = Reglages.ecrire("icone_fixe", v)

    var plages: List<Plage>
        get() = runCatching {
            val a = JSONArray(Reglages.texte("icone_plages", ""))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Plage(o.getInt("debut").coerceIn(0, 23), Icone.entries[o.getInt("icone").coerceIn(0, 3)])
            }.distinctBy { it.debut }.sortedBy { it.debut }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: PAR_DEFAUT
        set(v) {
            val a = JSONArray()
            v.distinctBy { it.debut }.sortedBy { it.debut }.forEach { a.put(JSONObject().put("debut", it.debut).put("icone", it.icone.ordinal)) }
            Reglages.ecrire("icone_plages", a.toString())
        }

    fun reinitialiser() = Reglages.retirer("icone_plages")

    /** Heure de fin d'une plage (début de la suivante, en tournant sur 24 h). */
    fun fin(l: List<Plage>, i: Int): Int = l[(i + 1) % l.size].debut

    fun pourHeure(heure: Int): Icone {
        val l = plages
        return (l.lastOrNull { it.debut <= heure } ?: l.last()).icone
    }

    fun voulue(): Icone = Icone.entries.getOrNull(fixe) ?: pourHeure(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    fun actuelle(context: Context): Icone? {
        val pm = context.packageManager
        return Icone.entries.firstOrNull { ic ->
            val etat = pm.getComponentEnabledSetting(ComponentName(context, "${context.packageName}.${ic.alias}"))
            etat == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (etat == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && ic == Icone.D_GRAVE)
        }
    }

    fun aChanger(context: Context): Boolean = runCatching { actuelle(context) != voulue() }.getOrDefault(false)

    /** Active la bonne icône d'abord, puis éteint les autres : il en reste toujours une. */
    fun appliquer(context: Context) {
        val cible = voulue()
        if (actuelle(context) == cible) return
        val pm = context.packageManager
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${cible.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            Icone.entries.filter { it != cible }.forEach {
                pm.setComponentEnabledSetting(
                    ComponentName(context, "${context.packageName}.${it.alias}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun minutesAvantProchainePlage(): Long {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val debuts = plages.map { it.debut }
        val suivant = debuts.firstOrNull { it > h } ?: (debuts.first() + 24)
        return ((suivant - h) * 60L - m).coerceAtLeast(1)
    }

    /** Vérification toutes les 15 minutes et réveil au prochain changement de plage. */
    fun programmer(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "icone_du_moment", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TravailIcone>(15, TimeUnit.MINUTES).build()
        )
        programmerProchainePlage(context)
    }

    fun programmerProchainePlage(context: Context) {
        val req = OneTimeWorkRequestBuilder<TravailIcone>()
            .setInitialDelay(minutesAvantProchainePlage() + 1, TimeUnit.MINUTES)
            .setInputData(workDataOf("plage" to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("icone_plage", ExistingWorkPolicy.REPLACE, req)
    }

    /** Après la sortie de l'application : le changement se fait une minute plus tard, application fermée. */
    fun programmerBientot(context: Context) {
        val req = OneTimeWorkRequestBuilder<TravailIcone>().setInitialDelay(1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("icone_bientot", ExistingWorkPolicy.REPLACE, req)
    }
}

class TravailIcone(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Changer d'icône pendant que l'écran de Will of D est ouvert fermerait l'application : on attend.
        if (!MainActivity.vivante) IconeDuMoment.appliquer(applicationContext)
        if (inputData.getBoolean("plage", false)) runCatching { IconeDuMoment.programmerProchainePlage(applicationContext) }
        return Result.success()
    }
}
