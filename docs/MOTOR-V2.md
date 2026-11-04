# Motor v2 de ZKAPriv 3 (E2)

Cómo habla ZKAPriv 3 con Zoka Quantic Testnet v2. El plan general está en
[PLAN-ZKAPRIV-3.md](PLAN-ZKAPRIV-3.md).

## Las piezas

| Pieza | Dónde | Qué hace |
|---|---|---|
| Billetera liviana | `crates/zoka-pq-pool/src/light.rs` (repo del nodo) | Rearma el árbol de notas desde páginas, sigue las notas propias, saldo, historial, planifica y prueba el pago |
| Páginas `ScanPage2` | `crates/zoka-pq-pool/src/scan.rs` | Salidas, nulificadores y padre de cada bloque, hasta 100 bloques |
| `/scan2` | `src/quantic/stark_api.rs` | Las páginas por HTTP (API local y pública) |
| `SCAN_PAGE` | `src/quantic/stark_net.rs` | Las mismas páginas por P2P, también por la .onion |
| `stark_mobile` | `src/quantic/stark_mobile.rs` | Conectar a una semilla (directo o solo por Tor), página, enviar, resolver y publicar la dirección corta |
| Puente JNI | `zkapriv/native/src/stark.rs` (feature `quantic-pq`) | JSON de entrada y salida; estado cifrado; prueba en un hilo con 64 MB de pila |
| Envoltorio Kotlin | `network/.../nativebridge/ZokaStark.kt` | Llamadas tipadas; un `{"error"}` es una `StarkException` |

Las ramas: `main` en el repo del nodo (motor integrado en 4.0.4) y
`zkapriv-v2` en este.

## Reglas

- **La frase es la de ZSilent:** 24 palabras BIP-39 en inglés; su entropía de
  32 bytes es la semilla de `Account::from_seed(semilla, red, 0)`. La misma
  frase da la misma billetera en los dos lados.
- **La prueba STARK se hace en el teléfono.** A la red solo va la
  transacción terminada.
- **El estado** (árbol, notas, historial, pago pendiente) se guarda en
  `state_dir/stark-<chain_id>.bin`, cifrado con ChaCha20-Poly1305 y una
  clave que sale de la semilla. Sin la frase el archivo no dice nada. Se
  escribe en un temporal y se renombra.
- **Una operación a la vez** sobre el estado (un candado en Rust): mientras se
  prueba un pago, sincronizar espera.
- **Tor es opcional.** Con `tor_proxy` (el SOCKS de Arti,
  `ZokaNativeNetwork.startTor`) todo va solo a las semillas .onion: páginas
  por `SCAN_PAGE`, envío y directorio. Si ninguna contesta, no sale nada.
- **Sin Tor**, las páginas vienen primero de `api.zokascan.cc/scan2`, luego
  de `zokascan.cc/scan2` si sirve páginas binarias, y entran con `addPages`;
  si fallan ambas fuentes, se usa P2P con `sync`. Las páginas vacías también
  se procesan: actualizan la punta. Las consultas evitan respuestas cacheadas.
- **Pago pendiente:** sus notas quedan reservadas hasta que se mina o vence
  (altura + 20). Si la semilla lo rechaza, se liberan. Si no se sabe si
  llegó, quedan reservadas hasta el vencimiento. Un pago a la vez.
- **Cambio de rama:** la billetera vuelve a una copia asentada (100 a 200
  bloques bajo la punta) y relee desde `next`.
- **Lo leído se verifica (3/11):** cada página trae la cabecera de su bloque
  (`merkle_root`, `state_root`, `nonce`). La billetera recalcula el estado que
  la cabecera compromete (raíz del árbol, notas, emisión, acumulador de
  nulificadores) y el hash del bloque (`zoka_pq_pool::commitment`, igual al de
  consenso por prueba): una página que esconde o inventa una salida se
  rechaza. Después confirma la cadena contra el último checkpoint del equipo
  (firma híbrida Ed25519 + ML-DSA-65, obligatoria en la v2): `verified` dice
  hasta qué bloque. `sync` lo hace solo con la semilla; por HTTPS, la app
  trae `/checkpoint` y llama a `confirmCheckpoint`. Si el checkpoint nombra
  otro bloque, la billetera vuelve a empezar. Una coinbase escondida se nota
  recién al madurar, porque antes no entra al árbol (ni se puede gastar).
- **Estado de otro formato:** se descarta y se vuelve a leer la cadena (es
  una caché; la frase es lo único que importa).
