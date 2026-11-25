package com.thousandlabs.willofd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.BuildConfig
import com.thousandlabs.willofd.maj.MisesAJour
import com.thousandlabs.willofd.maj.Signature
import com.thousandlabs.willofd.maj.VersionDistante
import com.thousandlabs.willofd.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface EtatMaj {
    data object Recherche : EtatMaj
    data object AJour : EtatMaj
    data class Disponible(val v: VersionDistante) : EtatMaj
    data class Telechargement(val v: VersionDistante, val progression: Float) : EtatMaj
    data class Pret(val v: VersionDistante, val apk: File) : EtatMaj
    data class Erreur(val message: String) : EtatMaj
}

/** Mises à jour depuis le dépôt officiel GitHub, avec vérification de la signature Thousand Labs. */
@Composable
fun EcranMisesAJour(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<EtatMaj>(EtatMaj.Recherche) }
    var essai by remember { mutableStateOf(0) }

    LaunchedEffect(essai) {
        etat = EtatMaj.Recherche
        etat = withContext(Dispatchers.IO) {
            runCatching { MisesAJour.derniereVersion() }.fold(
                onSuccess = { v -> if (MisesAJour.plusRecente(v.version, BuildConfig.VERSION_NAME)) EtatMaj.Disponible(v) else EtatMaj.AJour },
                onFailure = { EtatMaj.Erreur(it.message ?: "Recherche impossible. Vérifiez la connexion.") }
            )
        }
    }

    fun telecharger(v: VersionDistante) {
        etat = EtatMaj.Telechargement(v, 0f)
        portee.launch {
            etat = withContext(Dispatchers.IO) {
                runCatching { MisesAJour.telecharger(contexte, v) { p -> etat = EtatMaj.Telechargement(v, p) } }.fold(
                    onSuccess = { apk ->
                        when (MisesAJour.verifierSignature(contexte, apk)) {
                            Signature.DIFFERENTE -> {
                                apk.delete()
                                EtatMaj.Erreur("Le fichier téléchargé n'est pas signé par Thousand Labs Games : installation bloquée.")
                            }
                            else -> EtatMaj.Pret(v, apk)
                        }
                    },
                    onFailure = { EtatMaj.Erreur(it.message ?: "Téléchargement interrompu.") }
                )
            }
        }
    }

    EcranSecondaire("Mises à jour", onRetour) { marges ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Version installée : ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            CarteInfo(Icons.Outlined.Verified, "Will of D se met à jour uniquement depuis son dépôt officiel. La signature est vérifiée avant l'installation : vos réglages, favoris et téléchargements sont conservés.")

            when (val e = etat) {
                EtatMaj.Recherche -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Recherche de la dernière version…")
                }
                EtatMaj.AJour -> Text("Will of D est à jour.", style = MaterialTheme.typography.titleSmall)
                is EtatMaj.Disponible -> {
                    Text("Version ${e.v.version} disponible", style = MaterialTheme.typography.titleMedium)
                    if (e.v.notes.isNotBlank()) {
                        Text(e.v.notes.replace("## ", "").replace("**", ""), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { telecharger(e.v) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Télécharger (${Formats.taille(e.v.taille)})")
                    }
                }
                is EtatMaj.Telechargement -> {
                    Text("Téléchargement de la version ${e.v.version}…")
                    LinearProgressIndicator(progress = { e.progression }, modifier = Modifier.fillMaxWidth())
                }
                is EtatMaj.Pret -> {
                    Text("Version ${e.v.version} téléchargée et vérifiée.", style = MaterialTheme.typography.titleSmall)
                    Button(
                        onClick = {
                            if (!MisesAJour.peutInstaller(contexte)) MisesAJour.ouvrirAutorisationInstallation(contexte)
                            else MisesAJour.installer(contexte, e.apk)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (MisesAJour.peutInstaller(contexte)) "Installer" else "Autoriser l'installation")
                    }
                }
                is EtatMaj.Erreur -> Text(e.message, color = MaterialTheme.colorScheme.error)
            }

            if (etat !is EtatMaj.Recherche && etat !is EtatMaj.Telechargement) {
                OutlinedButton(onClick = { essai++ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rechercher à nouveau")
                }
            }
            TextButton(onClick = { ouvrirLien(contexte, MisesAJour.URL_RELEASES) }) { Text("Voir toutes les versions sur GitHub") }
        }
    }
}
