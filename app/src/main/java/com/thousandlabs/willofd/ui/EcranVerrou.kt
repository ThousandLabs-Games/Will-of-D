package com.thousandlabs.willofd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.thousandlabs.willofd.R
import com.thousandlabs.willofd.donnees.Reglages
import com.thousandlabs.willofd.securite.Biometrie
import com.thousandlabs.willofd.securite.Verrou
import kotlinx.coroutines.delay

/** Clavier de code à 4–8 chiffres, utilisé pour déverrouiller et pour définir un code. */
@Composable
fun ClavierCode(
    titre: String,
    sousTitre: String,
    erreur: String?,
    biometrie: Boolean,
    onBiometrie: () -> Unit,
    onValider: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0F1B3D))
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(104.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(titre, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(sousTitre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, code.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < code.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
        Text(
            erreur ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).height(36.dp)
        )
        val touches = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "ok")
        touches.chunked(3).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                ligne.forEach { t ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (t == "bio" && !biometrie) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = t != "bio" || biometrie) {
                                when (t) {
                                    "bio" -> onBiometrie()
                                    "ok" -> {
                                        if (code.length >= 4) {
                                            val saisi = code
                                            code = ""
                                            onValider(saisi)
                                        }
                                    }
                                    else -> { if (code.length < 8) code += t }
                                }
                            }
                    ) {
                        when (t) {
                            "bio" -> { if (biometrie) Icon(Icons.Outlined.Fingerprint, contentDescription = "Empreinte") }
                            "ok" -> Icon(Icons.Outlined.Check, contentDescription = "Valider")
                            else -> Text(t, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(enabled = code.isNotEmpty()) { code = code.dropLast(1) }
            ) { Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Effacer") }
        }
    }
}

/** Écran de déverrouillage (seulement si le verrouillage est activé dans les Réglages). */
@Composable
fun EcranVerrou() {
    val contexte = LocalContext.current
    var erreur by remember { mutableStateOf<String?>(null) }
    var essai by remember { mutableStateOf(0) }
    val bio = Reglages.biometrie && Biometrie.disponible(contexte)
    fun empreinte() {
        val a = Biometrie.activite(contexte) ?: return
        Biometrie.demander(a, "Will of D", "Déverrouillez avec votre empreinte", onSucces = { Verrou.deverrouiller() })
    }
    LaunchedEffect(Unit) { if (bio) empreinte() }
    LaunchedEffect(essai) {
        while (Verrou.attente() > 0) {
            erreur = "Trop d'essais. Patientez ${(Verrou.attente() / 1000) + 1} s."
            delay(1000)
        }
    }
    ClavierCode(
        titre = "Will of D est verrouillée",
        sousTitre = "Saisissez votre code",
        erreur = erreur,
        biometrie = bio,
        onBiometrie = { empreinte() },
        onValider = { code ->
            when {
                Verrou.attente() > 0 -> essai++
                Verrou.verifier(code) -> Verrou.deverrouiller()
                else -> {
                    erreur = "Code incorrect."
                    essai++
                }
            }
        }
    )
}
