# Hoja de Ruta Tecnica - zkapriv Android

## Objetivo

Convertir `zkapriv` en la wallet Android de Zoka, manteniendo solo el motor y las pantallas que aplican a esta blockchain:

- crear y restaurar wallet
- importar scan key
- desbloquear wallet
- ver balance ZKA
- recibir ZKA
- enviar ZKA
- escanear QR
- gestionar cuentas locales
- consultar actividad basica
- conectarse a un nodo Zoka

Quedan fuera de alcance las secciones que no aplican a Zoka: galerias, conectores externos, swaps, staking, tokens externos y navegadores integrados.

## Principios

- No mezclar protocolo viejo con Zoka.
- No dejar compatibilidad silenciosa con formatos de address o clave que Zoka no usa.
- Mantener UI, storage seguro, wallet/crypto y red separados.
- Priorizar una wallet basica funcional antes de extras.
- Zoka es privacy-first: la app no debe prometer datos que la cadena no publica.
- Todo nombre de paquete, clase, documentacion visible y branding debe apuntar a Zoka/zkapriv.

## Arquitectura Actual

Modulos:

- `app`: Android app, manifest, theme y navegacion principal.
- `core`: modelos, constantes y utilidades.
- `security`: almacenamiento seguro, mnemonics, derivacion, biometria y wallet manager.
- `network`: API y repositorio REST de Zoka.
- `price`: precio/cache para ZKA.
- `ui`: pantallas Compose y view models.

## API Zoka Detectada

Endpoints REST principales:

- `GET /health`
- `GET /stats`
- `GET /network/runtime`
- `GET /chain/height`
- `GET /chain/state-root`
- `GET /accounts/:address`
- `GET /accounts/:address/proof`
- `POST /accounts/proof/verify`
- `GET /blocks/:height`
- `GET /blocks/latest`
- `GET /blocks/:height/transactions`
- `GET /blocks/:height/private_tx_hashes`
- `GET /transactions/:hash`
- `POST /transactions`
- `GET /mempool/size`
- `GET /mempool/pending`
- `GET /private/tx/:hash`

Contrato de envio actual:

```text
TransactionRequest:
  sender: String
  receiver: String
  amount: u64
  nonce: u64
  priority_fee: Option<u64>
  signature_hex: Option<String>
  public_key_hex: Option<String>
```

Nota critica: el envio publico actual todavia firma con Ed25519 hasta que el nodo exponga o confirme el flujo definitivo para transferencias privadas `zka1...`.

## Fase 0 - Build y Ambiente Android

Objetivo: compilar localmente antes de distribuir APK.

Pendiente:

- Instalar Android SDK completo.
- Crear `local.properties` con `sdk.dir=...`.
- Verificar `platforms`, `build-tools` y `cmdline-tools`.
- Ejecutar `.\gradlew.bat :app:assembleDebug`.

Estado: **BLOQUEADA**. Solo se encontro `platform-tools`; falta SDK completo.

## Fase 1 - Identidad Android

Objetivo: que la app se presente como `zkapriv`.

Estado: **COMPLETA**

Realizado:

- Paquete `com.zoka.zkapriv`.
- `applicationId` y namespaces migrados.
- App name `zkapriv`.
- Root Gradle project `ZkaPriv`.
- Application class `ZkaPrivApplication`.
- Tema Compose `ZkaPrivTheme`.
- Deep link `zoka:`.
- Links externos apuntando a ZokaNetwork.
- Referencias del autor anterior cambiadas por `Natosh Zoka`.

Pendiente:

- Logo final Zoka.
- Icon launcher final.
- Screenshots/documentacion visual final.

## Fase 2 - Capa de Red Zoka

Objetivo: usar cliente REST Zoka.

Estado: **COMPLETA**

Realizado:

- `ZokaNodeApi.kt`: Retrofit REST.
- `ZokaRestModels.kt`: modelos de respuesta.
- `ZokaRepository.kt`: balance, status, envio, fee, cache e historial contra Zoka.
- Eliminada la API JSON-RPC anterior.
- `NetworkModule.kt` solo provee `ZokaNodeApi`.

