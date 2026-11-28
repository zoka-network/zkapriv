# ZKAPriv: the Android wallet of Zoka Quantic

ZKAPriv is a self-custodial Android wallet for **Zoka Quantic Testnet**, the
testnet of a blockchain with private payments by default and a post-quantum
private pool. Your phone builds the STARK proof of every payment; your 24
recovery words and keys never leave it. Testnet coins have no value.

Current version: **4.1.1**, with the black-and-glass design and the Zoka ID,
for Android 8 or later on `arm64-v8a` and `armeabi-v7a`. Download the APK and its `SHA256SUMS.txt` from
[Releases](https://github.com/zoka-network/zkapriv/releases/latest); from 4.0.1
on, `SHA256SUMS.txt.sig` signs the checksum list with Zoka's release key
([how to verify](https://github.com/zoka-network/zokanetwork/blob/main/docs/release-verification.md)).

## What it does

- **Private payments, proved on the phone.** A payment's STARK proof (proof v4,
  under 0.8 MB) is made on the device in seconds. Amounts, senders and
  recipients are not public on the chain.
- **Addresses.** A 57-character short address (`zqtest2…`) with a standard QR
  code, and the full address as a multi-part QR that works without the
  network's directory. It pays short, full and exchange deposit (integrated,
  `zqtesti2…`) addresses; with an exchange's address it writes the customer's
  code into the encrypted memo.
- **The same wallet everywhere.** The 24 words open the same wallet in ZSilent,
  the web wallet and ZKAPriv.
- **Zoka ID.** Every address has a mosaic and a 20-character code, the same in
  every Zoka wallet and in ZokaScan. Before paying, compare the recipient's code
  with theirs ([how it works](https://github.com/zoka-network/zokanetwork/blob/main/docs/ZOKA-ID.md)).
- **Several wallets, one PIN.** Each wallet has its own 24 words; a 6-digit PIN
  and optional biometrics open them. Screenshots of the wallet are blocked.
- **Verified reads.** The wallet reads public block pages from
  `api.zokascan.cc`, or peer to peer, and checks each page against its block
  header and the network's signed checkpoint. A server cannot invent a balance.
- **Optional Tor.** Tor mode runs Arti inside the app and never falls back to a
  direct connection.
- **View keys.** Export a full or incoming-only view key to show your history,
  for example in ZokaScan. A view key cannot spend.

Privacy details: [PRIVACY_POLICY_EN.md](PRIVACY_POLICY_EN.md)
([español](PRIVACY_POLICY.md)).

## Architecture

| Module | Responsibility |
| --- | --- |
| `app` | Entry point, navigation and dependency injection (Hilt). |
| `ui` | Compose screens (`ui/quantic`): wallet, send, receive, activity and settings. |
| `network` | The bridge to the native engine, chain reads, Tor and P2P. |
| `security` | Android Keystore, the encrypted vault, PIN and biometrics. |
| `core` | Shared models and utilities. |
| `native` | The Rust engine (`libzoka_mobile.so`), built from the node repository's crates: wallet, STARK prover, X-Wing, checkpoints and P2P. |

## Build

Requirements: JDK 17 and the Android SDK. The engine's `.so` files are in
`native/jniLibs` for both ABIs, so the app builds without Rust:

```sh
./gradlew assembleRelease
```

To rebuild the engine from the node's crates you need Rust, the Android NDK and
`cargo-ndk`; see `native/build-android.ps1` (Windows) and
`native/build-android-linux.sh`.

## Security

Recovery words, PIN and keys stay on the device, encrypted at rest. Never share
your 24 words: anyone who has them can spend your coins. Do not commit
`keystore.properties`, `local.properties`, signing keys or any file with wallet
secrets.

## License

Distributed under the MIT license. Copyright (c) 2025 Zoka Network.
