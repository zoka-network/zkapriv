# ZKAPriv 3 privacy

Updated November 3, 2026. Zoka Quantic **testnet v2**, whose coins have no value.

ZKAPriv is self-custodial. Recovery phrases, spending keys and proof witnesses
are not sent to servers. STARK proofs are generated on the phone. Android
Keystore protects the encrypted recovery phrase, salted PIN verifier and
security preferences. The wallet cache uses ChaCha20-Poly1305. Public peer
identifiers and Tor's own cache are separate. Android handles biometrics;
the app receives only the result.

With Tor off, public block pages and checkpoints come from api.zokascan.cc,
with zokascan.cc and P2P peers as alternatives. Requests specify block
heights, not wallet addresses or view keys. Outputs are opened locally.
Publishing a short address shares its full address with the directory.
Resolving a recipient's short address reveals that query to the queried
peers. Payments transmit completed proofs and encrypted outputs. Amounts
and participants are not public on the chain. Memos are readable by sender
and recipient through their respective view keys.

API operators, infrastructure providers and directly contacted peers may see
your IP, timing and connection metadata, and may operate in other countries.
ZKAPriv does not control their logs or promise absolute anonymity.

Optional Tor uses embedded Arti for reading, directory operations and sending
via onion peers. Failure does not fall back to direct connections. Operations
already started may finish on their original route; changes apply to subsequent
operations. External browsers and sharing targets use their own settings.

Camera access for QR scanning and biometrics are optional. Copying and sharing
expose selected content to the clipboard or chosen app. A view key reveals
balance and history but cannot spend; recovery phrases can spend. The version
3 wallet flow has no price requests, advertising or usage telemetry. Wallet
screenshots are blocked; debug design previews use synthetic data only.

Settings lets you authenticate to view recovery information, change the PIN
and remove the wallet. Uninstalling removes app-private storage, not public
chain transactions, published directory entries or copies you shared.
Keep the 24 words before removing the wallet: we cannot recover them for you.
Review the code or ask questions at
[the ZKAPriv repository](https://github.com/zoka-network/zkapriv); never post
recovery phrases, view keys, PINs or other secrets.
