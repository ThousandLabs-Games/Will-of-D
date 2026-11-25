package com.thousandlabs.willofd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.donnees.Carnet
import com.thousandlabs.willofd.donnees.Lien
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.ui.theme.Nuit
import com.thousandlabs.willofd.util.Formats

@Composable
fun EcranOnglets(onRetour: () -> Unit) {
    EcranSecondaire("Onglets (${Navigateur.onglets.size})", onRetour, actions = {
        TextButton(onClick = { Navigateur.fermerTout(); onRetour() }) { Text("Tout fermer") }
    }) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(Navigateur.onglets, key = { _, o -> o.id }) { i, o ->
                    val actuel = i == Navigateur.courant
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (o.prive) Nuit.Prive else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                if (actuel) 2.dp else 0.dp,
                                if (actuel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { Navigateur.choisir(i); onRetour() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                            val ic = o.icone
                            if (ic != null) Image(ic, contentDescription = null, modifier = Modifier.size(16.dp))
                            else Icon(if (o.prive) Icons.Outlined.VpnLock else Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (o.estAccueil) "Accueil" else o.titre.ifBlank { o.hote },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { Navigateur.fermer(o) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Fermer l'onglet", modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            val a = o.apercu
                            if (a != null && !o.estAccueil) {
                                Image(a, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(
                                    if (o.prive) "Onglet privé" else if (o.estAccueil) "Page d'accueil" else o.hote,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                Button(onClick = { Navigateur.nouvelOnglet(); onRetour() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nouvel onglet")
                }
                OutlinedButton(onClick = { Navigateur.nouvelOnglet(prive = true); onRetour() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.VpnLock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Onglet privé")
                }
            }
        }
    }
}

/** Favoris ou historique. */
@Composable
fun EcranCarnet(historique: Boolean, onRetour: () -> Unit) {
    val favoris by Carnet.favoris.collectAsState()
    val hist by Carnet.historique.collectAsState()
    val liste: List<Lien> = if (historique) hist else favoris
    EcranSecondaire(if (historique) "Historique" else "Favoris", onRetour, actions = {
        if (historique && hist.isNotEmpty()) {
            IconButton(onClick = { Carnet.effacerHistorique() }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Effacer l'historique") }
        }
    }) { marges ->
        if (liste.isEmpty()) {
            Box(Modifier.padding(marges)) {
                MessageVide(
                    if (historique) Icons.Outlined.History else Icons.Outlined.Bookmark,
                    if (historique) "Historique vide" else "Aucun favori",
                    if (historique) "Les pages visitées hors onglets privés apparaissent ici." else "Ajoutez une page depuis le menu du navigateur."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(marges)
            ) {
                items(liste, key = { it.url + it.date }) { l ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (Navigateur.onglet.estAccueil) Navigateur.ouvrir(l.url) else Navigateur.nouvelOnglet(l.url)
                                onRetour()
                            }
                            .padding(start = 20.dp, top = 10.dp, bottom = 10.dp)
                    ) {
                        Icon(if (historique) Icons.Outlined.History else Icons.Outlined.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.titre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (historique) Formats.date(l.date) + " · " + l.url else l.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            if (historique) Carnet.retirerHistorique(l.url) else Carnet.basculerFavori(l.titre, l.url)
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Retirer") }
                    }
                }
            }
        }
    }
}
