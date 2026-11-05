# Validación de ZKAPriv 3.0.0

Estado al 3/11/2026: interfaz y pruebas JVM terminadas; motor Android nuevo
en compilación. **Todavía no es un APK aprobado para publicar.**

## Cambios revisados

Home concentra saldo, actualización y privacidad Quantic. Send, Receive y
Activity tienen pestañas inferiores. La prueba muestra una matriz de 16×16
celdas, respeta movimiento reducido y termina con check rosa al aceptar o
error rojo. La aceptación se distingue de la inclusión en un bloque.

El saldo se consulta periódicamente, al volver a la app y después de un
envío. Un error transitorio no termina el ciclo. Los montos usan seis
decimales; se admite coma decimal y se rechazan separadores ambiguos.
La lectura HTTPS confirma el checkpoint híbrido y muestra la altura verificada.

La interfaz completa está en inglés por pedido del usuario. El Samsung
SM-A135M (Android 14 de 32 bits, armeabi-v7a) muestra correctamente Home y
la matriz en los temas oscuro y claro. Las capturas de revisión usan
**datos ficticios**, nunca el almacén de una billetera real.

## Pruebas ya ejecutadas

`JAVA_HOME=C:/Program Files/Java/jdk-17`, Gradle sin daemon permanente y
máximo dos workers:

```text
:app:assembleDebug :app:assembleDebugAndroidTest
:ui:testDebugUnitTest :network:testDebugUnitTest
```

Resultado: build correcto, **53 pruebas sin errores ni fallas** (39 de red y
14 de UI). Incluyen cuatro casos del ciclo de actualización, dos de montos,
cuatro de enlaces de pago y seis de QR multiparte. Las pruebas anteriores de
red siguen aprobadas, aunque no sustituyen la validación del motor STARK.

Registros y capturas locales de esta sesión:
`ZOKA/target/zkapriv-3-review/gradle-final-ui.log`, `home-final-dark.png`,
`home-en-light.png`, `proof-en-dark.png` y `proof-final-light.png`.

## Próxima comprobación en el teléfono

Después de copiar los motores nuevos de **ambos ABI**, compilar el APK y el
APK de instrumentación. Instalar con `adb install -r` para conservar datos.
Ejecutar únicamente:

```text
adb shell am instrument -w -e class com.zoka.zkapriv.StarkReadOnlyInstrumentedTest com.zoka.zkapriv.benchmark.test/androidx.test.runner.AndroidJUnitRunner
```

Esta prueba crea una billetera efímera en un directorio temporal exclusivo.
Lee la cadena real por HTTPS, vuelve a consultar después de alcanzar la
punta, verifica el checkpoint, prueba P2P y Arti/Tor. No publica una dirección,
no envía transacciones y no accede al almacén del usuario.

El usuario hace la prueba de recepción y envío con su billetera de testnet,
primero directo y después por Tor. Comprobar el saldo sin cerrar la app, la
matriz durante la prueba, el resultado y la posterior confirmación. Medir
tiempo y memoria de la prueba real sin registrar secretos.

## Paquetes y conservación de datos

La instalación `com.zoka.zkapriv` del Samsung es la 2.1.3 de la testnet v1;
no se reemplazó. `com.zoka.zkapriv.benchmark` estaba instalada desde el 30/10
y conserva sus datos al actualizar. Su nombre visible pasa a **ZKAPriv
Testnet**. No desinstalar ni limpiar datos para probar.

Antes de publicar: APK release con el keystore existente, comparación de su
certificado con el instalado, comprobación de los dos motores empaquetados y
SHA-256 del APK. No mezclar un motor nuevo con otro ABI anterior. La firma
se hace en la PC; las claves no se copian al VPS ni se imprimen.

## Resultado del 4/11/2026: aprobado para publicar

- **Prueba del usuario con dinero de la testnet**, con ZKAPriv Testnet
  (`com.zoka.zkapriv.benchmark` 3.0.0) en el Samsung: ZSilent → billetera web,
  billetera web → ZKAPriv y ZKAPriv → ZSilent. Los tres pagos se minaron
  (bloques 4789, 4791 y 4809, de 1,94 MB cada uno) y llegaron.
- **APK release** armado otra vez desde `e356c24` con el árbol limpio, después
  de sacar de `native/jniLibs/armeabi-v7a` tres restos ignorados por git que se
  empaquetaban sin que nada los cargara (`libif_watch-…`, `libiroh-…`,
  `libiroh_relay-…`; guardados en `target/zkapriv-3-review/`). Los motores
  dependen solo de `libc`, `libm` y `libdl`.
  - `com.zoka.zkapriv`, versionCode 18, versionName 3.0.0.
  - Firma v2 con el mismo certificado que la 2.1.3 instalada
    (`061376f8c4e3bc54a01ccfa7279124568cfdffe4c23f8625b42f673eddc64532`):
    se instala encima.
  - `libzoka_mobile.so`: arm64-v8a `38cabdd5c566…` (la compilación del VPS 2
    registrada) y armeabi-v7a `3ed07e893e6e…` (3/11), iguales a `jniLibs`.
  - SHA-256 del APK:
    `e701a319c5e565e0568b37c5ab21f39c69cfc0ccf522bf980904b99cd079ba04`.
