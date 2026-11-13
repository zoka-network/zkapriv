# ZKAPriv 4.1: rediseño «negro y vidrio» de todas las pantallas

## Contexto

ZKAPriv 4.0.0 (testnet v3) funciona, pero su aspecto sigue siendo el de ZSilent
(paneles de 10 dp, botón principal relleno de rosa). El usuario quiere llevar
**todas** las pantallas (onboarding, bloqueo, reset, Home, Send, Receive,
Activity, Settings y el resto) al estilo del home de zoka.network que le gustó:
negro profundo, piezas de vidrio, rosa solo en detalles. El esquema de
navegación **no cambia** (Wallet · Send · Receive · Activity · Settings, Home
limpio). Además pide arreglar lo tedioso: en Receive las direcciones nuevas se
apilan y hay que scrollear.

Decidido con el usuario (13/11):

- **Botones negros con letras rosa suave en los dos temas.** Oscuro: cápsula de
  vidrio negro con borde fino. Claro: fondo blanco y el botón sigue negro
  sólido con letras rosa. Se va el botón relleno de rosa.
- **Receive: selector de pastillas sobre un solo QR** (sin lista, sin scroll).
- **Restaurar en 24 casillas** con sugerencias. Ninguna otra función nueva
  (ni «pedir un monto» ni nombres de direcciones).

Repo: `C:\Users\mderramus\ZOKA\zkapriv` (git propio), rama nueva
`rediseno-vidrio` desde `protocolo-v3` (= `main`, `49c6b68`). Todo el texto de
la app sigue en inglés.

## 1. Sistema de diseño

Fuente de verdad: `zokaweb/src/index.css` y `zokaweb/src/components/zoka/home.css`
(`.quantic-cta`, `.story-pill`, `.road-card`, `.story-dot`, `.story-note a`).
No hay desenfoque real: el vidrio es degradé + borde fino + brillo arriba
(barato en el teléfono de 32 bits).

### Colores (`ui/theme/ZkTheme.kt`, se agregan campos a `ZkColors`)

| Token | Oscuro | Claro | Uso |
|---|---|---|---|
| `background` | `#000000` | `#FFFFFF` | página |
| `surface` | `#050506` | `#FAFAFA` | base de tarjetas |
| `surface2` | `#0B0B0D` | `#F4F4F5` | campos, teclas |
| `elevated` | `#111114` | `#FFFFFF` | hojas y diálogos |
| `border` / `borderStrong` | blanco 12 % / 25 % | negro 9 % / 20 % | bordes de 1 dp |
| `text` | `#FAFAFA` | `#0A0A0B` | títulos, datos |
| `textSoft` (nuevo) | `#B4B4BA` | `#3F3F46` | cuerpo |
| `textMuted` | `#8B8B92` | `#6B6B73` | etiquetas |
| `textFaint` (nuevo) | `#5F5F66` | `#A1A1AA` | placeholders |
| `accent` | `#F2ABD0` | `#A8326A` | rosa sobre la página: puntito, ícono activo, ZKA, PIN |
| `accentSoft` (nuevo) | `#E8BDD5` | `#A8326A` | letras rosa: enlaces, eyebrow |
| `accentLine` / `accentWash` (nuevos) | rosa 45 % / 8 % | rosa 40 % / 7 % | borde del botón principal / fondo al apretar |
| `ink` (nuevo) | negro vidrio | `#0A0A0B` sólido | relleno de botones y selección |
| `onInk` (nuevo) | `#E8BDD5` | `#F2ABD0` | letras sobre `ink` |
| éxito / aviso / peligro | sin cambios | sin cambios | solo estado |

Regla: rosa **sobre la página** = `accent`; rosa **sobre negro** = `onInk`.
El QR sigue siempre negro sobre blanco.

### Tipografía, formas y movimiento

- Inter + JetBrains Mono como hoy. Se suma **Inter Light (300)** para bajadas y
  cuerpo grande, como la web: `ui/src/main/res/font/inter_light.ttf` (OFL, de
  la release 3.19 de rsms/inter, la misma de los otros pesos).
- Títulos Inter SemiBold con tracking −0,03 em; etiquetas, eyebrows y botones
  en mono mayúscula con tracking (botón 12 sp / 0,14 em).
