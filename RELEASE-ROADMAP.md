# ZKAPriv — Hoja de ruta de release del APK (para Codex)

> Objetivo: pasar de los builds **debug** que veníamos probando a un **release firmado,
> limpio**, y publicarlo como **GitHub Release** en `ZokaNetwork/zkapriv`.
> Este documento es completo y autosuficiente: seguilo en orden, sin omitir pasos.
> Trabajá sobre la branch **`main`** del repo `zkapriv`.

---

## 0. Estado actual (contexto)

- La app está **funcionalmente validada** end-to-end: crear/recuperar wallet, enviar,
  recibir, devolver, **restore desde semilla con balance correcto**, Recent Activity
  (entradas/salidas, montos correctos, timestamps reales), y el **bug peligroso de
  sobre-reporte de balance YA resuelto** (se calcula desde los nullifiers committeados +
  fallback de RPC).
- Hasta ahora se compiló **debug** → package `com.zoka.zkapriv.benchmark`.
- El **release** usa package `com.zoka.zkapriv` (sin sufijo), firmado con la release key.
- El fix del lado nodo (VPS2 sirviendo cuerpos) **NO bloquea la app** (la app cae a BOOT
  por el fallback). La app puede salir igual.
- Keystore de firma: **ya existe** en `keystore/zkapriv-release-key.jks`.
- El `release` build type **no tiene minify** activado (no hay `minifyEnabled`), así que
  el código se comporta igual que debug (sin sorpresas de R8). Bajo riesgo.

---

## 1. Inputs que necesitás del usuario ANTES de empezar (no inventar)

1. **Versión a publicar.** Hoy `versionCode = 11`, `versionName = "0.1.4"`
   (`app/build.gradle`). Confirmar si v1 es `1.0.0` (subir `versionCode` a 12) o se
   mantiene `0.1.4`. **Preguntar al usuario y usar ese valor en TODOS lados** (build.gradle
   + labels de UI + tag + nombre del asset).
2. **Credenciales del keystore.** El `signingConfig` lee de `keystore.properties`
   (`storePassword`, `keyAlias`, `keyPassword`). Si ese archivo no existe, pedir las claves
   al usuario y crearlo (ver paso 3). **Nunca commitear esas claves.**

---

## 2. Arreglos locales (código) antes del release

### 2.1. Reconciliar el label de versión de la UI (obligatorio)
La UI muestra **`v0.1.0` hardcodeado** pero la app es `0.1.4`. Corregir en:
- `ui/src/main/java/com/zoka/zkapriv/ui/lock/LockScreen.kt` → la línea con `text = "v0.1.0"`.
- `ui/src/main/java/com/zoka/zkapriv/ui/onboarding/WelcomeScreenNew.kt` → la línea con `text = "v0.1.0"`.

Opción recomendada (que no vuelva a desincronizarse): exponer la versión real y leerla.
- El módulo `app` tiene `BuildConfig.VERSION_NAME`. El módulo `ui` es **librería**, así que
  su `BuildConfig.VERSION_NAME` puede venir vacío. Dos caminos:
  - **(A, simple y seguro):** hardcodear el string correcto (ej. `"v1.0.0"`) en ambos
    labels, igualándolo a la versión elegida en el paso 1.
  - **(B, robusto):** pasar la versión desde el `app` a la UI (parámetro Composable o un
    objeto de constantes en un módulo común que ambos lean), y mostrar `"v" + version`.
- Elegí A si querés cerrar rápido; B si querés que no se vuelva a desincronizar.

### 2.2. Bump de versión (si el usuario eligió subir a v1)
En `app/build.gradle`: actualizar `versionCode` y `versionName` al valor confirmado.
Mantener el label de UY (2.1) en el mismo número.

### 2.3. Sanidad de configuración de red / endpoints (verificar, no asumir)
- Confirmar que la app apunta a los RPC de producción correctos:
  `Constants.ZOKA_MAINNET_RPC` = `http://159.223.119.216:3000/` (BOOT, primario),
  `ZOKA_MAINNET_RPC_FALLBACK` = `http://157.230.5.103:3000/` (VPS2). Archivo:
  `core/src/main/java/com/zoka/zkapriv/core/util/Constants.kt`.
- Confirmar que el config de red embebido (mainnet) es el correcto y que **no** queda
  ningún endpoint de testing/localhost.

### 2.4. Limpieza
- Quitar logs de debug ruidosos o que filtren datos (revisar `Log.d`/`Log.w` con info
  sensible). La política ya es no loguear secretos — verificar que se respete.
- Confirmar que **no** se commiteen artefactos: `build/`, `.apk`, `keystore.properties`,
  `*.jks`. Revisar/actualizar `.gitignore` (ver paso 6).

---

## 3. Verificar / preparar el signing de release

1. Confirmar que existe `keystore/zkapriv-release-key.jks`.
2. Crear (si falta) `keystore.properties` en la raíz del repo con:
   ```
   storeFile=keystore/zkapriv-release-key.jks
   storePassword=<que lo da el usuario>
   keyAlias=zkapriv
   keyPassword=<que lo da el usuario>
   ```
   (El `build.gradle` ya lee `keystoreProperties`; ver `app/build.gradle` signingConfigs.)
