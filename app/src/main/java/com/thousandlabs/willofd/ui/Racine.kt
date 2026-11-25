package com.thousandlabs.willofd.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.thousandlabs.willofd.BuildConfig
import com.thousandlabs.willofd.MainActivity
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.navigateur.PleinEcran
import com.thousandlabs.willofd.securite.Verrou

sealed interface Destination {
    data object Onglets : Destination
    data object Telechargements : Destination
    data object Favoris : Destination
    data object Historique : Destination
    data object Reglages : Destination
    data object MisesAJour : Destination
    data object Guide : Destination
    data object Nouveautes : Destination
    data object APropos : Destination
    data object Quitter : Destination
}

@Composable
fun Racine(activite: MainActivity) {
    val verrouille by Verrou.verrouille.collectAsState()
    val pile = remember { mutableStateListOf<Destination>() }

    fun aller(d: Destination) {
        if (d == Destination.Quitter) {
            activite.moveTaskToBack(true)
            return
        }
        if (pile.lastOrNull() != d) pile.add(d)
    }

    fun retour() { if (pile.isNotEmpty()) pile.removeAt(pile.lastIndex) }

    // Écran demandé par une notification.
    val demande by activite.ecranDemande
    LaunchedEffect(demande) {
        if (demande == "telechargements") {
            pile.clear()
            pile.add(Destination.Telechargements)
            activite.ecranDemande.value = null
        }
    }

    // Notifications de progression : autorisation demandée une seule fois.
    val demandeNotifs = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Reglages.bool("notifs_demandees", false) &&
            ContextCompat.checkSelfPermission(activite, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Reglages.ecrire("notifs_demandees", true)
            demandeNotifs.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Écran « Nouveautés » affiché une fois après chaque mise à jour.
        if (Reglages.nouveautesVues != BuildConfig.VERSION_NAME) {
            val premiere = Reglages.nouveautesVues.isEmpty()
            Reglages.nouveautesVues = BuildConfig.VERSION_NAME
            pile.add(if (premiere) Destination.Guide else Destination.Nouveautes)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (verrouille) {
            EcranVerrou()
            return@Surface
        }
        BackHandler(enabled = pile.isNotEmpty()) { retour() }
        when (pile.lastOrNull()) {
            null -> EcranNavigateur(aller = ::aller)
            Destination.Onglets -> EcranOnglets(onRetour = ::retour)
            Destination.Telechargements -> EcranTelechargements(onRetour = ::retour)
            Destination.Favoris -> EcranCarnet(historique = false, onRetour = ::retour)
            Destination.Historique -> EcranCarnet(historique = true, onRetour = ::retour)
            Destination.Reglages -> EcranReglages(onRetour = ::retour, aller = ::aller)
            Destination.MisesAJour -> EcranMisesAJour(onRetour = ::retour)
            Destination.Guide -> EcranGuide(onRetour = ::retour)
            Destination.Nouveautes -> EcranNouveautes(onRetour = ::retour)
            Destination.APropos -> EcranAPropos(onRetour = ::retour)
            Destination.Quitter -> Unit
        }
        Navigateur.pleinEcran?.let { PleinEcranVideo(activite, it) }
    }
}

/** Lecture en plein écran demandée par le lecteur d'une page. */
@Composable
private fun PleinEcranVideo(activite: Activity, p: PleinEcran) {
    BackHandler { p.rappel.onCustomViewHidden(); Navigateur.pleinEcran = null }
    DisposableEffect(p) {
        val fenetre = activite.window
        val ancienne = activite.requestedOrientation
        activite.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val c = WindowCompat.getInsetsController(fenetre, fenetre.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activite.requestedOrientation = ancienne
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    (p.vue.parent as? ViewGroup)?.removeView(p.vue)
                    addView(p.vue, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