- Se definen `displayMedium`/`displayLarge` y un `Zk.text.balance` (Inter
  SemiBold, cifras tabulares): hoy el saldo del Home cae en Roboto porque
  `displayMedium` no está en `ZkTypography`.
- Formas: **cápsula** para botones, pastillas, segmentado y barra inferior;
  28 dp tarjetas principales; 20 dp tarjetas; 16 dp campos y avisos; 14 dp
  recuadros de ícono. (Reemplaza la regla «nada pasa de 10 dp».)
- Movimiento: apretar = escala 0,98 + borde rosa; el QR cambia con fundido;
  PIN errado tiembla. Todo consulta `Zk.animations` como hoy.

### Componentes (`ui/components/ZkComponents.kt`, `quantic/Common.kt`)

| Componente | Cambio |
|---|---|
| `ZButton` | Variantes `Primary` (ink + letras rosa, borde `accentLine`), `Secondary` (vidrio, borde neutro, letras `text`; reemplaza Default/Outline/Accent), `Ghost`, `Danger`. Cápsula; alto 36 / 48 / 54 |
| `Panel` | Misma firma; pasa a tarjeta de vidrio (degradé 160°, borde con brillo arriba, 20 dp). Parámetro `hero` = 28 dp y borde más fuerte |
| `ZChip` (nuevo) | Pastilla del selector: elegida = `ink` + `onInk`; resto = borde fino + `textMuted` |
| `Segmented`, `ZToggle`, `Pill` | Cápsula; lo elegido/prendido usa `ink` + rosa |
| `ZInput` | 16 dp, relleno `surface2`, foco con borde rosa |
| `Notice`, `EmptyState`, `ThinDivider`, `KeyValueRow` | Mismo uso, vidrio y bordes nuevos |
| `ZkQrCode` | Placa blanca redondeada; tamaño según el ancho disponible (180–240 dp) |
| `GlassIconButton` (nuevo) | Círculo de vidrio de 44 dp: volver, escanear, linterna |
| `ZSheet` (nuevo) | Hoja desde abajo sobre `Dialog` (hereda FLAG_SECURE) |
| `PinDots`, `PinPad` | Puntos rosa con halo; teclas redondas de vidrio que se achican solas en pantallas angostas |
| `PinGate` (nuevo) | Un solo armado título + puntos + mensaje + teclado: hoy está copiado 5 veces (Lock, PinSetup, Send, Reveal, ChangePin) |
| `ZkScreen` | Título grande para las pestañas; volver en círculo de vidrio; barritas de paso `StepBars` a la derecha en el onboarding |
| `WalletNavigation` | Barra inferior como cápsula de vidrio; pestaña activa = cápsula `ink` con ícono y texto rosa. Etiquetas en Inter 11 sp sin tracking (a 360 dp con letra 110 % se cortaba «RECEIVE») |
| `GlassArc` (nuevo) | Arco de luz muy tenue, **solo** en Welcome y Lock. Nunca en el Home |
| `ProofMatrix` | Misma lógica; tarjeta de vidrio 28 dp y celdas rosa |

`ui/gallery/ZkGallery.kt` se actualiza para mostrar todo en los dos temas.

## 2. Pantallas (misma navegación y mismos flujos)

- **Welcome:** arco tenue; eyebrow con puntito «QUANTIC TESTNET V3»; ZOKA
  **QUANTIC** grande (rosa solo en QUANTIC); «Private by design.» en Light; una
  frase; el aviso amarillo pasa a una nota chica «Test coins, no value».
  Abajo: `CREATE WALLET` (Primary) y `RESTORE WITH 24 WORDS` (Secondary).
- **Backup / Verify:** barritas de paso; grilla de 24 palabras en tarjeta de
  vidrio (2 columnas); aviso de peligro de una línea; fila con interruptor
  «I wrote the 24 words». Verify: 6 pastillas, la errada tiembla en rojo.
- **Restore (nuevo, 24 casillas):** grilla 2×12 de casillas numeradas; barra de
  sugerencias pegada al teclado (hasta 4 palabras por prefijo; tocar completa y
  pasa a la siguiente); casilla en rojo si la palabra no existe; pegar 24
  palabras las reparte; contador «17 of 24»; `RESTORE WALLET` se habilita con
  24 válidas y el motor valida el checksum como hoy. La frase vive solo en
  memoria (`remember`, nunca `rememberSaveable`) y el teclado sigue sin
  autocorrector. Vale para el alta y para «Wallets → Restore».
