package com.zoka.zkapriv.ui.quantic

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zoka.zkapriv.ui.qrscanner.QrScannerScreen
import com.zoka.zkapriv.ui.theme.ZK_DURATION_ENTER
import com.zoka.zkapriv.ui.theme.ZK_DURATION_FAST
import com.zoka.zkapriv.ui.theme.Zk
import com.zoka.zkapriv.ui.theme.ZkEasing

/**
 * ZKAPriv 3: toda la app sobre Zoka Quantic Testnet v2.
 *
 * `locked` lo maneja la actividad (se bloquea al salir y a los 5 minutos sin
 * uso); `onUnlocked` le avisa que el PIN o la huella fueron correctos.
 */
@Composable
fun QuanticApp(vm: WalletViewModel, locked: Boolean, onUnlocked: () -> Unit,
    payment: String? = null, onPaymentHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val animate = Zk.animations
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tabs = route in walletRoutes && !locked

    // Wallet siempre es la base de la pila: volver a ella es sacar lo de
    // arriba. La pantalla actual se lee del controlador en cada toque: la
    // referencia a esta función que recibe la barra puede quedar memorizada con
    // la ruta de cuando se dibujó, y con esa ruta vieja ("home") tocar Wallet
    // desde Send o Receive no hacía nada (3/11/2026). Tampoco se usa
    // restoreState: devolvía la pila guardada de otra pestaña.
    fun selectTab(destination: String) {
        if (nav.currentBackStackEntry?.destination?.route == destination) return
        if (destination == "home") {
            if (!nav.popBackStack("home", inclusive = false)) {
                nav.navigate("home") { popUpTo(0); launchSingleTop = true }
            }
            return
        }
        nav.navigate(destination) {
            popUpTo("home")
            launchSingleTop = true
        }
    }
    val start = remember {
        when {
            !vm.hasWallet() -> "welcome"
            locked -> "lock"
            else -> "home"
        }
    }

    // Al bloquearse, a la pantalla de bloqueo; la cadena se deja de leer.
    LaunchedEffect(locked) {
        if (locked) {
            vm.stopSync()
            if (vm.hasWallet() && nav.currentDestination?.route != "lock") {
                nav.navigate("lock") { popUpTo(0) }
            }
        } else if (vm.hasWallet()) {
            vm.startSync()
        }
    }

    // El enlace solo completa el formulario, después de desbloquear. Nunca envía.
    LaunchedEffect(payment, locked, route) {
        if (payment != null && !locked && vm.hasWallet() && route in walletRoutes) {
            selectTab("send")
            nav.currentBackStackEntry?.savedStateHandle?.set("qr", payment)
            onPaymentHandled()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWalletTabs provides tabs) {
            NavHost(
                modifier = Modifier.weight(1f),
                navController = nav,
                startDestination = start,
                enterTransition = {
                    if (animate) slideInHorizontally(tween(ZK_DURATION_ENTER, easing = ZkEasing)) { it / 6 } +
                        fadeIn(tween(ZK_DURATION_ENTER)) else EnterTransition.None
                },
                exitTransition = { if (animate) fadeOut(tween(ZK_DURATION_FAST)) else ExitTransition.None },
                popEnterTransition = { if (animate) fadeIn(tween(ZK_DURATION_ENTER)) else EnterTransition.None },
                popExitTransition = {
                    if (animate) slideOutHorizontally(tween(ZK_DURATION_FAST)) { it / 6 } +
                        fadeOut(tween(ZK_DURATION_FAST)) else ExitTransition.None
                },
            ) {
                // ── Onboarding ───────────────────────────────────────────────────────
                composable("welcome") {
                    WelcomeScreen(
                        onCreate = {
                            vm.startNewWallet()
                            nav.navigate("backup")
                        },
                        onRestore = { nav.navigate("restore") },
                    )
                }
                composable("backup") {
                    val words = remember { vm.draftPhrase?.split(" ").orEmpty() }
                    BackupPhraseScreen(words, onBack = { nav.popBackStack() }, onContinue = { nav.navigate("verify") })
                }
                composable("verify") {
                    val words = remember { vm.draftPhrase?.split(" ").orEmpty() }
                    VerifyPhraseScreen(words, onBack = { nav.popBackStack() }, onVerified = {
                        vm.commitPhrase()
                        nav.navigate("pin") { popUpTo("welcome") }
                    })
                }
                composable("restore") {
                    RestoreScreen(vm::validPhrase, onBack = { nav.popBackStack() }, onRestored = { phrase ->
                        vm.useRestoredPhrase(phrase)
                        vm.commitPhrase()
                        nav.navigate("pin") { popUpTo("welcome") }
                    })
                }
                composable("pin") {
                    PinSetupScreen(onBack = { nav.popBackStack() }, onPinSet = { pin ->
                        vm.setPin(pin)
                        nav.navigate("biometric")
                    })
                }
                composable("biometric") {
                    BiometricOfferScreen(onDone = { enabled ->
                        vm.finishOnboarding(enabled)
                        onUnlocked()
                        vm.startSync()
                        nav.navigate("home") { popUpTo(0) }
                    })
                }

                // ── Bloqueo ──────────────────────────────────────────────────────────
                composable("lock") {
                    LockScreen(
                        vm,
                        onUnlocked = {
                            onUnlocked()
                            nav.navigate("home") { popUpTo(0) }
                        },
                        onWiped = { nav.navigate("welcome") { popUpTo(0) } },
                    )
                }

                // ── Billetera ────────────────────────────────────────────────────────
                composable("home") {
                    HomeScreen(
                        vm,
                        onWallets = { nav.navigate("wallets") { launchSingleTop = true } },
                        onScan = { nav.navigate("scan") { launchSingleTop = true } },
                        onNetwork = { nav.navigate("network") { launchSingleTop = true } },
                        onSendProgress = { selectTab("send") },
                    )
                }
                composable("network") { NetworkScreen(vm, onBack = { nav.popBackStack() }) }

                // ── Varias billeteras: lista y alta desde adentro ───────────────────
                composable("wallets") {
                    WalletsScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onCreate = {
                            vm.startNewWallet()
                            nav.navigate("wallets/backup")
                        },
                        onRestore = { nav.navigate("wallets/restore") },
                    )
                }
                composable("wallets/backup") {
                    val words = remember { vm.draftPhrase?.split(" ").orEmpty() }
                    BackupPhraseScreen(
                        words,
                        onBack = {
                            vm.clearDraft()
                            nav.popBackStack()
                        },
                        onContinue = { nav.navigate("wallets/verify") },
                    )
                }
                composable("wallets/verify") {
                    val words = remember { vm.draftPhrase?.split(" ").orEmpty() }
                    VerifyPhraseScreen(words, onBack = { nav.popBackStack() }, onVerified = { nav.navigate("wallets/name") })
                }
                composable("wallets/restore") {
                    RestoreScreen(vm::validPhrase, onBack = { nav.popBackStack() }, onRestored = { phrase ->
                        vm.useRestoredPhrase(phrase)
                        nav.navigate("wallets/name")
                    })
                }
                composable("wallets/name") {
                    WalletNameScreen(vm, onBack = { nav.popBackStack() }, onAdded = {
                        nav.popBackStack("home", inclusive = false)
                    })
                }
                composable("send") { entry ->
                    var scanned by remember { mutableStateOf<String?>(null) }
                    val handle = entry.savedStateHandle
                    LaunchedEffect(handle) {
                        handle.getStateFlow<String?>("qr", null).collect { value ->
                            if (value != null) {
                                scanned = value
                                handle["qr"] = null
                            }
                        }
                    }
                    SendScreen(
                        vm,
                        scanned = scanned,
                        onScan = { nav.navigate("scan") },
                        onDone = { selectTab("home") },
                    )
                }
                composable("scan") {
                    QrScannerScreen(
                        onNavigateBack = { nav.popBackStack() },
                        onQrCodeScanned = { text ->
                            // Escaneado desde Send vuelve con el resultado; desde
                            // el inicio, abre la pestaña Send con lo leído.
                            val previous = nav.previousBackStackEntry
                            if (previous?.destination?.route == "send") {
                                previous.savedStateHandle["qr"] = text
                                nav.popBackStack()
                            } else {
                                nav.popBackStack()
                                selectTab("send")
                                nav.currentBackStackEntry?.savedStateHandle?.set("qr", text)
                            }
                        },
                    )
                }
                composable("receive") { ReceiveScreen(vm) }
                composable("history") {
                    HistoryScreen(vm, onMovement = { nav.navigate("movement/$it") })
                }
                composable("movement/{txid}") { entry ->
                    MovementScreen(vm, entry.arguments?.getString("txid").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        vm,
                        onBack = null,
                        onWallets = { nav.navigate("wallets") { launchSingleTop = true } },
                        onNetwork = { nav.navigate("network") { launchSingleTop = true } },
                        onReveal = { nav.navigate("reveal/$it") },
                        onChangePin = { nav.navigate("changepin") },
                        onWiped = { nav.navigate("welcome") { popUpTo(0) } },
                    )
                }
                composable("reveal/{what}") { entry ->
                    RevealScreen(vm, entry.arguments?.getString("what").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("changepin") {
                    ChangePinScreen(vm, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                }
            }
        }
        if (tabs) WalletNavigation(route, ::selectTab)
    }
}
