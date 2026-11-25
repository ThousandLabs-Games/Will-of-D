package com.thousandlabs.willofd.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.depot.Depot
import com.thousandlabs.willofd.depot.ElementDepot
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.telechargement.Analyse
import com.thousandlabs.willofd.telechargement.EtatTache
import com.thousandlabs.willofd.telechargement.Tache
import com.thousandlabs.willofd.telechargement.Telechargeur
import com.thousandlabs.willofd.util.Formats

private const val PAQUET_RIO = "com.thousandlabs.rioponeglyph"

private fun ouvrirRio(context: Context): Boolean {
    val i = context.packageManager.getLaunchIntentForPackage(PAQUET_RIO) ?: return false
    context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

/** Liste auto-synchronisée : elle reflète l'état réel de la zone privée et de la file. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EcranTelechargements(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val taches by Telechargeur.taches.collectAsState()
    val depot by Depot.elements.collectAsState()
    Reglages.version.collectAsState().value
    var aRetirer by remember { mutableStateOf<ElementDepot?>(null) }
    val rioInstallee = remember { runCatching { contexte.packageManager.getPackageInfo(PAQUET_RIO, 0) }.isSuccess }

    EcranSecondaire("Téléchargements", onRetour) { marges ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            item {
                Column {
                    Text("Mode de téléchargement", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val choix = listOf(1 to "En série", 2 to "2 en parallèle", 3 to "3", 4 to "4")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        choix.forEachIndexed { i, (v, l) ->
                            SegmentedButton(
                                selected = Reglages.simultanes == v,
                                onClick = { Reglages.simultanes = v; Telechargeur.planifier() },
                                shape = SegmentedButtonDefaults.itemShape(i, choix.size)
                            ) { Text(l, maxLines = 1) }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("En cours et en attente (${taches.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (taches.any { it.etat == EtatTache.PAUSE || it.etat == EtatTache.ECHEC }) {
                        TextButton(onClick = { Telechargeur.toutReprendre() }) { Text("Tout reprendre") }
                    }
                }
            }
            if (taches.isEmpty()) {
                item {
                    Text(
                        "Aucun téléchargement en cours. Sur une page, touchez l'icône de téléchargement en bas de l'écran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(taches, key = { it.id }) { t -> LigneTache(t) }

            item {
                Text(
                    "Prêts pour Rio Poneglyph (${depot.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            item {
                CarteInfo(
                    Icons.Outlined.Security,
                    "Dans Rio Poneglyph : Importer, puis « Depuis Will of D ». Chaque élément récupéré est chiffré dans le coffre puis disparaît automatiquement d'ici. L'échange se fait uniquement sur ce téléphone."
                ) {
                    Spacer(Modifier.height(10.dp))
                    if (rioInstallee) {
                        Button(onClick = { ouvrirRio(contexte) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ouvrir Rio Poneglyph")
                        }
                    } else {
                        Text("Rio Poneglyph n'est pas installée sur ce téléphone.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { ouvrirLien(contexte, "$URL_RIO/releases") }) { Text("Obtenir Rio Poneglyph") }
                    }
                }
            }
            if (depot.isEmpty()) {
                item {
                    Text(
                        "Rien en attente : tout a été récupéré par Rio Poneglyph.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(depot, key = { "d" + it.id }) { e -> LigneDepot(e) { aRetirer = e } }
        }
    }

    aRetirer?.let { e ->
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text("Retirer de Will of D ?") },
            text = { Text("« ${e.titre} » sera supprimé définitivement, sans passer par Rio Poneglyph.") },
            confirmButton = {
                Button(onClick = { Depot.supprimer(e.id); aRetirer = null }) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun LigneTache(t: Tache) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (t.etat == EtatTache.ECHEC) Icons.Outlined.ErrorOutline else if (t.pages) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = if (t.etat == EtatTache.ECHEC) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.titre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            t.serie.ifBlank { null },
                            if (t.pages) "${t.images.size} page(s)" else null,
                            if (t.pages && t.partie > 1) "partie ${t.partie}" else null,
                            t.octets.takeIf { it > 0 }?.let { Formats.taille(it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                when (t.etat) {
                    EtatTache.EN_COURS, EtatTache.ATTENTE -> IconButton(onClick = { Telechargeur.pause(t.id) }) {
                        Icon(Icons.Outlined.Pause, contentDescription = "Mettre en pause")
                    }
                    EtatTache.PAUSE -> IconButton(onClick = { Telechargeur.reprendre(t.id) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Reprendre")
                    }
                    EtatTache.ECHEC -> IconButton(onClick = { Telechargeur.reprendre(t.id) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Réessayer")
                    }
                }
                IconButton(onClick = { Telechargeur.annuler(t.id) }) { Icon(Icons.Outlined.Close, contentDescription = "Annuler") }
            }
            if (t.etat == EtatTache.EN_COURS) {
                Spacer(Modifier.height(6.dp))
                if (t.progression < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 10.dp))
                } else {
                    LinearProgressIndicator(progress = { t.progression.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 10.dp))
                }
            }
            Text(
                t.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (t.etat == EtatTache.ECHEC) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 10.dp)
            )
        }
    }
}

@Composable
private fun LigneDepot(e: ElementDepot, onRetirer: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(
                if (e.estPages) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.titre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        e.serie.ifBlank { null },
                        if (e.estPages) "${e.fichiers.size} page(s)" else null,
                        if (e.numero >= 0 && !e.titre.contains(Analyse.formater(e.numero))) "n° ${Analyse.formater(e.numero)}" else null,
                        Formats.taille(e.taille),
                        Formats.date(e.ajoute)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            IconButton(onClick = onRetirer) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Retirer") }
        }
    }
}