Pendiente:

- Selector de endpoint en Settings.
- Confirmar contrato final de envio privado.

## Fase 3 - Wallet, Claves y Direcciones

Objetivo: derivacion Zoka pura.

Estado: **COMPLETA EN CODIGO**

Derivacion canonica:

```text
scan_key = raw BIP-39 entropy
spend_key = SHA256("zoka-private-wallet-spend-v1" || 0x1f || scan_key)
address = zka1 + Base58(scan_pub || spend_pub || checksum)
receive material = zpriv:<scan_pub_hex>:<spend_pub_hex>
```

Realizado:

- `WalletManager.deriveAddress()` deriva `zka1...`.
- `WalletManager.getScanKey()` devuelve la `scan_key`.
- `WalletManager.getScanKeyHex()` exporta la `scan_key` hexadecimal.
- `WalletManager.importFromScanKeyHex()` reconstruye la misma `zka1...`.
- `EnglishWordlist.kt` usa la wordlist oficial BIP-39.
- `Ristretto255.kt` usa constantes y compresion compatibles con `curve25519-dalek`.
- `AccountManager` persiste `scanKeyHex`.
- Onboarding importa mnemonic o scan key sin formatos viejos.
- Settings revela scan key y `zpriv` con nombres Zoka.

Pendiente:

- Tests unitarios: seed -> address, scan key -> address, checksum, QR round-trip.

## Fase 4 - Envio y Recepcion

Objetivo: flujo basico de uso.

Estado: **CODIGO LISTO, PENDIENTE SDK/NODO**

Realizado:

- Balance ZKA desde `GET /accounts/:address`.
- `SendScreen`: label ZKA y placeholder `zka1...`.
- `SendViewModel`: obtiene `scanKeyHex` desde cuenta activa o wallet original.
- `ZokaRepository.sendTransaction()`: firma y transmite al nodo.
- `ReceiveScreen`: direccion `zka1...` y QR `zoka:`.
- Historial local por wallet.
- Fee plano inicial `0.001 ZKA`.

Pendiente:

- Probar crear wallet -> recibir fondos -> enviar ZKA en nodo local/testnet.
- Alinear envio publico/privado con contrato final del nodo.

## Fase 5 - Eliminacion de Secciones Que No Aplican

Objetivo: reducir superficie y evitar mezcla con el proyecto base.

Estado: **COMPLETA**

Eliminado:

- UI de galerias.
- UI de navegadores integrados.
- UI de staking.
- UI de swaps.
- UI de conectores externos.
- Repositorios de galerias y staking.
- Modelos de galerias, staking y navegadores integrados.
- Tokens externos y portfolio por contratos.
- Pruebas y documentos de derivacion que usaban formatos anteriores.

Verificacion esperada:

```bash
rg -n "<modulos descartados>" network/src core/src ui/src
```

Resultado esperado: 0 coincidencias.

## Fase 6 - Branding y Pulido UI

Estado: **PENDIENTE**

Tareas:

- Logo final Zoka.
- Paleta final Zoka.
- Iconos launcher definitivos.
- Privacy policy actualizada.
- Empty states revisados.
- Textos finales de onboarding/settings.
- Screenshots actualizadas.

## Fase 7 - Seguridad y Privacidad

Estado: **COMPLETA EN CODIGO**

Realizado:

- Logs HTTP reducidos en release.
- Eliminados logs sensibles de derivacion.
- `allowBackup=false`.
- `network_security_config.xml`: HTTP solo para localhost/emulador.
- `FLAG_SECURE` aplicado en pantallas sensibles.
- Storage cifrado para mnemonic y scan key.

Pendiente:

- Auditoria manual antes de release.
- Pruebas en dispositivo real.

## Fase 8 - Verificacion Final

Pendiente:

- Compilar APK debug.
- Ejecutar app en emulador/dispositivo.
- Validar import de seed.
- Validar import de scan key.
- Confirmar que ambas rutas devuelven la misma `zka1...`.
- Enviar transaccion en nodo local/testnet.
- Revisar que no haya referencias activas a formatos o secciones eliminadas.