- **PIN / Biometric:** `PinGate`; huella en círculo de vidrio con ícono rosa.
- **Lock:** arco tenue, marca mono «ZOKA QUANTIC», «Enter your PIN», teclado.
  «Forgot your PIN?» abre la pantalla **Reset** (hoy despliega un aviso al pie
  de una pantalla sin scroll, que puede quedar cortado).
- **Reset (nueva ruta `reset`):** una sola pantalla para «Forgot PIN» y para la
  «Danger zone» de Settings: explicación corta, interruptor «I have my 24
  words» y botón `REMOVE WALLET` (Danger). Bloqueada mientras hay un envío.
- **Home:** igual de limpio (marca + escáner, chip de billetera, saldo,
  aviso de envío, tarjeta de red). Solo cambia el estilo; saldo en Inter.
- **Send:** campos nuevos (To con pegar/escanear, Amount con pastilla `MAX`,
  Memo con contador); Review en tarjeta principal; PIN con `PinGate`; en
  «Working» un indicador de 4 pasos con las fases reales del motor (sin
  porcentaje inventado); Done / Failed con la matriz.
- **Receive:** fila de pastillas `Main · #1 · #2 … · + New` (los mismos
  números que muestra la billetera web para la misma frase) sobre **una**
  tarjeta con el QR, la dirección cortada al medio con copiar, y una línea de
  estado («Ready to receive» / «Activating…», como la web). Debajo `COPY` (Primary) y `SHARE` (Secondary), y
  el segmentado `Short · Full`, que ahora vale para cualquier dirección.
  `+ New` abre una hoja con la explicación y `CREATE ADDRESS`; al crearla
  queda elegida. Desaparecen el panel «Directory» y los botones «Publish»,
  «QR» y «Hide QR».
- **Activity / Movement:** filas con ícono en recuadro de vidrio; detalle en
  tarjeta principal.
- **Settings:** grupos en tarjetas de vidrio con filas de ícono + título +
  flecha o interruptor, menos texto; «Danger zone» es una fila que abre Reset.
  Se corrige el enlace de Privacy (apunta a la rama vieja `zkapriv-v2`).
- **Reveal, Change PIN, Wallets (+ diálogo de edición), Network, escáner QR:**
  mismos contenidos con los componentes nuevos (la linterna y el volver pasan a
  círculos de vidrio).

## 3. Cambios de función

1. **Direcciones (Receive).**
   - `security/.../vault/QuanticVault.kt`: marca de «publicada» por billetera
     **y por número de dirección** (hoy solo existe la de la principal y la de
     las extras se pierde al salir de la pantalla); `removeWallet` las borra.
   - `network/.../quantic/QuanticWallet.kt`: `addresses()` devuelve número,
     corta, **completa** y publicada (`ZokaStark.addresses` ya trae `address`);
     `publishExtra` guarda la marca con el id tomado al empezar, como
     `publishAddress`.
   - `ui/.../quantic/WalletViewModel.kt`: `addresses: StateFlow`, recarga al
     cambiar de billetera y un reintento automático cada 15 s para las que
     falten publicar (mismo patrón que `publishUntilKnown`). Sin botones.
2. **Restaurar en 24 casillas:** lógica pura en `quantic/PhraseEntry.kt`
   (sugerir por prefijo, repartir lo pegado, armar la frase) sobre
   `ENGLISH_WORDLIST` (`security/.../wallet/EnglishWordlist.kt`, la lista
   BIP-39 que ya trae la app; hoy solo la usa el `MnemonicManager` de la 2.x).
   El motor usa la misma lista (`bip39 = "2"`). Con pruebas JVM.
3. **Reset unificado** y **`PinGate`** (arriba).

## 4. Archivos

`zkapriv/ui/src/main/java/com/zoka/zkapriv/ui/`: `theme/ZkTheme.kt`,
`components/ZkComponents.kt`, `gallery/ZkGallery.kt`,
`qrscanner/QrScannerScreen.kt` y en `quantic/`: `Common.kt`,
`WalletNavigation.kt`, `Home.kt`, `Receive.kt`, `Send.kt`, `ProofMatrix.kt`,
`Lock.kt`, `Onboarding.kt`, `Settings.kt`, `Wallets.kt`, `NetworkScreen.kt`,
`QuanticApp.kt`, `WalletViewModel.kt`, `WalletDesignPreview.kt`; nuevos
`PhraseEntry.kt` y `Reset.kt`. Fuera de `ui`: `QuanticWallet.kt`,
`QuanticVault.kt`, `res/font/inter_light.ttf`, `app/build.gradle.kts`.

