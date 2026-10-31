# Inventario para ZKAPriv 3.0 (E0)

Foto del código en la rama `zkapriv-v2` (sale de `quantic-motor`, versión 2.1.4,
versionCode 17), tomada el 30/10/2026 antes de tocar nada. Sirve para saber qué
se rehace, qué se borra y qué se reusa. El plan está en
[PLAN-ZKAPRIV-3.md](PLAN-ZKAPRIV-3.md).

## 1. Interfaz

Todo es Jetpack Compose con Material 3: no hay layouts XML, ni Fragments, ni
`@Preview`.

### Módulos y navegación

- Módulos: `:app`, `:core`, `:ui`, `:security`, `:network`, `:price`, más
  `native` (crate de Rust que deja los `.so` en `native/jniLibs`, solo
  arm64-v8a y armeabi-v7a).
- Pantallas en `ui/src/main/java/com/zoka/zkapriv/ui/` (`accounts`, `auth`,
  `charts`, `components`, `dashboard`, `lock`, `onboarding`, `portfolio`,
  `qrscanner`, `reset`, `settings`, `transaction`).
- **El tema vive en `:app`** (`app/.../ui/theme/{Color,Theme,Type}.kt`) y `:ui`
  no depende de `:app`: ninguna pantalla puede usar los tokens. Por eso hay
  colores sueltos por todos lados. En E1 el tema pasa a `:ui`.
- `MainActivity` es `FragmentActivity` (para BiometricPrompt), con Hilt,
  `FLAG_SECURE` y `enableEdgeToEdge()`; solo vertical.
- `SessionManager` bloquea a los 300 000 ms sin uso y al pausar la app.
- El `NavHost` está dentro de `MainActivity.setContent`, con rutas de texto y
  transición de 350 ms (deslizar + fundido):

| Ruta | Pantalla |
|---|---|
| `lock` | `LockScreen` |
| `onboarding` | `OnboardingFlowNew` (forzado a tema claro) |
| `home` | `DashboardScreen` |
| `portfolio` | `PortfolioScreen` |
| `send` | `SendScreen` (lee `qr_result`) |
| `receive` | `ReceiveScreen` |
| `settings` | `SettingsScreen` (`onShowMnemonic` es un TODO) |
| `accounts` | `AccountsScreen` |
| `charts` | `ChartsScreen` |
| `qr-scanner` | `QrScannerScreen` (escribe `qr_result` en la entrada anterior) |

Errores conocidos de navegación:
- el manifiesto declara el esquema `zoka:` pero el `NavHost` no lo atiende;
- escanear desde el ícono del dashboard escribe `qr_result` en `home`, que no
  lo lee.

### Tema actual (se reemplaza entero)

- `ZkaPrivTheme(darkTheme, dynamicColor=false)` solo pasa `colorScheme` y
  `typography`; no hay `Shapes`.
- Primario azul `#007AFF` en oscuro y **negro** en claro; secundario violeta
  `#7B3FE4`; fondo `#000000` / `#FFFFFF`.
- El onboarding usa otro esquema propio (azul `#1976D2`).
- 38 hex sueltos en `:ui` (verdes, naranjas, rojos, degradés navy y violetas)
  y unos 170 `Color.Black` / `Color.White`.
- El modo oscuro se detecta de cuatro maneras distintas, y la barra de estado
  se pinta en cuatro lugares.
- `ThemeManager` (`core/.../preferences/ThemeManager.kt`) guarda LIGHT / DARK /
  SYSTEM en DataStore, con LIGHT por defecto. Se reusa.
- Tipografía: solo cuatro estilos, sin fuentes propias, `FontFamily.Monospace`
  en 7 lugares.
- Sin tokens de medidas: radios de 4 a 40 dp, botones de 56/58/64 dp.
- El único rosa es `AccountColor.PINK("#E91E63")`, uno de los diez colores de
  cuenta (`core/.../model/Account.kt`).
- El XML (`styles.xml`) hereda de MaterialComponents con azul viejo; el splash
  es blanco.

### Pantallas

- **Onboarding** (`ui/onboarding/`), con `OnboardingViewModelNew`:
  WELCOME → GENERATE_SEED → VERIFY_SEED → SETUP_PIN → COMPLETED.
  - `ImportWalletDialog` (dentro de `OnboardingFlowNew.kt`): frase de 12/24
    palabras o "Scan Key" en hex.
  - Overlay "Preparing your wallet…" que menciona Tor.
  - `PinSetupScreen` con `BiometricSetupDialog`, que **muestra una línea de
    depuración** y dice "Zoka Pay".
  - Sin uso: `OnboardingScreen.kt`, `OnboardingViewModel.kt`,
    `OnboardingComponents.kt`, `screens/BiometricSetupScreen.kt`,
    `screens/PinScreens.kt` (salvo `PinVisualTransformation`, que sí se usa) y
    `screens/VerifyMnemonicScreen.kt`.
