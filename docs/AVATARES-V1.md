# Avatares y panel de ZKAPriv

Pedido del 14/11/2026. Rama `rediseno-vidrio`. El usuario reemplaza la
propuesta anterior de Home por su imagen «Panel de billetera cripto ZKA»:
cuenta arriba, saldo grande, curva blanca, anillo de red y cuatro accesos.
La imagen dentro del anillo se reemplaza por un mosaico plano de la cuenta.

## Identidad y límites

Se usa **la dirección pública corta principal, índice 0**, en su formato
canónico completo y respetando mayúsculas/minúsculas. No se usa la versión
abreviada que muestra la pantalla, el nombre, el id local `main`/aleatorio,
ni la dirección extra seleccionada en Receive.

La inspección de `native/src/stark.rs` (`accounts`, `address_json`) y
`crates/zoka-pq-pool/src/wallet.rs` (`Account::from_seed`, `Address::short`)
confirma que restaurar la misma cuenta en la misma red reproduce esa
dirección. Esas funciones **no se modifican**. El avatar no recibe ni deriva
semillas, frases, claves de gasto o claves de vista.

`QuanticWallet.addresses()` ya obtiene esas direcciones para Receive. Al
leerlas conserva la principal como metadato `public_account_v1` en las
preferencias cifradas existentes, asociado al id local. `WalletEntry`
agrega `publicAccountId` y el ViewModel refresca ese dato para Compose.
Renombrar no lo altera; quitar la cuenta elimina también el metadato;
restaurar reconstruye el mismo dato por el flujo habitual.

Las cuentas anteriores a este cambio reciben el metadato cuando se abren
por primera vez. Hasta entonces se dibuja una cuadrícula neutra, sin
inventar identidad ni abrir las frases de las cuentas inactivas. No hay
nuevos pedidos de red, archivos de imagen, caché de bitmaps ni dependencias.

## Especificación congelada de v1

- Entrada: identificador público canónico, sensible a mayúsculas, UTF-8.
  El generador acepta de 1 a 256 caracteres ASCII de `A-Z a-z 0-9 : . _ -`.
  Esto es una validación del identificador visual, no del checksum de una
  dirección de pago. No normaliza, recorta ni convierte a minúsculas.
- Semilla: `SHA-256(UTF8("ZKAPriv/avatar/v1/seed\0") || UTF8(accountId))`.
- Bloque de expansión n:
  `SHA-256(UTF8("ZKAPriv/avatar/v1/grid\0") || seed || uint32_be(n))`,
  empezando en cero. `\0` significa un byte nulo.
- Se recorren los bytes sin signo; se descarta 255 y se aplica módulo 5 a
  los demás. Los primeros 81 resultados se colocan por filas, de izquierda
  a derecha y de arriba abajo. Sin simetrías ni PRNG dependiente del sistema.
- Paleta fija sRGB, por índice: `#080809`, `#303036`, `#AAAAAF`, `#FAFAFA`,
  `#F2ABD0`. Fondo `#101012`. No cambia con el tema.
- Id vacío o inválido: `null`; Canvas dibuja 81 casillas gris oscuro y la
  descripción accesible «Account avatar unavailable».
- Canvas alinea los cuadrados a píxeles físicos y reserva separaciones;
  no hay perspectiva, degradados en las casillas ni animación.

Vector de compatibilidad, calculado también con Python hashlib:

```text
accountId = public-account:alice
432401304002143331324404132221311134034044240240320441133232224123324403241422013
```

Hay un número finito de patrones (`5^81`). El hash y el espacio visual hacen
poco probable una coincidencia, pero **no garantizan unicidad absoluta**.
El avatar ayuda a reconocer una cuenta, no verifica destinatarios ni
reemplaza comprobar la dirección. Una futura versión debe conservar v1
para las identidades existentes; no cambiar sus constantes silenciosamente.

## Integración visual

`ZKAPrivAvatar(accountId: String, modifier: Modifier)` dibuja exclusivamente
el patrón producido por `AvatarMosaic.generate`. Wallets lo muestra a 40 dp en
cada fila y la hoja Network lo pone en el centro del anillo (19 % del ancho).
**El selector de cuenta del inicio no lleva mosaico** (pedido del 14/11): es
una cápsula fina con el ícono de billetera, el nombre y la dirección corta.
El tema claro conserva su elección de colores, pero no modifica el avatar.

### Inicio limpio y hojas (20/11/2026)

El inicio quedó en: selector fino, engranaje, saldo (tocarlo lo oculta) y la
curva de movimientos con un degradé **de la línea hacia arriba** que lleva una
lluvia de dígitos 0-9. Lo quieto (degradé, resplandor y campo tenue de
dígitos) se arma una vez por tamaño y curva; solo las gotas se redibujan, a
unos 16 cuadros por segundo, y se frenan sin animaciones (ajuste de la app o
del sistema), en segundo plano y en las pruebas.

La curva ya no desaparece por un dato raro: lo sin confirmar o de tipo
desconocido se salta, un historial cortado deja el saldo de antes en 0 y, sin
movimientos, la línea va plana. Con el saldo oculto también va plana. La
variación (↗ % y ZKA) solo aparece con movimientos. Sigue sin ser
rentabilidad de mercado.

