package com.thousandlabs.willofd

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.protection.Confidentialite
import com.thousandlabs.willofd.ui.Racine
import com.thousandlabs.willofd.ui.theme.ThemeWill

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ECRAN = "ecran"
    }

    /** Écran demandé depuis une notification (téléchargements). */
    val ecranDemande = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliquerCaptures()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        traiter(intent)
        setContent {
            ThemeWill {
                Racine(this)
            }
        }
    }

    fun appliquerCaptures() {
        if (Reglages.capturesBloquees) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traiter(intent)
    }

    private fun traiter(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_ECRAN)?.let { ecranDemande.value = it }
        val lien = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { t ->
                Regex("https?://\\S+").find(t)?.value ?: t
            }
            else -> null
        }
        if (!lien.isNullOrBlank()) {
            Navigateur.restaurer()
            val url = Navigateur.versUrl(lien) ?: return
            if (Navigateur.onglet.estAccueil) Navigateur.ouvrir(url) else Navigateur.nouvelOnglet(url)
        }
    }

    override fun onDestroy() {
        if (isFinishing && Reglages.effacerALaSortie) Confidentialite.effacerDonnees(applicationContext)
        super.onDestroy()
    }
}
