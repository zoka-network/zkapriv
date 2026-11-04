package com.zoka.zkapriv

import android.os.Bundle
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.ui.gallery.ZkGallery
import com.zoka.zkapriv.ui.quantic.QuanticApp
import com.zoka.zkapriv.ui.quantic.WalletViewModel
import com.zoka.zkapriv.ui.quantic.WalletDesignPreview
import com.zoka.zkapriv.ui.theme.ZkDark
import com.zoka.zkapriv.ui.theme.ZkLight
import com.zoka.zkapriv.ui.theme.ZkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ZKAPriv 3. Una sola actividad: [QuanticApp] maneja todas las pantallas.
 * Es FragmentActivity porque BiometricPrompt lo necesita.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private fun configureSystemBars(dark: Boolean) {
        val background = (if (dark) ZkDark else ZkLight).background.toArgb()
        window.decorView.setBackgroundColor(background)
        window.navigationBarColor = background
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    private val pendingPayment = MutableStateFlow<String?>(null)

    private fun readPaymentIntent(value: Intent?) {
        if (value?.action == Intent.ACTION_VIEW && value.data?.scheme == "zoka") {
            pendingPayment.value = value.dataString?.takeIf { it.length <= 10_000 }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPaymentIntent(intent)
    }

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readPaymentIntent(intent)

        // Ni capturas ni grabaciones de pantalla, ni vista previa en recientes:
        // acá se ven la frase de recuperación y la clave de vista.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        // Galería de componentes, solo en depuración y sin datos de la
        // billetera: por eso es la única pantalla que admite capturas.
        if (BuildConfig.DEBUG && intent.getStringExtra("screen") == "wallet-preview") {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            val dark = intent.getStringExtra("theme") != "light"
            configureSystemBars(dark)
            setContent { WalletDesignPreview(dark, intent.getStringExtra("scenario") ?: "home") }
            return
        }
        if (BuildConfig.DEBUG && intent.getStringExtra("screen") == "gallery") {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            setContent {
                var dark by remember { mutableStateOf(intent.getStringExtra("theme") != "light") }
                ZkGallery(dark = dark, onToggleTheme = { dark = !dark })
            }
            return
        }

        setContent {
            val vm: WalletViewModel = hiltViewModel()
            val themeMode by vm.themeMode.collectAsState()
            val animations by vm.animations.collectAsState()
            val locked by sessionManager.isLocked.collectAsState()
            val payment by pendingPayment.collectAsState()
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                configureSystemBars(dark)
            }
            ZkTheme(darkTheme = dark, animations = animations) {
                QuanticApp(vm, locked = locked, onUnlocked = { sessionManager.unlock() },
                    payment = payment, onPaymentHandled = { pendingPayment.value = null })
            }
        }
    }

    // Al salir de la app, no al pausarse: un diálogo del sistema (permiso de
    // cámara, compartir) pausa la actividad sin que el usuario se haya ido.
    override fun onStop() {
        super.onStop()
        sessionManager.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        sessionManager.onAppResumed()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!sessionManager.isLocked.value) {
            sessionManager.resetInactivityTimer()
        }
    }
}