- **Bloqueo** (`ui/lock/LockScreen.kt`): saludo, aviso de sesión que siempre
  dice "Your session expired…", biometría o PIN, teclado propio, "Reset
  wallet?" → `ui/reset/WalletResetCoordinator.kt`.
- **Autenticación** (`ui/auth/AuthenticationDialog.kt`): PIN de 6 dígitos o
  biometría; lo usa Enviar.
- **BiometricPrompt** en bloqueo, alta del PIN, enviar, ajustes (ver frase y
  clave) y `SettingsViewModel`. Ayudante en
  `security/.../biometric/BiometricManager.kt`. Siempre el diálogo del
  sistema.
- **Dashboard** (`ui/dashboard/DashboardScreen.kt`, 2217 líneas):
  - barra con cuenta activa y botón de escanear;
  - barra inferior Home / Receive / Send / Settings (solo Home es pestaña);
  - `Web3BalanceCard`: saldo, "$ Soon", disponible y pendiente, pares con
    `PeerSparkline`, progreso de sincronización, avisos de saldo sin
    verificar, incompleto o madurando;
  - acciones rápidas 2×2 y "Recent Activity" ("View All" es un TODO);
  - unas 660 líneas sin uso (líneas ~1389–2050).
  - No hay pantalla de historial.
- **Enviar** (`ui/transaction/SendScreen.kt`): una sola pantalla con monto
  (ZKA/USD, MAX), destinatario (pegar, escanear) y botón fijo. Después,
  autenticación y `TransactionResultScreen` (éxito o fallo). `estimatedFee`
  existe pero no se muestra.
- **Recibir** (`ui/transaction/ReceiveScreen.kt`): QR `zoka:addr?amount=`,
  dirección, `HybridAddressCard` con QR multiparte animado (500 ms por cuadro)
  para `zkq1`, pedido de monto.
  - **Error de diseño:** `HybridAddressCard` quedó dentro de un `Surface` y se
    superpone a la dirección.
- **Lector QR** (`ui/qrscanner/`): CameraX 1.3.0 + ML Kit 17.2.0, linterna,
  marco = un cuadrado de 250 dp al 20 %. Multiparte en `MultiPartQr.kt`:
  `zkqp:i/n:huella:trozo`, 300 caracteres por parte, solo si el texto pasa de
  400. Se reusa.
- **Ajustes** (`ui/settings/SettingsScreen.kt`, 2203 líneas):
  - Apariencia (tema);
  - Seguridad: cambiar PIN, biometría, ver frase, ver "Scan Key";
  - Billetera: cuentas;
  - Red: "RPC endpoint" (sale en v2);
  - Historial: exportar e importar;
  - Acerca de: dice "Version 0.1.0" fijo; diálogo con enlaces;
  - Zona de peligro: borrar la billetera.
- **Precio** (`ui/charts/ChartsScreen.kt`) desde `:price` (CoinGecko /
  CoinPaprika). No hay precio real de ZKA: según el plan, sale.
- **Cuentas y portafolio** (`ui/accounts/`, `ui/portfolio/`): varias cuentas,
  importar por scan key, colores por cuenta.
- **Red / pares / Tor:** sin pantallas propias; solo la fila de pares y la
  barra de sincronización del dashboard.
- **Splash:** blanco con `splash_icon` (una marca distinta), y después
  `AnimatedSplashScreen` deja **1,5 s de pantalla blanca**.

### Componentes compartidos

- Casi todo es privado de cada archivo. `PinDot` y `NumberPad` están
  duplicados (bloqueo y alta de PIN) con implementaciones distintas.
- `ui/components/AnimatedComponents.kt`: solo se usan `ShimmerBox` y
  `AnimatedCryptoCounter`.
- `ui/components/LucideIcons.kt`: íconos hechos a mano; se usan cuatro.
- Bloques de Ajustes públicos: `SettingsSection`, `ModernSettingsCard`,
  `ModernSettingsItem`, `ThemeOption`.

### Textos

- `strings.xml` solo tiene `app_name`. **No hay recursos de texto**: todo está
  en inglés dentro del código. No hay otros idiomas, así que v3 sigue en
  inglés (como ZSilent), con los términos de ZSilent.
