# ZKAPriv - Private Wallet for the Zoka Network

![Version](https://img.shields.io/badge/version-1.0.0-2ea043.svg)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

![ZKAPriv v1.0.0 screenshots](docs/screenshots/zkapriv-v1.0.0-screenshots-collage.png)

**ZKAPriv** is the official mobile wallet for the **Zoka Network (ZKA)**, a
blockchain built around private-by-default payments. Each transfer uses stealth
addresses and encrypted amounts, so an account balance and history are visible
only to whoever controls the wallet keys.

> ZKAPriv is not a general-purpose fork. It is built specifically for Zoka's
> private payment protocol: Ristretto255, encrypted notes, and nullifiers.

---

## Features

- **Per-transaction privacy.** One-time addresses are derived with ECDH over
  Ristretto255; amounts are encrypted and can only be decrypted by the receiver
  with their scan key.
- **Two-key account model.** Each account has a **scan key** for detecting
  incoming funds and a **spend key** for spending them, both derived from a
  BIP39 mnemonic.
- **Multi-account wallet.** Manage multiple independent accounts on one device,
  each with its own name, color, and `zka1...` receive address.
- **On-chain balance scanning.** The app reconstructs balances by scanning the
  chain and decrypting outputs that belong to the wallet, without relying on a
  server that knows the user's identity.
- **Device-level security.** PIN lock, biometric unlock, encrypted key storage,
  automatic session locking, and screenshot protection.
- **Direct Zoka node connectivity.** Configurable RPC endpoints with primary
  and fallback nodes.

---

## Architecture

ZKAPriv is a multi-module Android project built with Kotlin and Jetpack Compose:

| Module | Responsibility |
|--------|----------------|
| `app` | App entry point, navigation, theme, and dependency injection with Hilt. |
| `core` | Shared models, utilities, and base cryptography (`Ristretto255`, `Bech32`). |
| `network` | Zoka node RPC client, balance repository, and private transaction handling. |
| `security` | Account management, key derivation, mnemonics, Android keystore, and biometrics. |
| `price` | ZKA market price and statistics. |
| `ui` | Compose screens: dashboard, send, receive, accounts, settings, and onboarding. |

### Cryptography Overview

- Curve: **Ristretto255**.
- Internal payment address: `zpriv:<scan_pub_hex>:<spend_pub_hex>`.
- User receive address: `zka1` + Base58(`scan_pub || spend_pub || checksum`).
- Output derivation: `derived = H("zoka-output-key" || shared || commitment)`,
  `one_time_pubkey = spend_pub + derived*G`.
- Ownership detection: the receiver recomputes the one-time public key with
  their scan scalar and compares it with the on-chain output.
- Spending: each note produces a deterministic **nullifier** that prevents
  double spends without revealing which output was spent.

---

## Build

Requirements: Android Studio with AGP 8.x, JDK 17, and Android SDK/NDK. The app
includes native cryptography for ARMv7 and ARM64.

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# Install on a connected device without clearing app data
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release APK is generated at `app/build/outputs/apk/release/`.

---

## Network Configuration

Zoka node RPC endpoints are defined in the `core` module (`Constants.kt`) and
can also be adjusted inside the app from **Settings -> Network**:

- **Primary RPC** and **fallback RPC** for node availability.

---

## Security

- Wallet secrets, including mnemonics, scan keys, and spend keys, **never leave
  the device** unless the user explicitly exports them. They are stored
  encrypted at rest.
- Do not commit `keystore.properties`, `local.properties`, signing keys, seeds,
  tokens, APKs, or any file containing wallet secrets.
- Store the recovery phrase offline. It is the only way to restore a full wallet
  account.

---

## Status

ZKAPriv is actively maintained and connected to the Zoka Network mainnet. Its
privacy features are operational; protocol-level roadmap items are tracked in
the Zoka node repository.

---

## License

Distributed under the **MIT** license.

Copyright (c) 2025 Zoka Network.