Para poder mirar cada pantalla sin abrir el almacén, Lock, Receive, Send y
Settings se parten en envoltorio + `*Content` sin estado, como ya hacen
`HomeContent`, `HistoryContent`, `NetworkContent` y `WalletsContent`, y
`WalletDesignPreview` suma un escenario por pantalla.

No se toca el código viejo de la 2.x (`ui/onboarding`, `ui/dashboard`, etc.):
no usa estos componentes.

## 5. Etapas (un commit por subetapa, compilar al cierre de cada etapa)

0. **Maqueta visual.** Página HTML con las pantallas clave (Welcome, Lock,
   Home, Receive, Send, Settings) en oscuro y claro, con estos tokens. Es lo
   único que espera tu OK: los colores no se pueden juzgar en texto.
1. **Sistema:** tema, tipografía, componentes, barra inferior, galería.
2. **Billetera:** Home, Receive (con el motor de direcciones), Send, Activity,
   Network, Wallets, escáner.
3. **Acceso:** Welcome, Backup, Verify, Restore en 24 casillas, PIN,
   Biometric, Lock, Reset.
4. **Ajustes:** Settings, Reveal, Change PIN, diálogos; vista de diseño con
   todos los escenarios.
5. **Cierre:** pruebas, capturas, versión 4.1.0 (código 20), entrada en
   `HOJA_RUTA_TECNICA.md` (zkapriv) y `HOJA-DE-RUTA-CLAUDE.md` (ZOKA), memoria
   de diseño de ZKAPriv actualizada, push de la rama a GitHub y GitLab.

Tras el OK de la maqueta, las etapas 1 a 5 corren seguidas. **No** se mezcla a
`main`, no se arma el APK firmado ni se publica nada sin tu OK aparte.

## 6. Verificación

- `./gradlew.bat :ui:testDebugUnitTest :network:testDebugUnitTest` (las de hoy
  más `PhraseEntryTest`) y `./gradlew.bat :app:assembleDebug`.
- Variante de depuración `com.zoka.zkapriv.benchmark` con `adb install -r`
  (conserva sus datos; la app real `com.zoka.zkapriv` no se toca).
- Cada pantalla en los dos temas con la vista de diseño (datos ficticios, sin
  FLAG_SECURE):
  `adb shell am start -S -n com.zoka.zkapriv.benchmark/com.zoka.zkapriv.MainActivity --es screen wallet-preview --es scenario <pantalla> --es theme dark|light`
  y `adb exec-out screencap -p`. Capturas en `c:\tmp\zkapriv-rediseno\`.
- En el SM-A135M (360 dp, letra 110 %): nada cortado (barra inferior,
  «QUANTIC TESTNET», botones), Receive entra sin scroll con 8 direcciones,
  teclado del PIN completo, casillas de Restore visibles sobre el teclado.
- Flujos reales en la variante de depuración sobre la testnet v3: crear,
  respaldar, verificar, PIN, huella; bloquear y desbloquear; Reset; restaurar
  tipeando, con sugerencias y pegando; crear direcciones y ver que se publican
  solas y que la marca sobrevive a cerrar la app; cambiar de billetera.
  El envío de prueba lo tocás vos y yo lo verifico por la API.
- Animaciones apagadas: nada se mueve. Contraste AA en texto (rosa sobre negro
  11:1, `#A8326A` sobre blanco 6:1) y zonas táctiles de 48 dp.
- Hoy el teléfono no está conectado por adb: hace falta enchufarlo para ver
  las etapas 1 a 5. Mientras tanto pruebo con el emulador que ya hay en la PC
  (lo abro y lo cierro yo, a 360 dp y letra 110 %); el APK es solo ARM, así
  que puede no aceptarlo, y en ese caso las capturas esperan al teléfono.

## Notas

- Las marcas de «publicada» no saben de qué red son: en el próximo reinicio de
  red hay que limpiarlas junto con el estado (ya pasa hoy con la principal).
- Publicar la 4.1.0 (APK firmado, instalar sobre la app real, release, web,
  Discord) es otra tarea y pide OK explícito.