- Nombres mezclados: "zkapriv", "ZKAPRIV", "ZKAPriv", "Zoka Pay". Emojis en
  los textos.

### Recursos gráficos

- Ícono adaptable con fondo blanco y como frente el mismo PNG de 1254×1254 en
  todas las densidades (sin alfa, con el damero pegado en las esquinas). Sin
  capa monocroma.
- `logoportada.png`, `zkapriv_logo.png` (tres copias), `splash_icon.png`,
  `ic_logo.xml`.
- Lottie y Coil declarados y sin uso.

### Compilación

- AGP 8.1.0, Gradle 8.4, Kotlin 1.8.21, Java 17, compilador Compose 1.4.7,
  BOM 2023.10.00.
- compileSdk 36, minSdk 26, targetSdk 36.
- Tipos: `debug` (sufijo `.benchmark`), `release` (firmado, sin minify),
  `staging`.
- `abiFilters` lista cuatro ABI pero `jniLibs` trae dos.

## 2. Capa técnica

Resumen: la app **sigue en Zenith** (direcciones `zka1`, pruebas Groth16, P2P
por libp2p con Tor siempre prendido). El puente Quantic v1 existe en el crate
de Rust, pero Kotlin no lo llama y los `.so` no lo traen. Nada usa la v2
(`zoka-pq-pool`, `stark_net::Client`, `SocksDialer`, `/scan`).

### Crate nativo (`native/`)

- Biblioteca `zoka_mobile`: `jni 0.21`, `serde_json`, el crate del nodo
  (`path = "../.."`, feature `mobile`) y Arti (`arti-client 0.45`,
  `rusqlite` empaquetado, `tor-rtcompat`, `tokio`).
- Features: `pq-experimental` (`zkq1` y salidas X-Wing v2) y `quantic`
  (motor v1: Orchard/Halo 2, bloques compactos). **No hay `quantic-pq`.**
- Perfil release: opt-level 3, LTO completo, una unidad, `panic=abort`.
- JNI de `ZokaNativeWallet` (`src/lib.rs`): `buildPrivateTransaction`
  (Groth16 en el teléfono), `hybridWalletInfo`, `scanHybridOutputs`,
  `scanWithDigests`, `preloadTrustedSetup` y dos benchmarks.
- JNI de `ZokaNativeNetwork` (`zoka::mobile_net`): `connect0`,
  `publishPrivateTx0`, `status0`, `disconnect0`, `chainHeight0`,
  `blocksRange0`, `blocksDigestRange0`, `privateTxBody0/Bodies0/Digests0`,
  `startTor0`.
- `src/quantic.rs` (feature `quantic`, red `quantic-testnet` = cadena `-v1`,
  `zqtest1`): `quanticWalletInfo`, `quanticCheckAddress`, `quanticSync`,
  `quanticBuildTransfer` (~20 s en el teléfono de 32 bits),
  `quanticPreloadProvingKey`, `quanticLight0`. Kotlin no declara ninguna.
- **Tor** (`src/tor.rs`): Arti con `create_bootstrapped`, caché en
  `<filesDir>/tor-cache` y `tor-state`, SOCKS5 propio en `127.0.0.1:0`
  (puerto al azar), solo CONNECT, un cliente aislado por conexión, una sola
  instancia. Devuelve `{"proxy":"127.0.0.1:PUERTO"}`. **Se reusa tal cual
  para el Modo Tor de v3.**
- Compilación: `native/build-android.ps1` (NDK 27.3.13750724, `cargo-ndk`,
  API 26, arm64-v8a y armeabi-v7a con `-j 1`; copia `libc++_shared.so`).

### Lado Kotlin

- Envoltorios JNI: `network/.../nativebridge/ZokaNativeWallet.kt` y
  `ZokaNativeNetwork.kt`.
- Probador en proceso aparte `:zoka_prover` (`LocalProverService` /
  `LocalProverClient`, Messenger, hasta 16 MB, 6 min de espera). **Se reusa
  para la prueba STARK.**
- `P2pZokaNodeApi` implementa la vieja `ZokaNodeApi` sobre JNI; siempre
  prende Tor y conecta a `Constants.ZOKA_P2P_SEEDS`.
- `ZokaRepository.kt` (2622 líneas): saldo recorriendo resúmenes de bloque,
  descifrado v1 en Kotlin (Ristretto255 + Blake3), nulificadores en Kotlin,
  instantánea cifrada por dirección. Envío con `LocalPrivateTransferBuilder` +
  `IncrementalPrivateChainScanner`, bandeja de salida cifrada, comisión fija de
  1000 átomos. **Todo esto se reemplaza.**
