package com.thousandlabs.willofd.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thousandlabs.willofd.R
import com.thousandlabs.willofd.depot.Depot
import com.thousandlabs.willofd.donnees.Carnet
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.navigateur.Navigateur
import com.thousandlabs.willofd.navigateur.Onglet
import com.thousandlabs.willofd.protection.Bloqueur
import com.thousandlabs.willofd.telechargement.Telechargeur
import com.thousandlabs.willofd.ui.theme.Nuit
import kotlinx.coroutines.delay

/** Écran principal : navigateur à onglets, barre d'adresse inspirée d'Aloha, outils de protection. */
@Composable
fun EcranNavigateur(aller: (Destination) -> Unit) {
    val o = Navigateur.onglet
    var edition by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var protection by remember { mutableStateOf(false) }
    var medias by remember { mutableStateOf(false) }
    var recherche by remember { mutableStateOf(false) }

    LaunchedEffect(Navigateur.propositionDirecte) { if (Navigateur.propositionDirecte != null) medias = true }

    BackHandler(enabled = true) {
        when {
            edition -> edition = false
            recherche -> { recherche = false; o.vue?.clearMatches() }
            Navigateur.selection -> Navigateur.arreterSelection()
            o.vue?.canGoBack() == true && !o.estAccueil -> o.vue?.goBack()
            !o.estAccueil -> Navigateur.accueil()
            Navigateur.onglets.size > 1 -> Navigateur.fermer(o)
            else -> aller(Destination.Quitter)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (o.prive) Nuit.Prive else MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        BarreAdresse(o, edition, onEdition = { edition = it }, onProtection = { protection = true })
        if (o.progression in 1..99 && !o.estAccueil) {
            LinearProgressIndicator(
                progress = { o.progression / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        } else Spacer(Modifier.height(2.dp))

        Box(Modifier.weight(1f)) {
            if (o.estAccueil) {
                PageAccueil(onRecherche = { edition = true }, aller = aller)
            }
            if (!o.estAccueil) {
                key(o.id) {
                    val contexte = LocalContext.current
                    AndroidView(
                        factory = {
                            val w = Navigateur.vue(o, contexte)
                            (w.parent as? ViewGroup)?.removeView(w)
                            w
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (edition) {
                Suggestions(o) { edition = false }
            }
            Column(Modifier.align(Alignment.BottomCenter)) {
                Bandeau()
                if (Navigateur.selection) BarreSelection()
                if (recherche) BarreRecherche(o) { recherche = false }
            }
        }

        BarreBas(
            o = o,
            onOnglets = { aller(Destination.Onglets) },
            onMedias = { medias = true },
            onMenu = { menu = true }
        )
    }

    if (menu) FeuilleMenu(o, aller, onRecherche = { recherche = true }, onFermer = { menu = false })
    if (protection) FeuilleProtection(o) { protection = false }
    if (medias) FeuilleMedias(o, onFermer = { medias = false; Navigateur.propositionDirecte = null }, aller = aller)
}


// ====================================================================== Barre d'adresse

@Composable
private fun BarreAdresse(o: Onglet, edition: Boolean, onEdition: (Boolean) -> Unit, onProtection: () -> Unit) {
    val focus = remember { FocusRequester() }
    var saisie by remember(edition) { mutableStateOf(if (edition) o.url else "") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = !edition) { onEdition(true) }
                .padding(horizontal = 14.dp)
        ) {
            val icone = when {
                edition || o.estAccueil -> Icons.Outlined.Search
                o.url.startsWith("https://") -> Icons.Outlined.Lock
                else -> Icons.Outlined.LockOpen
            }
            Icon(
                icone,
                contentDescription = null,
                tint = if (!edition && o.url.startsWith("http://")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            if (edition) {
                TextField(
                    value = saisie,
                    onValueChange = { saisie = it },
                    singleLine = true,
                    placeholder = { Text("Rechercher ou saisir une adresse") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        Navigateur.ouvrir(saisie)
                        onEdition(false)
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                IconButton(onClick = { if (saisie.isEmpty()) onEdition(false) else saisie = "" }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Effacer")
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            o.estAccueil -> if (o.prive) "Onglet privé : rechercher ou saisir une adresse" else "Rechercher ou saisir une adresse"
                            else -> o.hote.ifBlank { o.url }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (o.estAccueil) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (!o.estAccueil) {
                    IconButton(onClick = {
                        if (o.progression in 1..99) o.vue?.stopLoading() else Navigateur.recharger(o)
                    }) {
                        Icon(
                            if (o.progression in 1..99) Icons.Outlined.Close else Icons.Outlined.Refresh,
                            contentDescription = if (o.progression in 1..99) "Arrêter" else "Recharger",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        BadgedBox(badge = {
            if (o.bloques > 0 && !o.estAccueil) Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(if (o.bloques > 99) "99+" else o.bloques.toString())
            }
        }) {
            IconButton(onClick = onProtection) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = "Protection de ce site",
                    tint = if (Bloqueur.enabledOn(o.url)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Suggestions(o: Onglet, fermer: () -> Unit) {
    val favoris by Carnet.favoris.collectAsState()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            if (!o.estAccueil) {
                val presse = LocalClipboardManager.current
                LigneAction(Icons.Outlined.ContentCopy, "Copier l'adresse de la page", o.url) {
                    presse.setText(AnnotatedString(o.url))
                    Navigateur.signaler("Adresse copiée")
                    fermer()
                }
            }
            Text(
                "Favoris",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            favoris.take(8).forEach { f ->
                LigneAction(Icons.Outlined.Bookmark, f.titre, f.url) {
                    Navigateur.ouvrir(f.url)
                    fermer()
                }
            }
        }
    }
}

// ====================================================================== Accueil

@Composable
private fun PageAccueil(onRecherche: () -> Unit, aller: (Destination) -> Unit) {
    val favoris by Carnet.favoris.collectAsState()
    val depot by Depot.elements.collectAsState()
    val taches by Telechargeur.taches.collectAsState()
    Reglages.version.collectAsState().value
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0F1B3D))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(120.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Will of D", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(
                    "Navigation protégée · ${Bloqueur.totalBloques} publicités et pisteurs bloqués",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onRecherche)
                        .padding(horizontal = 18.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Rechercher ou saisir une adresse", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val actifs = taches.size
                if (actifs > 0 || depot.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { aller(Destination.Telechargements) }) {
                        Icon(Icons.Outlined.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(
                                if (actifs > 0) "$actifs en cours" else null,
                                if (depot.isNotEmpty()) "${depot.size} prêt(s) pour Rio Poneglyph" else null
                            ).joinToString(" · ")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(favoris, key = { it.url }) { f ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { Navigateur.ouvrir(f.url) }
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Text(
                        f.titre.trim().take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(f.titre, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

// ====================================================================== Barres

@Composable
private fun BarreBas(o: Onglet, onOnglets: () -> Unit, onMedias: () -> Unit, onMenu: () -> Unit) {
    val actifs by Telechargeur.taches.collectAsState()
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 4.dp)
    ) {
        IconButton(onClick = { o.vue?.goBack() }, enabled = o.peutReculer && !o.estAccueil) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Page précédente")
        }
        IconButton(onClick = { o.vue?.goForward() }, enabled = o.peutAvancer) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Page suivante")
        }
        IconButton(onClick = { Navigateur.accueil() }) {
            Icon(Icons.Outlined.Home, contentDescription = "Accueil")
        }
        IconButton(onClick = onOnglets) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .border(1.6.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
            ) {
                Text(
                    Navigateur.onglets.size.let { if (it > 99) "∞" else it.toString() },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        BadgedBox(badge = {
            val n = o.medias.size
            if (n > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(n.toString()) }
            else if (actifs.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.secondary)
        }) {
            IconButton(onClick = onMedias) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "Télécharger depuis cette page",
                    tint = if (o.medias.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Menu")
        }
    }
}

/** Indication discrète en bas de l'écran : élément bloqué, redirection refusée… */
@Composable
private fun Bandeau() {
    val e = Navigateur.evenement
    LaunchedEffect(e?.id) {
        if (e != null) {
            delay(if (e.action != null) 6000 else 3200)
            if (Navigateur.evenement?.id == e.id) Navigateur.evenement = null
        }
    }
    AnimatedVisibility(e != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val courant = e ?: return@AnimatedVisibility
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Outlined.VpnLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(courant.texte, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            if (courant.action != null && courant.faire != null) {
                TextButton(onClick = {
                    courant.faire.invoke()
                    Navigateur.evenement = null
                }) { Text(courant.action) }
            }
        }
    }
}

@Composable
private fun BarreSelection() {
    val c = Navigateur.choix
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp)
    ) {
        Text(
            if (c == null) "Touchez l'élément à masquer sur ce site." else "Élément choisi : ${c.second.ifBlank { "sans texte" }}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { Navigateur.elargirSelection() }, enabled = c != null) {
                Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Élargir")
            }
            Button(onClick = { Navigateur.masquerChoix() }, enabled = c != null) { Text("Masquer") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { Navigateur.arreterSelection() }) { Text("Terminer") }
        }
    }
}

@Composable
private fun BarreRecherche(o: Onglet, fermer: () -> Unit) {
    var texte by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp)
    ) {
        TextField(
            value = texte,
            onValueChange = { texte = it; o.vue?.findAllAsync(it) },
            singleLine = true,
            placeholder = { Text("Rechercher dans la page") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
        )
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        IconButton(onClick = { o.vue?.findNext(false) }) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Résultat précédent") }
        IconButton(onClick = { o.vue?.findNext(true) }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Résultat suivant") }
        IconButton(onClick = { o.vue?.clearMatches(); fermer() }) { Icon(Icons.Outlined.Close, contentDescription = "Fermer") }
    }
}

// ====================================================================== Feuilles

@Composable
private fun Tuile(icone: ImageVector, libelle: String, actif: Boolean = false, enabled: Boolean = true, onClic: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClic)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (actif) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    actif -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(libelle, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMenu(o: Onglet, aller: (Destination) -> Unit, onRecherche: () -> Unit, onFermer: () -> Unit) {
    val contexte = LocalContext.current
    val favoris by Carnet.favoris.collectAsState()
    val favori = favoris.any { it.url == o.url }
    ModalBottomSheet(onDismissRequest = onFermer) {
        val tuiles: List<@Composable () -> Unit> = listOf(
            { Tuile(Icons.Outlined.Add, "Nouvel onglet") { Navigateur.nouvelOnglet(); onFermer() } },
            { Tuile(Icons.Outlined.VpnLock, "Onglet privé") { Navigateur.nouvelOnglet(prive = true); onFermer() } },
            {
                Tuile(if (favori) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (favori) "Retirer des favoris" else "Ajouter aux favoris", actif = favori, enabled = !o.estAccueil) {
                    val ajoute = Carnet.basculerFavori(o.titre, o.url)
                    Navigateur.signaler(if (ajoute) "Ajouté aux favoris" else "Retiré des favoris")
                    onFermer()
                }
            },
            { Tuile(Icons.Outlined.Bookmarks, "Favoris") { aller(Destination.Favoris); onFermer() } },
            { Tuile(Icons.Outlined.History, "Historique") { aller(Destination.Historique); onFermer() } },
            { Tuile(Icons.Outlined.DownloadForOffline, "Téléchargements") { aller(Destination.Telechargements); onFermer() } },
            {
                Tuile(if (o.ordinateur) Icons.Outlined.Smartphone else Icons.Outlined.DesktopWindows, if (o.ordinateur) "Mode téléphone" else "Mode ordinateur", actif = o.ordinateur) {
                    Navigateur.modeOrdinateur(o, !o.ordinateur); onFermer()
                }
            },
            { Tuile(Icons.Outlined.FindInPage, "Rechercher dans la page", enabled = !o.estAccueil) { onRecherche(); onFermer() } },
            { Tuile(Icons.Outlined.TouchApp, "Masquer un élément", enabled = !o.estAccueil) { Navigateur.demarrerSelection(); onFermer() } },
            { Tuile(Icons.Outlined.Share, "Partager", enabled = !o.estAccueil) { Navigateur.partager(contexte); onFermer() } },
            { Tuile(Icons.Outlined.Settings, "Réglages") { aller(Destination.Reglages); onFermer() } },
            { Tuile(Icons.AutoMirrored.Outlined.HelpOutline, "Comment faire") { aller(Destination.Guide); onFermer() } }
        )
        tuiles.chunked(4).forEach { ligne ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ligne.forEach { t -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { t() } }
                repeat(4 - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleProtection(o: Onglet, onFermer: () -> Unit) {
    Reglages.version.collectAsState().value
    val hote = Bloqueur.host(o.url)?.removePrefix("www.") ?: ""
    val actif = Bloqueur.enabledOn(o.url)
    val masques = if (hote.isBlank()) emptyList() else Bloqueur.UserHiding.entries(hote)
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (o.estAccueil) "Protection" else hote, style = MaterialTheme.typography.titleLarge)
            Text(
                "${o.bloques} élément(s) bloqué(s) sur cette page · ${Bloqueur.totalBloques} au total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (o.drm) {
                Spacer(Modifier.height(10.dp))
                CarteInfo(Icons.Outlined.Lock, "Ce site utilise une protection DRM : ses vidéos ne peuvent pas être téléchargées.")
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        if (!o.estAccueil && hote.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bloqueur actif sur ce site")
                    Text(
                        if (Reglages.blocagePub) "Désactivez-le si ce site refuse de fonctionner." else "Le bloqueur est désactivé dans les Réglages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = actif,
                    enabled = Reglages.blocagePub,
                    onCheckedChange = { on ->
                        val l = Reglages.sitesSansBlocage.toMutableList()
                        if (on) l.removeAll { hote == it || hote.endsWith(".$it") } else l.add(hote)
                        Reglages.sitesSansBlocage = l.distinct()
                        Navigateur.recharger(o)
                    }
                )
            }
            LigneInterrupteur(Icons.Outlined.DesktopWindows, "Mode ordinateur", "Affiche la version complète du site", o.ordinateur) {
                Navigateur.modeOrdinateur(o, it)
            }
            LigneAction(Icons.Outlined.TouchApp, "Masquer un élément", "Touchez ensuite l'élément gênant") {
                Navigateur.demarrerSelection()
                onFermer()
            }
            if (masques.isNotEmpty()) {
                LigneAction(Icons.Outlined.VisibilityOff, "Réafficher les éléments masqués", "${masques.size} élément(s) masqué(s) sur ce site") {
                    masques.map { it.first }.distinct().forEach { Bloqueur.UserHiding.clear(it) }
                    Navigateur.recharger(o)
                    onFermer()
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
