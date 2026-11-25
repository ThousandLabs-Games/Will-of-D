package com.thousandlabs.willofd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.navigateur.Onglet
import com.thousandlabs.willofd.navigateur.ResultatPages
import com.thousandlabs.willofd.protection.Confidentialite
import com.thousandlabs.willofd.telechargement.Analyse
import com.thousandlabs.willofd.telechargement.GenreMedia
import com.thousandlabs.willofd.telechargement.MediaDetecte
import com.thousandlabs.willofd.telechargement.Tache
import com.thousandlabs.willofd.telechargement.Telechargeur
import com.thousandlabs.willofd.util.Formats
import kotlinx.coroutines.delay

/** Ce que l'on prépare au téléchargement : une vidéo ou les planches d'un chapitre. */
private sealed interface Preparation {
    data class Video(val media: MediaDetecte) : Preparation
    data class Pages(val resultat: ResultatPages) : Preparation
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleMedias(o: Onglet, onFermer: () -> Unit, aller: (Destination) -> Unit) {
    var analyse by remember { mutableStateOf<ResultatPages?>(null) }
    var enAnalyse by remember { mutableStateOf(false) }
    var deblocage by remember { mutableStateOf(false) }
    var analyseFaite by remember { mutableStateOf(false) }
    var preparation by remember { mutableStateOf<Preparation?>(Navigateur.propositionDirecte?.let { Preparation.Video(it) }) }

    fun analyser() {
        enAnalyse = true
        Navigateur.analyserPages(o) { r ->
            analyse = r
            enAnalyse = false
            analyseFaite = true
        }
    }

    LaunchedEffect(deblocage) {
        if (!deblocage) return@LaunchedEffect
        Navigateur.contourner(o) {}
        delay(3000)
        analyser()
        deblocage = false
    }

    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("Télécharger depuis cette page", style = MaterialTheme.typography.titleLarge)
            Text(
                "Les téléchargements restent dans la zone privée de Will of D, invisibles pour la Galerie et les autres applications, jusqu'à leur récupération par Rio Poneglyph.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (o.estAccueil) {
                CarteInfo(Icons.Outlined.Info, "Ouvrez d'abord la page d'une vidéo ou d'un chapitre.")
            } else {
                if (o.drm) {
                    CarteInfo(Icons.Outlined.Block, "Ce site protège ses vidéos (DRM) : leur téléchargement est impossible. Les pages restent téléchargeables.")
                    Spacer(Modifier.height(12.dp))
                }

                // ------------------------------------------------ Vidéos
                Text("Vidéos repérées", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                val videos = o.medias.toList()
                if (videos.isEmpty()) {
                    Text(
                        if (o.fluxLecteur) "Le lecteur utilise un flux : lancez la lecture quelques secondes, il apparaîtra ici."
                        else "Aucune vidéo repérée. Lancez la lecture de la vidéo, puis rouvrez ce panneau.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                videos.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.libelle, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    m.hote.ifBlank { null },
                                    m.nomFichier.ifBlank { null },
                                    m.taille.takeIf { it > 0 }?.let { Formats.taille(it) }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { preparation = Preparation.Video(m) },
                            enabled = m.genre != GenreMedia.DASH && !o.drm
                        ) { Text("Télécharger") }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                // ------------------------------------------------ Pages
                Text("Pages d'un chapitre", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Faites défiler le chapitre jusqu'en bas pour charger toutes les planches, puis lancez la recherche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val r = analyse
                when {
                    enAnalyse || deblocage -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (deblocage) "Déblocage de la page…" else "Recherche des planches…")
                    }
                    r != null && r.images.isNotEmpty() -> {
                        Text(
                            "${r.images.size} planche(s) trouvée(s)" + if (r.ignorees > 0) " · ${r.ignorees} image(s) annexes écartées" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { preparation = Preparation.Pages(r) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Télécharger ce chapitre")
                        }
                        TextButton(onClick = { analyser() }) { Text("Relancer la recherche") }
                    }
                    else -> {
                        if (analyseFaite) {
                            Text(
                                "Aucune planche trouvée sur cette page.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedButton(onClick = { analyser() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rechercher les planches de cette page")
                        }
                    }
                }

                // ------------------------------------------------ Repli
                if (Reglages.contournement) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))
                    Text("Le site résiste ?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Déblocage ciblé : clic droit et sélection rétablis, calques posés sur les médias retirés, images paresseuses chargées et lecteur relancé pour révéler le flux. Si rien n'apparaît ensuite, mieux vaut changer de site.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { deblocage = true }, enabled = !deblocage, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Débloquer la page et chercher à nouveau")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { onFermer(); aller(Destination.Telechargements) }) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voir les téléchargements")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    preparation?.let { p ->
        DialoguePreparation(o, p, onAnnuler = { preparation = null }) {
            preparation = null
            Navigateur.signaler("Ajouté aux téléchargements", "Voir") { aller(Destination.Telechargements) }
            onFermer()
        }
    }
}

