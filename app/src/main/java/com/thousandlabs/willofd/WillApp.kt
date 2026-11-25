package com.thousandlabs.willofd

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.thousandlabs.willofd.depot.Depot
import com.thousandlabs.willofd.donnees.Carnet
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.protection.Bloqueur
import com.thousandlabs.willofd.protection.Confidentialite
import com.thousandlabs.willofd.securite.Verrou
import com.thousandlabs.willofd.telechargement.Notifications
import com.thousandlabs.willofd.telechargement.Telechargeur

class WillApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Reglages.init(this)
        Depot.init(this)
        Carnet.init(this)
        Confidentialite.init(this)
        Bloqueur.init(this)
        Notifications.init(this)
        Verrou.init(this)
        Telechargeur.init(this)
        runCatching { IconeDuMoment.programmer(this) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_STOP -> {
                        Verrou.surSortie()
                        Bloqueur.enregistrerCompteur()
                    }
                    Lifecycle.Event.ON_START -> {
                        Verrou.surRetour()
                        Depot.rafraichir()
                    }
                    else -> Unit
                }
            }
        )
    }
}
