package com.thousandlabs.willofd.securite

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrie {

    private const val AUTORISES = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun disponible(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTORISES) == BiometricManager.BIOMETRIC_SUCCESS

    fun activite(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Affiche la fenêtre d'empreinte. [onErreur] n'est appelé que pour une vraie erreur,
     * pas lorsque l'utilisateur choisit de saisir son code.
     */
    fun demander(
        activite: FragmentActivity,
        titre: String,
        sousTitre: String,
        onSucces: () -> Unit,
        onErreur: (String) -> Unit = {}
    ) {
        val invite = BiometricPrompt(
            activite,
            ContextCompat.getMainExecutor(activite),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSucces()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val annulation = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!annulation) onErreur(errString.toString())
                }
            }
        )
        val infos = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titre)
            .setSubtitle(sousTitre)
            .setNegativeButtonText("Utiliser le code")
            .setAllowedAuthenticators(AUTORISES)
            .build()
        invite.authenticate(infos)
    }
}
