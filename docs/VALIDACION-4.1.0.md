# ZKAPriv 4.1.0: rediseño negro y vidrio

Trabajo del 13/11/2026, rama `rediseno-vidrio`. Continúa los commits de
Claude `3fbc06b` (direcciones) y `3550b61` (restauración).
Plan: [PLAN-REDISENO-4.1.md](PLAN-REDISENO-4.1.md).

## Qué cambia

- Tema oscuro y claro con botones negros, letras rosa, tarjetas con
  degradé, Inter y JetBrains Mono. Se agrega Inter Light 4.0, la misma
  versión de los otros pesos que ya estaban en el proyecto.
- Se mantienen las cinco pestañas y el inicio centrado en saldo y red.
  Welcome, respaldo, verificación, restauración, PIN, biometría, bloqueo,
  envío, recepción, actividad, ajustes, red y billeteras usan el tema nuevo.
- Receive muestra una dirección y un QR a la vez. La dirección elegida
  queda visible en el selector, también con las ocho direcciones creadas.
- Settings y «Forgot your PIN?» llevan a la misma pantalla Reset. Para
  quitar las billeteras hay que confirmar que se tienen las palabras; la
  acción queda deshabilitada mientras se envía. Durante el borrado se
  bloquea volver para no cortar la operación desde la navegación.
- Reveal y Change PIN usan el mismo teclado y espera por intentos fallidos
  que Lock y Send. El enlace de Privacy apunta a `main`.
- Los diálogos de billeteras usan la hoja común, con desplazamiento y
  espacio para el teclado.
- Versión 4.1.0, código 20. No cambia el motor nativo ni la red v3.

## Verificación

Con JDK 17, `--no-daemon --max-workers=2`:

```text
:ui:testDebugUnitTest :network:testDebugUnitTest
:app:assembleDebug :app:assembleDebugAndroidTest
```

Pasada final: **28 pruebas de UI y 47 de red**, sin fallas. Compilaron
ambos APK (`build-keyboard.log`, 2 min 27 s). **Las seis pruebas instrumentadas
pasaron en el Samsung**, 28,573 s, sin fallas (`instrumented-final.log`).
Se revisaron 22 pantallas en claro y oscuro, con 44 capturas; Receive y el
diálogo de edición se volvieron a capturar con el último ajuste. Se incluye
una captura adicional de edición con teclado abierto.

La revisión final encontró que Android desplazaba el diálogo al abrir el
teclado y tapaba sus botones. Se corrigió pasando los insets al contenedor
de la hoja y usando `decorFitsSystemWindows = false`; la sexta prueba abre
el teclado y guarda el nombre ficticio con el botón Save.

APK final de prueba: `ZKAPriv-4.1.0-testnet-debug.apk`, SHA-256
`3a2d17f945188465301999d5b1c34acc7bea076c2fe3ad84bc11fed9fbc5bbe3`.

Dispositivo: Samsung SM-A135M, Android de 32 bits, 360 dp, letra al 110 %.
Instalación con `adb install -r`, paquete `com.zoka.zkapriv.benchmark`, nombre
visible **ZKAPriv Testnet**. La app `com.zoka.zkapriv` conserva su instalación.

`WalletRedesignInstrumentedTest` abre exclusivamente `wallet-preview` con
datos ficticios. Comprueba Reset con y sin envío, el acceso desde Lock,
el selector con ocho direcciones, la distribución de una frase pública de
prueba en las 24 casillas y guardar desde el diálogo con el teclado abierto.
No abre el almacén, no consulta el portapapeles y
no hace pagos ni borra billeteras reales.

Los dos motores incluidos coinciden por SHA-256 con `native/jniLibs`:

| ABI | SHA-256 de libzoka_mobile.so |
| --- | --- |
| armeabi-v7a | `04b40bd277f7015248c00871f32319b8ffb35fb9949eb5145aa45736044f46c8` |
| arm64-v8a | `5cf624180550f2f4d1b816fc2b3dd77229d9010e5c82fc55303fc5f4e738bc75` |

Evidencia local: `ZOKA/target/zkapriv-rediseno-2026-11-13/`, con registros
de Gradle, capturas de los dos temas, script de capturas y parche previo de
Claude. Las capturas usan únicamente la vista de diseño.

## Alcance

No se prueban envío, restauración de una billetera real, biometría del
usuario ni borrado de sus datos. Esas acciones las prueba el usuario.
No hay APK release firmado, integración a `main`, release ni despliegue
web; publicar sigue necesitando su OK.
