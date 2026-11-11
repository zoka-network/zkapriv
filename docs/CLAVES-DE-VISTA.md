# Claves de vista: integración del 10/11/2026

Rama `endurecimiento-sin-consenso`. No publicada ni instalada.

## Uso y actualización

La actualización conserva billeteras, PIN, biometría, direcciones y estado
de sincronización. No hay que restaurar, crear claves, configurar servicios
ni volver a leer la cadena. El motor actualizado marca el vuelto de los
nuevos pagos automáticamente, mediante la biblioteca compartida del nodo.

En **Settings → View keys**, el PIN o la biometría protegen el acceso como
antes. Se abre siempre **Incoming**: una clave de alcance limitado, derivada
localmente de la billetera existente. **Full** se elige explícitamente.
Compartir una clave sigue siendo una decisión del usuario: ninguna se manda
a servicios externos automáticamente.

- **Incoming view key** permite ver entrantes y memos, pero no gastos ni
  saldo. No puede gastar. La pantalla aclara que el vuelto anterior a la
  actualización no se puede distinguir de otros entrantes.
- **Full view key** permite ver entrantes, gastos y saldo, sin poder gastar.
- Todo el texto de la interfaz está en inglés.

La derivación corre fuera del hilo de la interfaz. Espera el mismo candado
que sincronizar y cambiar de billetera, comprueba la cuenta antes y después,
y descarta resultados cancelados. Al cambiar de billetera se descartan la
autenticación de esa pantalla, el alcance elegido y la clave mostrada.

## Contrato nativo y compatibilidad

Se conserva `ZokaStark.viewKey0`; el pedido suma `view_key_kind`, con valores
`incoming` o `full`. La respuesta agrega `kind`. Para clientes anteriores,
omitir el campo conserva la clave completa. Un valor desconocido se rechaza.

Kotlin siempre pide el alcance explícito y verifica tanto `kind` como el
prefijo de testnet. Si se empaqueta por error un motor anterior, no muestra
su clave completa como si fuera de entrantes. No cambia el formato cifrado
`ZKLW3` ni el almacén de Android.

`native/Cargo.lock` también incorpora las correcciones compatibles de la
revisión de seguridad del nodo: `rustls-webpki`, `webpki-roots`,
`webpki-root-certs`, `ctutils`, `libz-sys`, `zstd-sys` e `inout`. Se actualizó
además `quinn-proto` a 0.11.19, como en el nodo, para cerrar cinco avisos que
el lock móvil todavía tenía.

La auditoría con la base RustSec del 10/11 todavía señala tres avisos:
`libsqlite3-sys` 0.35.0 (RUSTSEC-2026-0459, SQLite incluido con Arti),
`rsa` 0.9.10 (RUSTSEC-2023-0071, también Arti) y `crypto-bigint` 0.5.5
(RUSTSEC-2026-0387). SQLite y RSA aparecen en el árbol de Android;
`crypto-bigint` no aparece en el árbol de ese target con estas features,
aunque está en el lock. No se suprimieron avisos. Sus versiones corregidas
exigen evaluar una actualización de Arti y sus dependencias; la auditoría
del móvil **no está limpia** y no se debe confundir con la del nodo.

## Validación y límites

Las pruebas JVM cubren un motor anterior que devuelve una clave de alcance
mayor, los prefijos, el cambio de billetera antes y durante la derivación,
y la cancelación mientras JNI trabaja. La prueba Rust verifica los dos
alcances, el contrato anterior y el rechazo de alcances desconocidos.

La prueba instrumentada `StarkViewKeyInstrumentedTest` usa una frase efímera
y un directorio propio de caché, sin red. Comprueba que ambos alcances
correspondan a la misma billetera y no alteren su dirección ni su estado.
No lee el almacén del usuario ni realiza pagos.

Comandos JVM y APK (Java 17, máximo dos workers):

```text
gradlew.bat --no-daemon --max-workers=2 :ui:testDebugUnitTest :network:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Resultado local: **64 pruebas, 0 fallas, 0 errores, 0 omitidas**. Compilaron
el APK debug y el APK instrumentado en 3 min 45 s. Registro:
`build/incoming-view-key-validation.log`. Estos APK todavía usaron los
motores previos; la prueba instrumentada nueva compiló, pero no se ejecutó.

Motor Android, con el nodo de la misma rama y los manifiestos públicos de
`docs/`, en Linux con NDK r27d:

```sh
ANDROID_NDK_HOME=<ruta-del-ndk> bash native/build-android-linux.sh
```

Antes de aprobar un APK hay que incluir los `.so` nuevos de **ambos ABI**.
Compilar la interfaz con los motores anteriores no valida la nueva función
nativa. Publicación e instalación se coordinan por separado; no hubo ninguna
en este cambio.