- Historial en `SharedPreferences("zoka_transactions")`, máximo 50.
- Almacenamiento:
  - `SecureStorage.kt` (EncryptedSharedPreferences `zoka_wallet_prefs`):
    frase, scan key, instantáneas, bandeja, **PIN en texto plano comparado
    como cadena**, biometría;
  - `AccountManager.kt` (`zoka_accounts_encrypted`): cuentas con frase,
    `scanKeyHex` y `restoreHeight`;
  - `RpcSettings.kt` (sin cifrar): endpoint viejo y cachés;
  - `ThemeManager.kt` (DataStore): tema;
  - Room `price_database`.
- Claves: `WalletManager.kt` usa la entropía BIP-39 cruda como scan key y
  deriva la de gasto con SHA-256. **v3 usa la derivación de ZSilent**
  (`Account::from_seed`), así la misma frase da la misma billetera.
- La biometría es un BiometricPrompt sin `CryptoObject`, y las claves del
  Keystore tienen `setUserAuthenticationRequired(false)`: la biometría no
  protege ninguna clave. A revisar en E3.
- `:price` nunca consulta nada: siempre devuelve "Public price feeds are
  disabled…". La pantalla de precio sale.

### Lo que depende de v1/Zenith (se borra o se rehace)

- Direcciones `zka1`, `zkq1` y `zpriv`: `WalletManager.kt`, `Bech32.kt`,
  `Ristretto255.kt`, validaciones en `ZokaRepository.kt` (1947–1970,
  2313–2406), `ReceiveViewModel`, `ReceiveScreen` (`HybridAddressCard`),
  `SendViewModel`, `SendScreen` ("zka1..."), Ajustes (`zpriv`).
- Scan keys: Ajustes "Show Scan Key", `AccountManager.importAccountFromScanKey`
  y las claves `scan_key_*`. En v3 es la clave de vista `zqtestview2`.
- Protocolo Zenith: `ZokaRepository.kt` (1036–1600, 1761–1850), parámetros
  Groth16 en `app/src/*/assets/trusted-setup*`, `ZOKA_PRIVATE_*` en
  `network/build.gradle.kts`, `LocalPrivateTransferBuilder`,
  `IncrementalPrivateChainScanner`, `PrivateChainSnapshotStore`,
  `P2pZokaNodeApi`, `ZokaNodeApi`, `ZokaRestModels`, `RpcEndpointInterceptor`,
  `RpcSettings`, `SeedEnvelopeEncryption`.
- Constantes (`core/.../util/Constants.kt`): `ZOKA_P2P_SEEDS` (tres `.onion`
  viejas), `ZOKA_CHAIN_ID = "zoka-mainnet-zenith-v1"`,
  `WALLET_BIRTHDAY_FLOOR = 25_200` (altura de Zenith), RPC `127.0.0.1:3000`,
  CoinGecko, comisiones `MIN_FEE_ATOMS = 100` / `DEFAULT_FEE_ATOMS = 1000`.
- `Token(decimals = 9)` fijo en `ZokaRepository.kt` aunque ZKA tiene 6.
- No hay memo en Kotlin.
- QR: `zoka:<dir>?amount=`; multiparte `zkqp:i/n:huella:trozo` (mismo
  formato que ZSilent). La dirección completa v2 (~1760 caracteres) son 6
  partes; la corta (57) entra en un QR.
- Textos viejos: "Version 0.1.0", `README.md`, `PRIVACY_POLICY*.md`.

### Pruebas y compilación

- JVM: `core` (`Ristretto255Test`), `network` (siete clases, 14 pruebas en
  `IncrementalPrivateChainScannerTest`), `security`
  (`PrivateTxOutboxCodecTest`), `ui` (`MultiPartQrTest`,
  `ZokaPaymentQrTest`). Usan direcciones v1.
- Instrumentadas: `LocalProverServiceInstrumentedTest` y
  `MobileProverBenchmarkInstrumentedTest`.
- Firma: `keystore.properties` → `keystore/zkapriv-release-key.jks`, alias
  `zkapriv` (fuera de git).
- CI apagado (`.github/workflows/android.yml.disabled`).
- `RELEASE-ROADMAP.md` tiene el procedimiento de release (apksigner, aapt,
  armeabi-v7a obligatorio, prueba en el teléfono).
