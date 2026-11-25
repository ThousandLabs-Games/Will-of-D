package com.thousandlabs.willofd.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.BuildConfig
import com.thousandlabs.willofd.R

private class Rubrique(val icone: ImageVector, val titre: String, val etapes: List<String>)

private val GUIDE = listOf(
    Rubrique(
        Icons.Outlined.Public, "Naviguer",
        listOf(
            "Touchez la barre d'adresse en haut, saisissez une adresse ou une recherche, puis validez.",
            "Le cadenas indique une connexion sécurisée. Le bouclier, à droite, montre le nombre d'éléments bloqués sur la page.",
            "En bas : page précédente, page suivante, accueil, onglets, téléchargement et menu.",
            "Menu : nouvel onglet, onglet privé (ni historique ni cookies conservés), favoris, historique, mode ordinateur, recherche dans la page, partage."
        )
    ),
    Rubrique(
        Icons.Outlined.Shield, "Protection contre les publicités et les redirections",
        listOf(
            "Les publicités et pisteurs sont bloqués d'office. Si un site refuse ou contourne le blocage, le plus simple est de changer de site.",
            "Une redirection vers un autre site sans geste de votre part est bloquée. Une indication discrète apparaît en bas, avec « Autoriser » si c'était voulu.",
            "Les fenêtres surgissantes sont bloquées ; une fenêtre ouverte après votre appui sur un autre site peut être ouverte avec « Ouvrir ».",
            "Les calques invisibles posés sur les lecteurs vidéo sont retirés : touchez à nouveau pour lancer la vidéo.",
            "Interagir avec la page ou le lecteur (lecture, pause, plein écran) reste toujours possible.",
            "Pour un site qui ne fonctionne plus : bouclier, puis « Bloqueur actif sur ce site » désactivé."
        )
    ),
    Rubrique(
        Icons.Outlined.TouchApp, "Masquer un élément gênant",
        listOf(
            "Menu, puis « Masquer un élément », ou bouclier, puis « Masquer un élément ».",
            "Touchez l'élément : il est encadré. « Élargir » choisit le bloc qui le contient.",
            "« Masquer » le cache sur tout le site, y compris aux prochaines visites.",
            "Pour tout réafficher : bouclier, puis « Réafficher les éléments masqués »."
        )
    ),
    Rubrique(
        Icons.Outlined.Movie, "Télécharger une vidéo",
        listOf(
            "Ouvrez la page de la vidéo et lancez la lecture quelques secondes : le flux est alors repéré.",
            "Touchez l'icône de téléchargement en bas (un nombre indique les vidéos repérées).",
            "Choisissez la vidéo, vérifiez le titre, la série et le numéro d'épisode, puis « Télécharger ».",
            "Si le site propose son propre lien de téléchargement, Will of D le prend en charge directement.",
            "Les vidéos protégées par DRM (plateformes payantes) ne peuvent pas être téléchargées."
        )
    ),
    Rubrique(
        Icons.AutoMirrored.Outlined.MenuBook, "Télécharger un chapitre (manga, webtoon)",
        listOf(
            "Ouvrez le chapitre et faites-le défiler jusqu'en bas pour charger toutes les planches.",
            "Icône de téléchargement, puis « Rechercher les planches de cette page ».",
            "Vérifiez le titre, la série, le numéro du chapitre et le sens de lecture, puis « Télécharger ».",
            "Les pages sont enregistrées au format de Rio Poneglyph : images numérotées et fichier poneglyph.json.",
            "Une suite téléchargée plus tard (chapitre suivant, ou page 2 du même chapitre) s'enchaîne ou se regroupe proprement dans Rio Poneglyph, sans doublon."
        )
    ),
    Rubrique(
        Icons.Outlined.Security, "Quand un site bloque le téléchargement",
        listOf(
            "1. Will of D tente d'abord le téléchargement classique.",
            "2. Si le site résiste, les pages sont reprises depuis la page ouverte, après neutralisation des scripts gênants. Gardez la page ouverte pendant ce temps.",
            "3. Dans le panneau de téléchargement, « Débloquer la page et chercher à nouveau » rétablit le clic droit, retire les calques et relance le lecteur.",
            "Si le site résiste encore, un constat clair s'affiche : mieux vaut passer à un autre site.",
            "Ces contournements sont ciblés et se désactivent dans les Réglages."
        )
    ),
    Rubrique(
        Icons.Outlined.Queue, "Téléchargements en série ou en parallèle",
        listOf(
            "Ajoutez autant de vidéos et de chapitres que vous le souhaitez : ils entrent dans la file.",
            "Écran Téléchargements : « En série » télécharge un élément à la fois ; 2, 3 ou 4 en parallèle vont plus vite.",
            "Chaque téléchargement a sa notification de progression. Pause, reprise, annulation et nouvel essai sont possibles.",
            "Un téléchargement interrompu (réseau, redémarrage) reprend là où il s'était arrêté."
        )
    ),
    Rubrique(
        Icons.Outlined.Download, "Envoyer vers Rio Poneglyph",
        listOf(
            "Les téléchargements terminés restent dans la zone privée de Will of D, invisibles pour la Galerie et Google Files.",
            "Dans Rio Poneglyph : Importer, puis « Depuis Will of D ». Choisissez les éléments, puis Récupérer.",
            "Chaque élément est chiffré dans le coffre, vérifié, puis disparaît automatiquement de Will of D, nom compris.",
            "L'échange se fait uniquement sur le téléphone, jamais par Internet, et seulement avec Rio Poneglyph signée par Thousand Labs. Rio Poneglyph n'envoie jamais rien vers Will of D."
        )
    ),
    Rubrique(
        Icons.Outlined.Lock, "Verrouillage (facultatif)",
        listOf(
            "Réglages, puis « Verrouillage par code » : choisissez un code de 4 à 8 chiffres.",
            "Choisissez quand verrouiller : seulement à l'ouverture, ou après 5 ou 30 minutes d'absence. Quitter l'application ne la verrouille jamais immédiatement.",
            "L'empreinte peut remplacer le code.",
            "Le verrouillage se désactive à tout moment, après saisie du code actuel."
        )
    ),
    Rubrique(
        Icons.Outlined.PhoneAndroid, "Réglages conseillés sur Tecno Spark 40 (HiOS)",
        listOf(
            "Autorisez les notifications de Will of D à la première demande.",
            "Phone Master, puis gestion du démarrage automatique : autorisez Will of D.",
            "Paramètres, Batterie, Will of D : choisissez « Aucune restriction », sinon HiOS coupe les téléchargements écran éteint.",
            "Dans les applications récentes, verrouillez Will of D (cadenas) pour qu'elle ne soit pas fermée lors du nettoyage.",
            "Mettez à jour « Android System WebView » et Chrome depuis le Play Store : le moteur de navigation en dépend."
        )
    ),
    Rubrique(
        Icons.Outlined.SystemUpdate, "Mettre à jour Will of D",
        listOf(
            "Réglages, puis Mises à jour : la recherche est automatique.",
            "Touchez Télécharger puis Installer. La signature est vérifiée avant l'installation.",
            "La mise à jour s'installe par-dessus l'application : réglages, favoris et téléchargements sont conservés."
        )
    )
)

