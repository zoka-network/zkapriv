# ZKAPriv 3.0: rediseño visual completo y paso a Zoka Quantic Testnet v2

## Contexto

ZKAPriv, la billetera Android (`C:\Users\mderramus\ZOKA\zkapriv`, rama
`quantic-motor`, versión 2.1.4), quedó en la testnet **v1**: direcciones
`zkq1`, Orchard/Halo2, bloques compactos y P2P lento. Tampoco tiene el estilo
nuevo.

La red ahora es la **v2 STARK**:
- direcciones `zqtest2` completas y cortas;
- clave de vista `zqtestview2`;
- API pública con `/scan` y caché en zokascan.cc;
- Modo Tor opcional con la semilla .onion seed2;
- ZSilent 4.0.5 y nodo 4.0.3 ya publicados.

El usuario pidió dos cosas:
1. Rehacer **todo** el esquema visual con la paleta nueva y los detalles
   rosa: onboarding, dashboard, bloqueo, login, biometría, enviar, recibir,
   ajustes, lector QR y el resto.
2. Aplicar los cambios conceptuales y técnicos de la red.

La autonomía es por etapas, dejando rastro para que Codex pueda seguir.

**Decisiones técnicas ya acordadas (30/10):**
- **Leer saldo:** por `/scan` (`https://zokascan.cc/scan`, que tiene caché),
  abriendo las salidas en el teléfono. Caché local de páginas y "fecha de
  nacimiento" de cada billetera. Si la API no responde, P2P.
- **Prueba STARK:** siempre en el teléfono. Nunca se manda el testigo a un
  servidor.
- **Enviar:** por P2P al nodo, con iroh. En Modo Tor, con el Arti que ZKAPriv
  ya trae, por su SOCKS (`SocksDialer`), hacia la .onion de seed2. La API
  pública sigue siendo de solo lectura.
- **Tor es opcional:** apagado por defecto. Si Tor no arranca, no hay
  conexión directa en su lugar.
- **Teléfono de prueba:** 32 bits, **armeabi-v7a**. Recompilar ese ABI y
  también arm64.

## Base visual (fuente: tema de ZSilent)

Portar a Android Compose los tokens de
`zsilent-core/src/main/kotlin/com/zsilent/core/ui/theme/Theme.kt`, los
componentes de `ui/components/Components.kt` y las reglas de
`zsilent-core/docs/GUIA_CAMBIO_VISUAL.md`.

- **Rosa:** `#F2ABD0` en oscuro y `#A8326A` en claro. Sobre rosa, el texto va
  en `#0A0709`. Tinta de gráficos y progreso: `#D9659F`. Enlaces: `#E8BDD5`.
- **Fondos y bordes:** el fondo oscuro es `#000000`, con superficies
  `#020202` / `#070707` / `#0F0F0F` y bordes `#1B1B1B` / `#333333`. El tema
  oscuro va por defecto, como en ZSilent (corregido el 30/10: decía claro).
- **Estados:** éxito `#6CD092`, aviso `#EFCA61`, peligro `#FF645F`. Solo
  significan estado.
- **Tipografía:** Inter para la interfaz y JetBrains Mono para todo dato
  (direcciones, montos, alturas). Las fuentes están en
  `zsilent-core/src/main/resources/font/`. Etiquetas en mayúsculas mono con
  tracking.
- **Formas:** radios 4/6/10 dp, sin elevación. Las superficies se separan con
  bordes de 1 dp.
- **Componentes:**
  - Panel, Pill (tonos On/Warn/Err/Off/Info) y ZButton (Default, Ghost,
    Accent rosa, Danger, Outline);
  - ZInput, Toggle, Segmented y Stat;
  - KeyValueRow con valor cortado al medio y botón de copiar;
  - EmptyState y aviso.
- **QR:** siempre negro sobre blanco, módulos cuadrados.
- **Ícono:** la marca Z-Q (`zsilent-core/src/main/resources/zsilent-icon.png`).
- **Sin colores sueltos:** ningún `Color(0x…)` fuera del tema. Toda animación
  respeta la preferencia de movimiento.

## Etapas

Cada etapa va en su rama y con commit por sub-etapa, push a GitHub y GitLab,
y anotación en `docs/TRASPASO.md` (repo ZOKA) y en el `HOJA_RUTA_TECNICA.md`
de zkapriv.

### E0. Mapa e inventario
- Volcar los informes de exploración al `HOJA_RUTA_TECNICA.md` de zkapriv:
  - pantallas y navegación;
  - funciones JNI;
  - todo lo que depende de v1/Zenith: `zkq1`, Ironwood, Orchard, exchange,
    semillas onion manuales, URLs y constantes.
- Rama `zkapriv-v2` desde `quantic-motor`. Los `.so` sin commitear se
  respetan.

### E1. Sistema de diseño en Android
- Paquete `ui/theme`:
  - colores oscuro/claro, tipografía con las fuentes en `res/font`, espaciado,
    formas;
  - `ZkTheme` con preferencia de tema y de movimiento.
- Paquete `ui/components`, con los componentes de arriba portados de
  ZSilent.
- QR multiparte animado para la dirección completa: reusar el de ZKAPriv
  2.1.4.
- Una pantalla de galería, solo en debug, que muestre todos los componentes
  en los dos temas.
- Ícono adaptable Z-Q y splash.

### E2. Motor nativo v2 (Rust, `zkapriv/native`)

