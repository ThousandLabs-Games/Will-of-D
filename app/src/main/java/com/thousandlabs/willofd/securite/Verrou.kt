package com.thousandlabs.willofd.securite

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.thousandlabs.willofd.donnees.Reglages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Verrouillage optionnel par code (4 à 8 chiffres) et empreinte.
 * Il est désactivable dans les Réglages et ne verrouille jamais au simple fait de quitter l'application :
 * seulement à l'ouverture, ou après une absence prolongée si cette option est choisie.
 */
object Verrou {
    private lateinit var sp: SharedPreferences
    private const val ITERATIONS = 60_000

    private val _verrouille = MutableStateFlow(false)
    val verrouille: StateFlow<Boolean> = _verrouille.asStateFlow()

    private var sortie = 0L

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("verrou", Context.MODE_PRIVATE)
        // Démarrage de l'application : verrouillée seulement si l'option est active et un code défini.
        _verrouille.value = actif
    }

    val actif: Boolean get() = Reglages.verrouActif && sp.contains("empreinte")

    private fun deriver(code: String, sel: ByteArray): ByteArray {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return f.generateSecret(PBEKeySpec(code.toCharArray(), sel, ITERATIONS, 256)).encoded
    }

    fun definir(code: String) {
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val e = deriver(code, sel)
        sp.edit()
            .putString("sel", android.util.Base64.encodeToString(sel, android.util.Base64.NO_WRAP))
            .putString("empreinte", android.util.Base64.encodeToString(e, android.util.Base64.NO_WRAP))
            .putInt("echecs", 0)
            .apply()
        Reglages.verrouActif = true
    }

    fun desactiver() {
        sp.edit().clear().apply()
        Reglages.verrouActif = false
        Reglages.biometrie = false
        _verrouille.value = false
    }

    /** Attente imposée (ms) après plusieurs échecs. */
    fun attente(): Long {
        val jusqua = sp.getLong("bloque_jusqua", 0L)
        val reste = jusqua - System.currentTimeMillis()
        return if (reste > 0) reste else 0L
    }

    fun verifier(code: String): Boolean {
        if (attente() > 0) return false
        val sel = android.util.Base64.decode(sp.getString("sel", "") ?: "", android.util.Base64.NO_WRAP)
        val attendu = android.util.Base64.decode(sp.getString("empreinte", "") ?: "", android.util.Base64.NO_WRAP)
        if (sel.isEmpty() || attendu.isEmpty()) return false
        val ok = MessageDigest.isEqual(deriver(code, sel), attendu)
        if (ok) {
            sp.edit().putInt("echecs", 0).remove("bloque_jusqua").apply()
        } else {
            val echecs = sp.getInt("echecs", 0) + 1
            val e = sp.edit().putInt("echecs", echecs)
            if (echecs >= 5) e.putLong("bloque_jusqua", System.currentTimeMillis() + 30_000L * (echecs - 4))
            e.apply()
        }
        return ok
    }

    fun deverrouiller() { _verrouille.value = false }

    fun verrouillerMaintenant() { if (actif) _verrouille.value = true }

    fun surSortie() { sortie = SystemClock.elapsedRealtime() }

    /** Retour dans l'application : verrouillage uniquement après le délai choisi (jamais immédiat). */
    fun surRetour() {
        if (!actif || sortie == 0L) return
        val delai = when (Reglages.verrouDelai) { 1 -> 5 * 60_000L; 2 -> 30 * 60_000L; else -> return }
        if (SystemClock.elapsedRealtime() - sortie >= delai) _verrouille.value = true
    }
}
