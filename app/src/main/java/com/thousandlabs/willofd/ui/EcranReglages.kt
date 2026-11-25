package com.thousandlabs.willofd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Https
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thousandlabs.willofd.BuildConfig
import com.thousandlabs.willofd.MainActivity
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.MOTEURS
import com.thousandlabs.willofd.protection.Bloqueur
import com.thousandlabs.willofd.protection.Confidentialite
import com.thousandlabs.willofd.securite.Biometrie
import com.thousandlabs.willofd.securite.Verrou
import com.thousandlabs.willofd.telechargement.Telechargeur
import com.thousandlabs.willofd.util.Formats

private class Choix(val titre: String, val options: List<String>, val actuel: Int, val choisir: (Int) -> Unit)

@Composable
fun EcranReglages(onRetour: () -> Unit, aller: (Destination) -> Unit) {
    val contexte = LocalContext.current
    Reglages.version.collectAsState().value
    val majListes by Bloqueur.updating.collectAsState()
    val regles by Bloqueur.ruleCount.collectAsState()
    var choix by remember { mutableStateOf<Choix?>(null) }
    var definirCode by remember { mutableStateOf(false) }
    var retirerCode by remember { mutableStateOf(false) }
    var confirmationEffacer by remember { mutableStateOf(false) }

    EcranSecondaire("Réglages", onRetour) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            TitreSection("Protection")
            LigneInterrupteur(Icons.Outlined.Shield, "Bloquer les publicités et les pisteurs", "Listes EasyList, EasyPrivacy, Peter Lowe et liste intégrée", Reglages.blocagePub) { Reglages.blocagePub = it }
            LigneInterrupteur(Icons.Outlined.TrackChanges, "Bloquer les redirections non sollicitées", "Changement de site sans geste de votre part : bloqué, avec possibilité d'autoriser", Reglages.bloquerRedirections) { Reglages.bloquerRedirections = it }
            LigneInterrupteur(Icons.Outlined.PictureInPicture, "Bloquer les fenêtres surgissantes", "Une nouvelle fenêtre du même site reste possible après un appui", Reglages.bloquerFenetres) { Reglages.bloquerFenetres = it }
            LigneInterrupteur(Icons.Outlined.Layers, "Retirer les calques publicitaires invisibles", "Calques transparents posés sur les lecteurs pour détourner vos appuis", Reglages.antiCalques) { Reglages.antiCalques = it }
            LigneInterrupteur(Icons.Outlined.Apps, "Bloquer l'ouverture d'autres applications", "Liens « intent », boutiques d'applications", Reglages.bloquerApplis) { Reglages.bloquerApplis = it }
            LigneInterrupteur(Icons.Outlined.Policy, "Anti-pistage", "Signal « ne pas me pister », cookies tiers refusés, paramètres utm retirés", Reglages.antiPistage) { Reglages.antiPistage = it }
            LigneInterrupteur(Icons.Outlined.Https, "Connexion sécurisée automatique (HTTPS)", null, Reglages.httpsSeulement) { Reglages.httpsSeulement = it }
            LigneInterrupteur(Icons.Outlined.Security, "Navigation sécurisée", "Alerte avant les sites dangereux connus (service Safe Browsing du système)", Reglages.navigationSure) { Reglages.navigationSure = it }
            LigneInterrupteur(Icons.Outlined.Refresh, "Mise à jour automatique des listes", "Une fois par semaine, depuis les sources officielles des listes", Reglages.majListesAuto) { Reglages.majListesAuto = it }
            LigneAction(
                Icons.Outlined.Refresh,
                if (majListes) "Mise à jour des listes…" else "Mettre à jour les listes maintenant",
                "$regles règles · " + (Bloqueur.lastUpdate().takeIf { it > 0 }?.let { "mises à jour le " + Formats.date(it) } ?: "liste intégrée uniquement")
            ) { Bloqueur.updateLists() }
            val sans = Reglages.sitesSansBlocage
            if (sans.isNotEmpty()) {
                LigneAction(Icons.Outlined.Block, "Sites sans bloqueur", sans.joinToString(", ")) {
                    choix = Choix("Réactiver le bloqueur sur…", sans, -1) { i ->
                        Reglages.sitesSansBlocage = sans.filterIndexed { j, _ -> j != i }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            TitreSection("Navigation")
            LigneInterrupteur(Icons.Outlined.DesktopWindows, "Mode ordinateur par défaut", "Pour les nouveaux onglets", Reglages.modeOrdinateur) { Reglages.modeOrdinateur = it }
            LigneAction(Icons.Outlined.Search, "Moteur de recherche", MOTEURS.getOrElse(Reglages.moteur) { MOTEURS[0] }.first) {
                choix = Choix("Moteur de recherche", MOTEURS.map { it.first }, Reglages.moteur) { Reglages.moteur = it }
            }
            LigneInterrupteur(Icons.Outlined.History, "Historique", "Jamais enregistré dans les onglets privés", Reglages.historiqueActif) { Reglages.historiqueActif = it }
            LigneInterrupteur(Icons.Outlined.CleaningServices, "Effacer les données à la fermeture", "Cookies, cache et stockage des sites", Reglages.effacerALaSortie) { Reglages.effacerALaSortie = it }
            LigneAction(Icons.Outlined.CleaningServices, "Effacer maintenant cookies et cache", "Vous serez déconnecté des sites") { confirmationEffacer = true }
            val masques = Bloqueur.UserHiding.sites()
            if (masques.isNotEmpty()) {
                LigneAction(Icons.Outlined.VisibilityOff, "Éléments masqués", "${masques.sumOf { it.second }} élément(s) sur ${masques.size} site(s) · toucher pour gérer") {
                    choix = Choix("Réafficher les éléments masqués de…", masques.map { "${it.first} (${it.second})" }, -1) { i ->
                        Bloqueur.UserHiding.clear(masques[i].first)
                        Reglages.ecrire("masques_maj", System.currentTimeMillis())
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            TitreSection("Téléchargements")
            val modes = listOf("En série (un à la fois)", "2 en parallèle", "3 en parallèle", "4 en parallèle")
            LigneAction(Icons.Outlined.Queue, "Mode de téléchargement", modes[Reglages.simultanes - 1]) {
                choix = Choix("Mode de téléchargement", modes, Reglages.simultanes - 1) { Reglages.simultanes = it + 1; Telechargeur.planifier() }
            }
            val qualites = listOf("Meilleure qualité disponible", "720p au maximum", "480p au maximum (économe)")
            LigneAction(Icons.Outlined.HighQuality, "Qualité des flux vidéo", qualites[Reglages.qualite.coerceIn(0, 2)]) {
                choix = Choix("Qualité des flux vidéo", qualites, Reglages.qualite) { Reglages.qualite = it }
            }
            LigneInterrupteur(Icons.Outlined.Wifi, "Wi-Fi uniquement", "Les téléchargements attendent le Wi-Fi", Reglages.wifiSeulement) { Reglages.wifiSeulement = it; Telechargeur.planifier() }
            LigneInterrupteur(Icons.Outlined.LockOpen, "Déblocage ciblé des sites récalcitrants", "Rétablit clic droit et sélection, retire les calques posés sur les médias, relance les lecteurs", Reglages.contournement) { Reglages.contournement = it }
            LigneInterrupteur(Icons.Outlined.WebAsset, "Récupération depuis la page", "Si le site bloque les pages, elles sont reprises depuis la page ouverte", Reglages.extractionPage) { Reglages.extractionPage = it }
            LigneInterrupteur(Icons.Outlined.PhotoLibrary, "Inclure les petites images", "Utile pour les planches de petite taille ; risque d'inclure logos et bannières", Reglages.petitesImages) { Reglages.petitesImages = it }
            val sens = listOf("vertical" to "Webtoon (vertical)", "droite-gauche" to "Manga (droite à gauche)", "gauche-droite" to "BD (gauche à droite)")
            LigneAction(Icons.AutoMirrored.Outlined.MenuBook, "Sens de lecture proposé", sens.firstOrNull { it.first == Reglages.sensPages }?.second ?: sens[0].second) {
                choix = Choix("Sens de lecture proposé", sens.map { it.second }, sens.indexOfFirst { it.first == Reglages.sensPages }) {
                    Reglages.sensPages = sens[it].first
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            TitreSection("Sécurité")
            LigneInterrupteur(Icons.Outlined.Lock, "Verrouillage par code", if (Verrou.actif) "Activé" else "Désactivé : l'application s'ouvre directement", Verrou.actif) { on ->
                if (on) definirCode = true else retirerCode = true
            }
            if (Verrou.actif) {
                val delais = listOf("Seulement à l'ouverture de l'application", "Après 5 minutes d'absence", "Après 30 minutes d'absence")
                LigneAction(Icons.Outlined.LockClock, "Quand verrouiller", delais[Reglages.verrouDelai.coerceIn(0, 2)] + " (jamais en quittant)") {
                    choix = Choix("Quand verrouiller", delais, Reglages.verrouDelai) { Reglages.verrouDelai = it }
                }
                LigneInterrupteur(
                    Icons.Outlined.Fingerprint,
                    "Empreinte",
                    if (Biometrie.disponible(contexte)) null else "Aucune empreinte enregistrée sur ce téléphone",
                    Reglages.biometrie,
                    actif = Biometrie.disponible(contexte)
                ) { Reglages.biometrie = it }
            }
            LigneInterrupteur(Icons.Outlined.Screenshot, "Bloquer les captures d'écran", "Masque aussi l'aperçu dans les applications récentes", Reglages.capturesBloquees) {
                Reglages.capturesBloquees = it
                (contexte as? MainActivity)?.appliquerCaptures()
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            TitreSection("Application")
            LigneAction(Icons.Outlined.SystemUpdate, "Mises à jour", "Version installée : ${BuildConfig.VERSION_NAME}") { aller(Destination.MisesAJour) }
            LigneAction(Icons.AutoMirrored.Outlined.HelpOutline, "Comment faire", "Guide d'utilisation") { aller(Destination.Guide) }
            LigneAction(Icons.Outlined.NewReleases, "Nouveautés", "Ce qui a changé dans cette version") { aller(Destination.Nouveautes) }
            LigneAction(Icons.Outlined.Info, "À propos", "Thousand Labs Games") { aller(Destination.APropos) }
            LigneAction(Icons.AutoMirrored.Outlined.OpenInNew, "Dépôt officiel sur GitHub", URL_DEPOT) { ouvrirLien(contexte, URL_DEPOT) }
        }
    }

    choix?.let { c ->
        AlertDialog(
            onDismissRequest = { choix = null },
            title = { Text(c.titre) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    c.options.forEachIndexed { i, o ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { c.choisir(i); choix = null }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = i == c.actuel, onClick = { c.choisir(i); choix = null })
                            Spacer(Modifier.width(8.dp))
                            Text(o)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choix = null }) { Text("Fermer") } }
        )
    }

    if (confirmationEffacer) {
        AlertDialog(
            onDismissRequest = { confirmationEffacer = false },
            title = { Text("Effacer cookies et cache ?") },
            text = { Text("Vous serez déconnecté des sites. Les favoris, l'historique et les téléchargements sont conservés.") },
            confirmButton = {
                Button(onClick = {
                    Confidentialite.effacerDonnees(contexte.applicationContext)
                    confirmationEffacer = false
                }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmationEffacer = false }) { Text("Annuler") } }
        )
    }

    if (definirCode) DialogueNouveauCode(onFini = { definirCode = false })
    if (retirerCode) {
        Dialog(onDismissRequest = { retirerCode = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var erreur by remember { mutableStateOf<String?>(null) }
            ClavierCode(
                titre = "Désactiver le verrouillage",
                sousTitre = "Saisissez votre code actuel",
                erreur = erreur,
                biometrie = false,
                onBiometrie = {},
                onValider = { code ->
                    if (Verrou.verifier(code)) {
                        Verrou.desactiver()
                        retirerCode = false
                    } else erreur = if (Verrou.attente() > 0) "Trop d'essais. Patientez un instant." else "Code incorrect."
                }
            )
        }
    }
}

@Composable
private fun DialogueNouveauCode(onFini: () -> Unit) {
    var premier by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onFini, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ClavierCode(
            titre = if (premier == null) "Choisissez un code" else "Confirmez le code",
            sousTitre = "4 à 8 chiffres",
            erreur = erreur,
            biometrie = false,
            onBiometrie = {},
            onValider = { code ->
                val p = premier
                if (p == null) {
                    premier = code
                    erreur = null
                } else if (p == code) {
                    Verrou.definir(code)
                    onFini()
                } else {
                    premier = null
                    erreur = "Les deux codes sont différents. Recommencez."
                }
            }
        )
    }
}