**Nodo 4.0.4**, antes que nada:
- sumar los **nulificadores** a cada `ScanTransaction`
  (`crates/zoka-pq-pool/src/scan.rs`); sin eso, la billetera no sabe qué notas
  propias ya gastó;
- versionar el formato de página;
- recompilar el WASM de ZokaScan.

**Pool:** `zoka-pq-pool` suma un **ledger liviano** que rearma el árbol de
notas (con la regla de maduración de coinbase) a partir de las páginas, para
calcular anchor y caminos de Merkle en el teléfono sin delatar qué nota se
gasta.

**Feature `quantic-pq` en `zkapriv/native`**, con funciones JNI:
- crear y restaurar (24 palabras, la misma frase que ZSilent);
- dirección completa y corta, y exportar la clave de vista;
- sumar páginas de `/scan` (caché y fecha de nacimiento) y consultar saldo e
  historial (gastable, inmadura, memos);
- estimar comisión (`conventional_fee`);
- armar y probar la transferencia (`prove_transfer`);
- enviar por `stark_net::Client`, con iroh o con `SocksDialer` hacia el Arti
  embebido;
- publicar y resolver la dirección corta.

**Compilación y medición:**
- armeabi-v7a y arm64;
- medir en el teléfono el tiempo y la memoria de una prueba de 1×2. Define la
  interfaz de progreso del envío.

### E3. Pantallas
Cada pantalla se rehace con E1 y se conecta con E2. Se borra todo lo de v1 y
Zenith.

1. **Onboarding:**
   - bienvenida "ZOKA QUANTIC" con la palabra en rosa;
   - crear, o restaurar con 24 palabras;
   - respaldo y verificación de la frase;
   - PIN y alta de biometría;
   - aviso "Test coins, no value".
2. **Bloqueo, login y biometría:** PIN, BiometricPrompt y bloqueo automático.
3. **Dashboard:**
   - saldo total, gastable e inmadura;
   - estado de sincronización (altura y "scan pages");
   - pastilla de red, y de Tor cuando está prendido;
   - últimos movimientos.
4. **Historial y detalle de movimiento:** con el enlace a zokascan.cc.
5. **Enviar:**
   - dirección corta o completa, pegada o escaneada;
   - monto, comisión y memo de 512 bytes;
   - confirmación;
   - progreso de la prueba ("Proving…") y del envío;
   - resultado con el txid.
6. **Recibir:** dirección corta en QR normal, la completa en QR multiparte,
   copiar y compartir, y "publicar dirección corta".
7. **Lector QR:** cámara con un marco rosa. Lee direcciones cortas,
   completas, multiparte y URIs.
8. **Ajustes:**
   - seguridad: PIN, biometría, ver frase y ver clave de vista, siempre con
     autenticación;
   - red: interruptor de Modo Tor, semillas e información del nodo;
   - apariencia: tema y movimiento;
   - acerca de: versión, enlaces y la política de privacidad actualizada;
   - zona de peligro.
9. **Estados vacíos, errores y diálogos.** La pantalla de precio se mantiene
   solo si tiene datos reales; si no, sale.
10. **Textos:** se mantiene el esquema de idiomas que ya tenga la app, con los
    términos de ZSilent: short address, view key, Tor mode, ZKA, recovery
    phrase.

### E4. Pruebas
- Unitarias en Rust: ledger liviano contra ledger completo, formato de página,
  JNI de saldo.
- Unitarias en Kotlin: los ViewModels.
- Capturas de la galería y de cada pantalla en los dos temas.
- **De punta a punta en el teléfono** por adb, contra la testnet real:
  1. crear la billetera;
  2. el usuario manda ZKA desde ZSilent;
  3. ver el saldo;
  4. enviar de vuelta;
  5. repetir con el Modo Tor prendido.
  El usuario hace los envíos; yo verifico por API y por adb.

### E5. Release
- ZKAPriv 3.0.0, por ser la red v2: APK firmado con el keystore existente,
  con hashes.
- Release en GitHub (zoka-network/zkapriv) con el OK del usuario.
- Sección Android de zoka.network actualizada.
- Hoja de ruta, traspaso y memoria actualizados.

## Archivos clave
- **Fuente visual:** `zsilent-core/.../ui/theme/Theme.kt`, `Effects.kt`,
  `ui/components/Components.kt`, `docs/GUIA_CAMBIO_VISUAL.md` y
  `ui/onboarding/NoirOnboarding.kt`.
- **Red v2:**
  - pool: `crates/zoka-pq-pool/src/{wallet.rs, scan.rs, ledger.rs, transfer.rs}`;
  - cliente: `src/quantic/stark_net.rs` (`Client`);
  - Tor: `src/network/stream_transport.rs` (`SocksDialer`);
  - API: `src/quantic/stark_api.rs` (`/scan`).
- **App:** `zkapriv/native/src/{lib.rs, quantic.rs, tor.rs}`,
  `zkapriv/native/Cargo.toml`, los módulos `app`, `ui`, `core`, `network` y
  `security` de zkapriv, y `zkapriv/app/build.gradle*` (versión).

## Verificación
- **Por etapa:**
  - E1: la galería en los dos temas, en capturas;
  - E2: pruebas de Rust, más la medición de la prueba en el teléfono;
  - E3: capturas de cada pantalla;
  - E4: el flujo real de recibir y enviar en la testnet, con y sin Tor.
- **Reglas que se mantienen:**
  - los secretos no salen del teléfono ni van a los registros;
  - el QR siempre negro sobre blanco;
  - el usuario hace los envíos de prueba;
  - publicar pide OK;
  - nada de `push --force` ni `reset --hard`.
