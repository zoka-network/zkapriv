# ZkaPriv

Android wallet for the Zoka blockchain.

ZkaPriv is a self-custodial wallet focused on the private Zoka account model. The project keeps only the surfaces that apply to Zoka: wallet creation/import, account management, send/receive, balances, transaction history, QR flow, local security, and price display for native ZKA.

## Current Scope

- Create wallets from 12/24-word BIP-39 seed phrases.
- Import wallets from a Zoka scan key in hex.
- Derive canonical `zka1...` private addresses.
- Store scan keys and mnemonics with encrypted local storage.
- Manage multiple local Zoka accounts.
- Send and receive native ZKA.
- Show balance, USD value, and transaction history.
- Protect access with PIN and biometric authentication.
- Light/dark theme support.

Out of scope for ZkaPriv right now: gallery modules, embedded browsers, external connector modules, staking modules, swaps, wrapped assets, bridge assets, and any explorer/browser surface that does not apply to the Zoka wallet flow.

## Zoka Key Model

The wallet derivation used by the Android app is:

```text
scan_key = raw BIP-39 entropy
spend_key = SHA256("zoka-private-wallet-spend-v1" || 0x1f || scan_key)
address = zka1 + Base58(scan_pub || spend_pub || checksum)
receive material = zpriv:<scan_pub_hex>:<spend_pub_hex>
```

This means seed phrase import and scan key import must resolve to the same `zka1...` address when they represent the same entropy.

## Project Structure

```text
zkapriv/
├── app/       Main Android app module
├── core/      Shared models and utilities
├── network/   Zoka API client and repository
├── price/     Native ZKA price repository
├── security/  Wallet, crypto, secure storage, biometrics
└── ui/        Jetpack Compose screens and view models
```

## Build

Prerequisites:

- JDK 17
- Android SDK 34+
- Android Gradle Plugin dependencies resolved by Gradle

Build from the repo root:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Security Notes

- Never share a scan key or seed phrase.
- `zpriv` receive material is public receive metadata, not a spend secret.
- ZkaPriv cannot recover lost wallets.

## License

MIT License. See [LICENSE](LICENSE).

## Credits

Developed by Natosh Zoka for the Zoka community.