3. **NO commitear** `keystore.properties` ni el `.jks`. Verificar que estén en `.gitignore`.

---

## 4. Build del APK de release

```bash
cd zkapriv
./gradlew clean
./gradlew assembleRelease
# salida: app/build/outputs/apk/release/app-release.apk
```
Verificaciones del artefacto:
```bash
# Está firmado con la release key?
"$ANDROID_HOME"/build-tools/<ver>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# Incluye ambas ABIs (el celu objetivo es armeabi-v7a)?
unzip -l app/build/outputs/apk/release/app-release.apk | grep -E "lib/(armeabi-v7a|arm64-v8a)/libzoka_mobile.so"
# Package correcto (com.zoka.zkapriv, sin .benchmark)?
"$ANDROID_HOME"/build-tools/<ver>/aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "^package:"
```

---

## 5. Smoke test ON-DEVICE (CRÍTICO — no saltear)

El release es **otro package** (`com.zoka.zkapriv`) y está **firmado con la release key**,
así que se instala como app nueva. Probarlo instalado antes de publicar:
```bash
ADB="$ANDROID_HOME/platform-tools/adb"
$ADB install app/build/outputs/apk/release/app-release.apk
```
Checklist en el dispositivo:
- [ ] La pantalla de inicio muestra la **versión correcta** (label del paso 2.1).
- [ ] **Crear** wallet nueva → onboarding completo (seed, verificación, PIN/biometría).
- [ ] **Recuperar** wallet desde semilla → balance correcto.
- [ ] **Recibir** (mostrar dirección) y **enviar** una tx → se broadcastea.
- [ ] **Recent Activity** muestra entradas y salidas con montos y fechas correctas.
- [ ] Lock/unlock biométrico + PIN andan.

Si algo falla en release que andaba en debug → investigar antes de publicar (no publicar
un release no probado).

---

## 6. Commits + push

- Asegurar `.gitignore` (que NO entren secretos ni artefactos):
  ```
  keystore.properties
  *.jks
  /build/
  **/build/
  *.apk
  ```
  (Si el `.jks` ya estuviera trackeado por error, removerlo del índice: `git rm --cached keystore/zkapriv-release-key.jks` — el archivo queda local.)
- Commitear los arreglos locales (versión/labels/limpieza):
  ```bash
  git add app/build.gradle ui/.../LockScreen.kt ui/.../WelcomeScreenNew.kt core/.../Constants.kt .gitignore
  git commit -m "release: vX.Y.Z — reconcile version label, prep signed release build"
  git push origin main
  ```
- **Tag** del commit de release:
  ```bash
  git tag vX.Y.Z
  git push origin vX.Y.Z
  ```

---

## 7. Publicar el GitHub Release (limpio)

```bash
# renombrar el APK para que el asset sea claro:
cp app/build/outputs/apk/release/app-release.apk zkapriv-vX.Y.Z.apk

gh release create vX.Y.Z zkapriv-vX.Y.Z.apk \
  --repo ZokaNetwork/zkapriv \
  --title "ZKAPriv vX.Y.Z" \
  --notes "Primer release público de ZKAPriv (wallet privada de Zoka).

Incluye:
- Envío/recepción de transacciones privadas (ZK, Groth16) on-device.
- Restore desde semilla con balance correcto.
- Recent Activity con entradas, salidas, montos y fechas reales.
- Detección de gasto desde los nullifiers committeados (balance seguro, sin sobre-reporte).
- Fallback automático de RPC para servir cuerpos de tx faltantes.

ABI: armeabi-v7a + arm64-v8a. Self-custodial."
```
- Verificar que el asset `.apk` quedó subido y que el tag apunta al commit correcto.

---

## 8. ❌ Qué NO hacer

1. **NO** commitear `keystore.properties`, el `.jks`, ni el `.apk` (secretos/artefactos).
2. **NO** publicar el release sin el **smoke test on-device** (paso 5).
3. **NO** cambiar `applicationId`/package del release (es la identidad de la app).
4. **NO** quitar las `.so` nativas ni dejar fuera la ABI **armeabi-v7a** (el celu es 32-bit).
5. **NO** activar minify/shrink "porque sí" sin re-probar (hoy está off = seguro).
6. **NO** inventar la versión ni las claves del keystore: confirmarlas con el usuario (paso 1).

---

## 9. Resumen del flujo

1. Confirmar versión + claves del keystore con el usuario.
2. Arreglos locales: label de versión (2.1), bump si aplica (2.2), sanidad de endpoints (2.3), limpieza (2.4).
3. Preparar `keystore.properties` (sin commitear).
4. `./gradlew clean assembleRelease` → verificar firma/ABIs/package.
5. **Smoke test on-device** del APK de release.
6. `.gitignore` + commit + push + tag.
7. `gh release create` con el `.apk` como asset + notas.
8. Verificar el release publicado.

> Notas: el fix de cuerpos en VPS2 (lado nodo) y el endurecimiento pre-mainnet
> (dificultad 8→12, maturity 5→60, TLS) son del **nodo/red**, no de la app, y no
> bloquean este release.