La barra tiene cinco accesos: Wallet, Receive, Send, **Network** (el anillo) y
Activity. Receive y Network no cambian de pantalla: abren `WindowSheet`, una
ventana que sube desde abajo con **maximizar a la izquierda y minimizar a la
derecha**; también se arrastra (arriba maximiza, abajo achica o cierra), y
atrás o tocar afuera la cierran. El anillo muestra altura, hashrate,
sincronización y dificultad con datos reales («—» si faltan); cada dato se
apoya en su guía sin cruzar las letras. «Network details» lleva a la pantalla
de red de siempre.

## Pruebas y visualización

Pruebas JVM: vector conocido, 81 casillas y paleta, determinismo, locale,
orden de llamadas, muestra de 5.000 ids distintos (sin pretender demostrar
unicidad), entradas inválidas y límite de dependencias. Metadatos con
preferencias simuladas que rechazan lecturas de claves/frases; reapertura,
renombrado y misma cuenta con distintos ids locales. Historial y datos de
red tienen sus pruebas de valores reales y ausentes.

Las instrumentadas abren solo la vista debug con datos ficticios: verifican
los 81 colores en 40/64/80 dp, igualdad de píxeles tras recrear la actividad,
placeholder y selección de dos cuentas. No abren el almacén del usuario.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
.\gradlew.bat --no-daemon --max-workers=2 :ui:testDebugUnitTest :network:testDebugUnitTest :security:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb shell am instrument -w -e class com.zoka.zkapriv.AvatarInstrumentedTest,com.zoka.zkapriv.WalletRedesignInstrumentedTest com.zoka.zkapriv.benchmark.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -S -W -n com.zoka.zkapriv.benchmark/com.zoka.zkapriv.MainActivity --es screen wallet-preview --es scenario home --es theme dark
# Hojas: --es scenario sheet-network | sheet-receive | sheet-receive-full
```

En la app normal: desbloqueá, tocá el nombre de la cuenta para ir al
selector y el ícono del anillo de la barra para ver el mosaico. En debug también existen `avatar-sizes`, `home-loading`,
`home-tor`, `home-large`, `wallets` y los escenarios anteriores.
La compilación de prueba conserva el paquete separado
`com.zoka.zkapriv.benchmark` (ZKAPriv Testnet). No se publica una release.

Resultados y capturas finales: pendientes de completar al terminar la validación.

## Archivos de esta implementación

| Archivo (relativo a este repo) | Cambio |
| --- | --- |
| `ui/src/main/java/com/zoka/zkapriv/ui/avatar/AvatarMosaic.kt` | Generador puro y contrato v1 |
| `ui/src/main/java/com/zoka/zkapriv/ui/avatar/ZKAPrivAvatar.kt` | Canvas reutilizable |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/Dashboard.kt` | Home basado en la referencia |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/BalanceHistory.kt` | Serie visual de movimientos reales |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/Home.kt` | Home pasa a Dashboard; conserva actividad y tarjeta de red reutilizada |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/HomeFormat.kt` | Unidad GH/s |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/WalletNavigation.kt` | Cuatro accesos de la referencia |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/QuanticApp.kt` | Engranaje hacia Settings |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/Wallets.kt` | Mosaico junto al nombre |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/WalletViewModel.kt` | Refresca metadato público después de leer direcciones |
| `ui/src/main/java/com/zoka/zkapriv/ui/quantic/WalletDesignPreview.kt` | Datos ficticios, selección y tamaños de prueba |
| `ui/src/main/java/com/zoka/zkapriv/ui/theme/ZkTheme.kt` | Verde de variación positiva del panel |
| `network/src/main/java/com/zoka/zkapriv/network/quantic/QuanticWallet.kt` | Conserva dirección ya obtenida; reutiliza respuesta de red |
| `network/src/main/java/com/zoka/zkapriv/network/quantic/NetworkDisplayStats.kt` | Extrae hashrate y dificultad para mostrar |
| `security/src/main/java/com/zoka/zkapriv/security/vault/QuanticVault.kt` | Campo de metadato público; no cambia cifrado ni derivación |
| `ui/src/test/java/com/zoka/zkapriv/ui/avatar/AvatarMosaicTest.kt` | Algoritmo y límites |
| `ui/src/test/java/com/zoka/zkapriv/ui/quantic/BalanceHistoryTest.kt` | Serie y datos ausentes |
| `ui/src/test/java/com/zoka/zkapriv/ui/quantic/HomeFormatTest.kt` | GH/s |
| `network/src/test/java/com/zoka/zkapriv/network/quantic/NetworkDisplayStatsTest.kt` | Datos opcionales del panel |
| `security/src/test/java/com/zoka/zkapriv/security/vault/PublicAccountMetadataTest.kt` | Persistencia simulada y rechazo de lectura de secretos |
| `app/src/androidTest/java/com/zoka/zkapriv/AvatarInstrumentedTest.kt` | Píxeles, recreación y selector |
| `app/src/androidTest/java/com/zoka/zkapriv/WalletRedesignInstrumentedTest.kt` | Reconoce el nuevo saldo al volver del diálogo |
| `docs/AVATARES-V1.md`, `HOJA_RUTA_TECNICA.md` | Especificación y registro |

También se actualizan `docs/TRASPASO.md` y `HOJA-DE-RUTA-CLAUDE.md` del
repositorio padre. No se cambian el código nativo ni sus bibliotecas `.so`.