@Composable
private fun DialoguePreparation(o: Onglet, p: Preparation, onAnnuler: () -> Unit, onLance: () -> Unit) {
    val pages = p is Preparation.Pages
    val titrePage = when (p) {
        is Preparation.Video -> p.media.titrePage.ifBlank { o.titre }
        is Preparation.Pages -> p.resultat.titre.ifBlank { o.titre }
    }
    val urlPage = when (p) {
        is Preparation.Video -> o.pageUrl ?: o.url
        is Preparation.Pages -> p.resultat.url.ifBlank { o.url }
    }
    val proposition = remember(p) { if (pages) Analyse.pourPages(titrePage, urlPage) else Analyse.pourVideo(titrePage, urlPage) }
    var titre by remember(p) { mutableStateOf(proposition.titre) }
    var serie by remember(p) { mutableStateOf(proposition.serie) }
    var numero by remember(p) { mutableStateOf(if (proposition.numero >= 0) Analyse.formater(proposition.numero) else "") }
    var sens by remember(p) { mutableStateOf(Reglages.sensPages) }

    AlertDialog(
        onDismissRequest = onAnnuler,
        icon = { Icon(if (pages) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.PlayCircle, contentDescription = null) },
        title = { Text(if (pages) "Télécharger ce chapitre" else "Télécharger cette vidéo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = titre, onValueChange = { titre = it }, label = { Text("Titre") }, singleLine = true)
                OutlinedTextField(value = serie, onValueChange = { serie = it }, label = { Text("Série (facultatif)") }, singleLine = true)
                OutlinedTextField(
                    value = numero,
                    onValueChange = { v -> numero = v.filter { it.isDigit() || it == ',' || it == '.' }.take(8) },
                    label = { Text(if (pages) "Numéro du chapitre" else "Numéro de l'épisode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (pages) {
                    Text("Sens de lecture", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("vertical" to "Webtoon", "droite-gauche" to "Manga", "gauche-droite" to "BD").forEach { (v, l) ->
                            FilterChip(selected = sens == v, onClick = { sens = v }, label = { Text(l) })
                        }
                    }
                }
                Text(
                    "Avec la série et le numéro, les suites téléchargées plus tard (chapitre suivant, page 2…) s'enchaînent ou se regroupent proprement dans Rio Poneglyph.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = numero.replace(',', '.').toDoubleOrNull() ?: -1.0
                val s = serie.trim()
                val nom = titre.trim().ifBlank { if (n >= 0) (if (pages) "Chapitre " else "Épisode ") + Analyse.formater(n) else "Sans titre" }
                val cleElement = Analyse.cleElement(pages, s, n, urlPage)
                if (pages) Reglages.sensPages = sens
                val commun = Tache(
                    id = Telechargeur.nouvelId(),
                    pages = pages,
                    titre = nom,
                    serie = s,
                    numero = n,
                    partie = if (pages) Reglages.prochainePartie(cleElement) else 0,
                    cleSerie = Analyse.cleSerie(s),
                    cleElement = cleElement,
                    sens = if (pages) sens else "",
                    agent = Confidentialite.agent(o.ordinateur),
                    pageUrl = urlPage,
                    ongletId = o.id
                )
                val t = when (p) {
                    is Preparation.Video -> commun.copy(
                        url = p.media.url,
                        genreMedia = p.media.genre,
                        referer = p.media.referer.ifBlank { urlPage },
                        origine = p.media.origine.let { o2 ->
                            if (o2.startsWith("http") && o2.count { it == '/' } > 2) {
                                runCatching { java.net.URI(o2).let { u -> "${u.scheme}://${u.host}" } }.getOrDefault("")
                            } else o2
                        }
                    )
                    is Preparation.Pages -> commun.copy(images = p.resultat.images.map { it.url })
                }
                Telechargeur.ajouter(t)
                onLance()
            }) { Text("Télécharger") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
