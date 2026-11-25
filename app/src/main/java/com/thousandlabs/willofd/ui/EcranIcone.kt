package com.thousandlabs.willofd.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.IconeDuMoment
import com.thousandlabs.willofd.IconeDuMoment.Icone
import com.thousandlabs.willofd.IconeDuMoment.Plage
import com.thousandlabs.willofd.donnees.Reglages

@Composable
private fun Vignette(icone: Icone, taille: Dp, choisie: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(taille)
            .clip(RoundedCornerShape(taille * 0.24f))
            .background(Color(0xFF0F1B3D))
            .border(
                if (choisie) 2.dp else 1.dp,
                if (choisie) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(taille * 0.24f)
            )
    ) {
        Image(painterResource(icone.dessin), contentDescription = icone.nom, modifier = Modifier.size(taille * 1.3f))
    }
}

private fun heure(h: Int) = "$h h"

/** Icône de l'application : fixe, ou selon un emploi du temps choisi plage par plage. */
@Composable
fun EcranIcone(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    Reglages.version.collectAsState().value
    val plages = IconeDuMoment.plages
    val fixe = IconeDuMoment.fixe
    var choixHeure by remember { mutableStateOf<Int?>(null) }
    var choixIcone by remember { mutableStateOf<Int?>(null) }
    var confirmer by remember { mutableStateOf(false) }

    EcranSecondaire("Icône de l'application", onRetour) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icone.entries.forEach { ic ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                        Vignette(ic, 64.dp, choisie = ic == IconeDuMoment.voulue())
                        Spacer(Modifier.height(6.dp))
                        Text(ic.nom, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }

            TitreSection("Affichage")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { IconeDuMoment.fixe = -1 }
                    .padding(horizontal = 12.dp)
            ) {
                RadioButton(selected = fixe < 0, onClick = { IconeDuMoment.fixe = -1 })
                Column {
                    Text("Selon l'heure")
                    Text("D'après l'emploi du temps ci-dessous", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icone.entries.forEach { ic ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { IconeDuMoment.fixe = ic.ordinal }
                        .padding(horizontal = 12.dp)
                ) {
                    RadioButton(selected = fixe == ic.ordinal, onClick = { IconeDuMoment.fixe = ic.ordinal })
                    Text("Toujours : ${ic.nom}")
                }
            }

            if (fixe < 0) {
                TitreSection("Emploi du temps")
                Text(
                    "Choisissez l'heure de début de chaque plage et l'icône affichée : elles se succèdent dans cet ordre au fil de la journée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                plages.forEachIndexed { i, p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp)
                    ) {
                        OutlinedButton(onClick = { choixHeure = i }) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("De ${heure(p.debut)} à ${heure(IconeDuMoment.fin(plages, i))}")
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { choixIcone = i }
                                .padding(4.dp)
                        ) {
                            Vignette(p.icone, 40.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p.icone.nom, style = MaterialTheme.typography.bodySmall)
                        }
                        if (plages.size > 1) {
                            IconButton(onClick = { IconeDuMoment.plages = plages.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Retirer cette plage")
                            }
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (plages.size < 8) {
                        TextButton(onClick = {
                            val libres = (0..23).filter { h -> plages.none { it.debut == h } }
                            val apres = libres.firstOrNull { it > plages.last().debut } ?: libres.firstOrNull()
                            if (apres != null) IconeDuMoment.plages = plages + Plage(apres, Icone.entries[plages.size % 4])
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ajouter une plage")
                        }
                    }
                    TextButton(onClick = { IconeDuMoment.reinitialiser() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Par défaut")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            CarteInfo(
                Icons.Outlined.Schedule,
                "Pour ne jamais interrompre votre navigation, l'icône change seulement quand Will of D est fermée (balayée des applications récentes). Les téléchargements continuent normalement.",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (IconeDuMoment.aChanger(contexte)) {
                Button(onClick = { confirmer = true }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Appliquer maintenant")
                }
            }
        }
    }

    choixHeure?.let { i ->
        val p = plages.getOrNull(i)
        if (p == null) { choixHeure = null; return@let }
        AlertDialog(
            onDismissRequest = { choixHeure = null },
            title = { Text("Début de la plage") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (0..23).forEach { h ->
                        val prise = plages.any { it.debut == h && it !== p }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !prise) {
                                    IconeDuMoment.plages = plages.map { if (it === p) it.copy(debut = h) else it }
                                    choixHeure = null
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = h == p.debut, enabled = !prise, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                heure(h) + if (prise) " (déjà utilisée)" else "",
                                color = if (prise) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choixHeure = null }) { Text("Fermer") } }
        )
    }

    choixIcone?.let { i ->
        val p = plages.getOrNull(i)
        if (p == null) { choixIcone = null; return@let }
        AlertDialog(
            onDismissRequest = { choixIcone = null },
            title = { Text("Icône de ${heure(p.debut)} à ${heure(IconeDuMoment.fin(plages, i))}") },
            text = {
                Column {
                    Icone.entries.forEach { ic ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    IconeDuMoment.plages = plages.map { if (it === p) it.copy(icone = ic) else it }
                                    choixIcone = null
                                }
                                .padding(6.dp)
                        ) {
                            Vignette(ic, 44.dp, choisie = ic == p.icone)
                            Spacer(Modifier.width(12.dp))
                            Text(ic.nom)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choixIcone = null }) { Text("Fermer") } }
        )
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Changer l'icône maintenant ?") },
            text = { Text("Android ferme l'application pendant le changement d'icône. Vos onglets seront rouverts et les téléchargements continuent.") },
            confirmButton = {
                Button(onClick = {
                    confirmer = false
                    com.thousandlabs.willofd.navigateur.Navigateur.enregistrer()
                    IconeDuMoment.appliquer(contexte.applicationContext)
                    (contexte as? Activity)?.finishAndRemoveTask()
                }) { Text("Appliquer") }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } }
        )
    }
}
