package com.zoka.zkapriv

import android.os.Bundle
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.ui.gallery.ZkGallery
import com.zoka.zkapriv.ui.quantic.QuanticApp
import com.zoka.zkapriv.ui.quantic.WalletViewModel
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

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ni capturas ni grabaciones de pantalla, ni vista previa en recientes:
        // acá se ven la frase de recuperación y la clave de vista.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        // Galería de componentes, solo en depuración y sin datos de la
        // billetera: por eso es la única pantalla que admite capturas.
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
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                window.decorView.setBackgroundColor((if (dark) ZkDark else ZkLight).background.toArgb())
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            ZkTheme(darkTheme = dark, animations = animations) {
                QuanticApp(vm, locked = locked, onUnlocked = { sessionManager.unlock() })
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