- **Las semillas son solo la primera puerta (3/11):** en cada `sync` el nodo
  comparte los pares que conoce (`PEERS`) y la app los guarda en
  `state_dir/peers-<chain_id>.json` (sin cifrar: son datos públicos), hasta
  64. Cada conexión prueba hasta 2 pares que respondieron en la última
  semana, después las semillas (en orden al azar) y al final hasta 4 de los
  otros. Se guardan solo el id y las .onion, nunca IPs ni hosts de afuera: un
  nodo malicioso no puede hacer que los teléfonos marquen la IP de un
  tercero. Una lista llena de pares inventados no desplaza a los que
  respondieron. Lo que sirve un par aprendido se verifica igual que lo de una
  semilla (cabeceras y checkpoint).

## Funciones (`ZokaStark`)

| Kotlin | Devuelve |
|---|---|
| `newPhrase()` | 24 palabras nuevas |
| `walletInfo(phrase)` | `address`, `short`, `chain_id` |
| `viewKey(phrase)` | `zqtestview2…` |
| `checkAddress(text)` | `valid`, `kind` (`full` / `short`) |
| `status(phrase, stateDir)` | saldo, historial, `next`, `in_sync`, `pending` (sin red) |
| `addPages(phrase, stateDir, pages)` | lo mismo, después de sumar páginas HTTPS |
| `sync(phrase, stateDir, torProxy)` | lo mismo, tras hasta 20 páginas por P2P y el checkpoint del nodo, y `peers` (cuántos pares conoce la app) |
| `confirmCheckpoint(phrase, stateDir, json)` | lo mismo, tras confirmar un checkpoint traído por HTTPS |
| `quote(phrase, stateDir, amount)` | `enough`, `fee_atoms`, `inputs`, `max_sendable_atoms` |
| `send(phrase, stateDir, to, amount, memo, torProxy)` | `txid`, `fee_atoms`, `expiry`, `proving_ms` |
| `sendPhase()` | 0: preparación/espera; 1: conexión; 2: prueba; 3: transmisión. No toma el candado ni expone secretos |
| `resolve(short, torProxy, stateDir?)` | la dirección completa o null |
| `publish(phrase, torProxy, stateDir?)` | si el nodo la tomó |

`to` puede ser la dirección completa o la corta; la corta se resuelve en el
directorio de la red y se comprueba contra la corta.

## Compilar

```powershell
.\native\build-android.ps1 -Features "quantic-pq"
```

Arma arm64-v8a y armeabi-v7a (el teléfono de pruebas es de 32 bits). Los
`.so` de la 2.1.4 quedaron respaldados en
`C:\Users\mderramus\ZOKA-respaldos\zkapriv-jniLibs-2.1.4-20261030`.

En Linux, con NDK r27d y libclang de LLVM instalado:

```bash
ANDROID_NDK_HOME=/opt/zoka-android/android-ndk-r27d bash native/build-android-linux.sh
```

El script arma primero armeabi-v7a y luego arm64-v8a. Copia el motor y su
`libc++_shared.so`, verifica los símbolos JNI y muestra hashes. No empaquetar
los `.so` intermedios de iroh. `JNI_OnLoad` inicializa `ndk_context` para el
DNS Android antes de abrir conexiones.

## Integración Android del 3/11

`QuanticWallet` ya trae `/checkpoint` después de las páginas HTTPS y llama a
`confirmCheckpoint`. Home muestra la altura verificada y los pares conocidos.
El mismo estado observable alimenta Home, Activity y Receive; la publicación
de una dirección solo se marca exitosa si el nodo la acepta.

Un solo ciclo actualiza cada 10 segundos, cada 4 con un pago pendiente y
cada segundo mientras alcanza la punta. Un error espera 15 segundos y vuelve
a intentar. Volver a la app o terminar un envío pide una lectura inmediata.
Los pedidos manuales se agrupan y no se superponen con el envío. Su resultado
y las reservas se conservan aunque se cambie de pestaña o se bloquee la app.

Las semillas permanentes seed2/seed3 ya corren 4.0.4. La validación del APK
nuevo está en curso: pruebas JVM aprobadas y diseño comprobado en el Samsung
SM-A135M; faltan terminar ambos motores y ejecutar la prueba instrumentada
de HTTPS, P2P y Tor. Usa una billetera efímera aislada, sin leer el almacén
del usuario ni enviar pagos. Los envíos y recepción reales los prueba el
usuario antes de publicar.