private class Version(val numero: String, val points: List<String>)

private val NOUVEAUTES = listOf(
    Version(
        "1.0.0",
        listOf(
            "Première version de Will of D, navigateur sécurisé de Thousand Labs Games.",
            "Blocage renforcé des publicités et des pisteurs, des redirections non sollicitées, des fenêtres surgissantes et des calques invisibles, avec indication discrète en bas de l'écran.",
            "Onglets, onglets privés, favoris, historique, mode téléphone ou ordinateur, masquage d'éléments, recherche dans la page.",
            "Téléchargement de vidéos (liens directs et flux HLS) et de chapitres complets au format Rio Poneglyph, avec continuité des suites.",
            "Repli progressif face aux sites qui bloquent : téléchargement classique, récupération depuis la page, déblocage ciblé, puis constat clair.",
            "File en série ou en parallèle, notifications de progression, reprise après interruption.",
            "Zone privée reliée à sens unique à Rio Poneglyph, liste des téléchargements synchronisée automatiquement.",
            "Verrouillage facultatif par code ou empreinte, mises à jour intégrées depuis GitHub, guide « Comment faire »."
        )
    )
)

@Composable
fun EcranGuide(onRetour: () -> Unit) {
    EcranSecondaire("Comment faire", onRetour) { marges ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            items(GUIDE, key = { it.titre }) { r ->
                var ouvert by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable { ouvert = !ouvert }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(r.icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(r.titre, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (ouvert) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                        if (ouvert) {
                            Spacer(Modifier.height(10.dp))
                            r.etapes.forEach { e ->
                                Text(
                                    e,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EcranNouveautes(onRetour: () -> Unit) {
    EcranSecondaire("Nouveautés", onRetour) { marges ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            items(NOUVEAUTES, key = { it.numero }) { v ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Version ${v.numero}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        v.points.forEach { p ->
                            Text(p, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EcranAPropos(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    EcranSecondaire("À propos", onRetour) { marges ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF0F1B3D))
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(120.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Will of D", style = MaterialTheme.typography.headlineSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Thousand Labs Games", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Navigateur sécurisé et anti-publicité, avec téléchargement de vidéos et de chapitres vers Rio Poneglyph. Aucun compte, aucune publicité, aucun pistage, aucun serveur : tout reste sur le téléphone.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { ouvrirLien(contexte, URL_DEPOT) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dépôt officiel de Will of D")
            }
            TextButton(onClick = { ouvrirLien(contexte, URL_RIO) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rio Poneglyph")
            }
            TextButton(onClick = { ouvrirLien(contexte, URL_SITE) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Site de Thousand Labs Games")
            }
        }
    }
}
