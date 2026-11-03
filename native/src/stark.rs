//! Puente JNI del motor de Zoka Quantic Testnet v2 (STARK) para ZKAPriv 3.
//!
//! Mismo contrato que el resto del puente: entra un JSON, sale un JSON, y un
//! error vuelve como `{"error": "..."}`.
//!
//! - La billetera es la misma que la de ZSilent: 24 palabras (BIP-39, 256
//!   bits) cuya entropía es la semilla de `Account::from_seed`.
//! - El estado de la billetera liviana (`zoka_pq_pool::light`) se guarda en
//!   el directorio privado de la app, cifrado con una clave que sale de la
//!   semilla: sin la frase, el archivo no dice nada.
//! - La cadena se lee por páginas: las que la app trae por HTTPS
//!   (`starkAddPages`, zokascan.cc con caché) o pedidas a una semilla por P2P
//!   (`starkSync`), directo o solo por Tor (el SOCKS de Arti).
//! - La prueba STARK se hace acá, en el teléfono. A la red solo va la
//!   transacción terminada.

use std::{
    fs,
    net::SocketAddr,
    path::{Path, PathBuf},
    sync::{Mutex, OnceLock},
};

use bip39::{Language, Mnemonic};
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    ChaCha20Poly1305, Key, Nonce,
};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use rand::RngCore;
use serde::Deserialize;
use serde_json::{json, Value};
use sha2::{Digest as _, Sha256};
use zeroize::Zeroizing;

use zoka::quantic::{
    stark::{
        protocol::{
            ledger::{conventional_fee, ATOMS},
            light::{LightWallet, Movement},
            scan::ScanPage2,
            wallet::{Account, Address, ShortAddress, MEMO_BYTES},
        },
        Profile,
    },
    stark_mobile::{self, Route},
};

use super::{configure_mobile_prover_runtime, responder_json};

/// Páginas que pide una llamada a `starkSync` como mucho (100 bloques cada
/// una): la app vuelve a llamar mientras `in_sync` sea falso, y entre
/// llamadas puede mostrar el avance.
const PAGES_PER_CALL: usize = 20;
/// La versión del formato del estado. Uno de otra versión es una caché
/// vieja: se descarta y la billetera vuelve a leer la cadena.
const STATE_MAGIC: &[u8; 6] = b"ZKLW3\0";
/// Pila del hilo que prueba: la de los hilos de Android no alcanza para todo.
const PROVER_STACK: usize = 64 * 1024 * 1024;

fn err(e: impl std::fmt::Display) -> String {
    e.to_string()
}

fn runtime() -> Result<&'static tokio::runtime::Runtime, String> {
    static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
    if let Some(rt) = RUNTIME.get() {
        return Ok(rt);
    }
    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .thread_name("zoka-stark")
        .enable_all()
        .build()
        .map_err(err)?;
    Ok(RUNTIME.get_or_init(|| rt))
}

/// Una operación sobre el estado a la vez: sincronizar y enviar no se pisan.
fn state_lock() -> std::sync::MutexGuard<'static, ()> {
    static LOCK: Mutex<()> = Mutex::new(());
    LOCK.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

// ── Pedido común ─────────────────────────────────────────────────────────────

#[derive(Deserialize)]
struct Request {
    #[serde(default)]
    phrase: Option<String>,
    #[serde(default = "default_network")]
    network: String,
    #[serde(default)]
    state_dir: Option<String>,
    /// Altura de nacimiento de una billetera nueva (la punta al crearla); 1
    /// para una restaurada.
    #[serde(default)]
    birth: Option<u32>,
    /// `127.0.0.1:PUERTO` del SOCKS de Arti: si viene, todo va por Tor.
    #[serde(default)]
    tor_proxy: Option<String>,
    #[serde(default)]
    pages_hex: Vec<String>,
    #[serde(default)]
    to: Option<String>,
    #[serde(default)]
    address: Option<String>,
    #[serde(default)]
    amount_atoms: Option<u64>,
    #[serde(default)]
    memo: Option<String>,
    /// Un checkpoint firmado, tal como lo da `/checkpoint` de la API.
    #[serde(default)]
    checkpoint: Option<Value>,
}

fn default_network() -> String {
    "testnet".into()
}

impl Request {
    fn parse(json: &str) -> Result<Self, String> {
        serde_json::from_str(json).map_err(|e| format!("pedido invalido: {e}"))
    }
    fn profile(&self) -> Result<Profile, String> {
        Profile::named(&self.network)
    }
    fn seed(&self) -> Result<Zeroizing<[u8; 32]>, String> {
        seed_of(self.phrase.as_deref().ok_or("falta la frase")?)
    }
    fn account(&self) -> Result<(Account, Zeroizing<[u8; 32]>), String> {
        let seed = self.seed()?;
        let account = Account::from_seed(&seed, self.profile()?.network, 0).map_err(err)?;
        Ok((account, seed))
    }
    fn route(&self) -> Result<Route, String> {
        match &self.tor_proxy {
            None => Ok(Route::Direct),
            Some(proxy) => proxy
                .parse::<SocketAddr>()
                .map(Route::Tor)
                .map_err(|_| "tor_proxy no es ip:puerto".to_string()),
        }
    }
    fn state_path(&self) -> Result<PathBuf, String> {
        let dir = self.state_dir.as_deref().ok_or("falta state_dir")?;
        Ok(Path::new(dir).join(format!("stark-{}.bin", self.profile()?.chain_id)))
    }
}

/// La frase de ZSilent: 24 palabras en inglés; su entropía es la semilla.
fn seed_of(phrase: &str) -> Result<Zeroizing<[u8; 32]>, String> {
    let mnemonic = Mnemonic::parse_in_normalized(Language::English, phrase.trim())
        .map_err(|_| "la frase de recuperacion no es valida".to_string())?;
    let entropy = Zeroizing::new(mnemonic.to_entropy());
    let seed: [u8; 32] = entropy
        .as_slice()
        .try_into()
        .map_err(|_| "Zoka Quantic v2 usa frases de 24 palabras".to_string())?;
    Ok(Zeroizing::new(seed))
}

// ── Estado cifrado ───────────────────────────────────────────────────────────

fn state_key(seed: &[u8; 32], network: u8) -> Zeroizing<[u8; 32]> {
    let mut hasher = Sha256::new();
    hasher.update(b"ZKAPriv light wallet state v2");
    hasher.update([network]);
    hasher.update(seed);
    Zeroizing::new(hasher.finalize().into())
}

fn load(path: &Path, seed: &[u8; 32], profile: Profile, birth: u32) -> Result<LightWallet, String> {
    let bytes = match fs::read(path) {
        Ok(bytes) => bytes,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            return LightWallet::new(profile.network, birth.max(1)).map_err(err)
        }
        Err(e) => return Err(err(e)),
    };
    let Some(body) = bytes
        .strip_prefix(STATE_MAGIC.as_slice())
        .filter(|body| body.len() > 12)
    else {
        if bytes.starts_with(b"ZKLW") {
            return LightWallet::new(profile.network, birth.max(1)).map_err(err);
        }
        return Err("el estado guardado no es de ZKAPriv".into());
    };
    let key = state_key(seed, profile.network);
    let cipher = ChaCha20Poly1305::new(Key::from_slice(key.as_ref()));
    let plain = Zeroizing::new(
        cipher
            .decrypt(Nonce::from_slice(&body[..12]), &body[12..])
            .map_err(|_| "el estado guardado es de otra billetera o esta dañado")?,
    );
    let wallet = LightWallet::decode(&plain).map_err(err)?;
    if wallet.network() != profile.network {
        return Err("el estado guardado es de otra red".into());
    }
    Ok(wallet)
}

/// Escribe en un temporal y lo renombra: un corte a mitad no deja un estado
/// roto.
fn save(path: &Path, seed: &[u8; 32], wallet: &LightWallet) -> Result<(), String> {
    let plain = Zeroizing::new(wallet.encode().map_err(err)?);
    let key = state_key(seed, wallet.network());
    let cipher = ChaCha20Poly1305::new(Key::from_slice(key.as_ref()));
    let mut nonce = [0u8; 12];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    let sealed = cipher
        .encrypt(Nonce::from_slice(&nonce), plain.as_slice())
        .map_err(|_| "no se pudo cifrar el estado")?;
    let mut bytes = Vec::with_capacity(STATE_MAGIC.len() + 12 + sealed.len());
    bytes.extend_from_slice(STATE_MAGIC);
    bytes.extend_from_slice(&nonce);
    bytes.extend_from_slice(&sealed);
    if let Some(dir) = path.parent() {
        fs::create_dir_all(dir).map_err(err)?;
    }
    let temporary = path.with_extension("tmp");
    fs::write(&temporary, &bytes).map_err(err)?;
    fs::rename(&temporary, path).map_err(err)
}

// ── Respuestas ───────────────────────────────────────────────────────────────

fn zka(atoms: u64) -> String {
    format!("{}.{:06}", atoms / ATOMS, atoms % ATOMS)
}

fn movement(m: &Movement, tip: u32) -> Value {
    let kind = if m.coinbase {
        "mined"
    } else if m.spent > 0 {
        "sent"
    } else {
        "received"
    };
    let amount = if m.spent > 0 { m.paid() } else { m.received };
    json!({
        "txid": hex::encode(m.txid),
        "height": m.height,
        "timestamp": m.timestamp,
        "kind": kind,
        "amount_atoms": amount,
        "amount": zka(amount),
        "fee_atoms": m.fee,
        "memo": m.memo,
        "confirmations": tip.saturating_sub(m.height) + 1,
    })
}

fn status(wallet: &LightWallet) -> Value {
    let balance = wallet.balance();
    let history: Vec<Value> = wallet
        .history()
        .iter()
        .rev()
        .take(200)
        .map(|m| movement(m, balance.height))
        .collect();
    json!({
        "height": balance.height,
        "tip": balance.tip,
        "next": wallet.next(),
        "in_sync": wallet.in_sync(),
        "spendable_atoms": balance.spendable_atoms,
        "spendable": zka(balance.spendable_atoms),
        "immature_atoms": balance.immature_atoms,
        "immature": zka(balance.immature_atoms),
        "reserved_atoms": balance.reserved_atoms,
        "max_sendable_atoms": balance.max_sendable_atoms,
        "notes": balance.notes,
        // Hasta acá lo leído coincide con un checkpoint firmado por el
        // equipo con Ed25519 y ML-DSA-65.
        "verified": balance.verified,
        "pending": wallet.pending().map(|p| json!({
            "txid": hex::encode(p.txid),
            "expiry": p.expiry,
        })),
        "history": history,
    })
}

// ── Operaciones ──────────────────────────────────────────────────────────────

fn new_phrase() -> Result<String, String> {
    let mut entropy = Zeroizing::new([0u8; 32]);
    rand::rngs::OsRng.fill_bytes(entropy.as_mut());
    let mnemonic = Mnemonic::from_entropy_in(Language::English, entropy.as_ref()).map_err(err)?;
    Ok(json!({ "phrase": mnemonic.to_string() }).to_string())
}

fn wallet_info(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let (account, _) = request.account()?;
    let address = account.address();
    Ok(json!({
        "network": request.network,
        "chain_id": profile.chain_id,
        "address": address.encode().map_err(err)?,
        "short": address.short().map_err(err)?,
    })
    .to_string())
}

fn view_key(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let (account, _) = request.account()?;
    Ok(json!({ "view_key": account.view_key().map_err(err)?.encode() }).to_string())
}

fn check_address(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let network = request.profile()?.network;
    let text = request.address.as_deref().ok_or("falta address")?.trim();
    let kind = if Address::decode(text, network).is_ok() {
        "full"
    } else if ShortAddress::decode(text, network).is_ok() {
        "short"
    } else {
        return Ok(json!({ "valid": false }).to_string());
    };
    Ok(json!({ "valid": true, "kind": kind }).to_string())
}

/// Lo guardado, sin red: saldo, historial y desde dónde sigue.
fn wallet_status(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let _lock = state_lock();
    let (_, seed) = request.account()?;
    let wallet = load(&request.state_path()?, &seed, request.profile()?, request.birth.unwrap_or(1))?;
    Ok(status(&wallet).to_string())
}

/// Páginas que la app trajo por HTTPS, en orden desde `next`.
fn add_pages(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let _lock = state_lock();
    let (account, seed) = request.account()?;
    let path = request.state_path()?;
    let mut wallet = load(&path, &seed, profile, request.birth.unwrap_or(1))?;
    for page in &request.pages_hex {
        let bytes = hex::decode(page).map_err(|_| "pagina en hex invalida")?;
        let page = ScanPage2::decode(&bytes).map_err(err)?;
        if page.from != wallet.next() {
            // Un cambio de rama hizo volver a la billetera: lo que sigue ya no encaja.
            break;
        }
        wallet.add_page(&account, &page).map_err(err)?;
    }
    save(&path, &seed, &wallet)?;
    Ok(status(&wallet).to_string())
}

/// Páginas pedidas a una semilla por P2P (directo o por Tor), hasta
/// [`PAGES_PER_CALL`].
fn sync(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let route = request.route()?;
    let _lock = state_lock();
    let (account, seed) = request.account()?;
    let path = request.state_path()?;
    let mut wallet = load(&path, &seed, profile, request.birth.unwrap_or(1))?;
    let seeds = stark_mobile::builtin_seeds(profile)?;
    let outcome: Result<(), String> = runtime()?.block_on(async {
        let mut client = stark_mobile::connect(&seeds, route).await?;
        for _ in 0..PAGES_PER_CALL {
            let page = stark_mobile::page(&mut client, wallet.next()).await?;
            let empty = page.blocks.is_empty();
            wallet.add_page(&account, &page).map_err(err)?;
            if empty || wallet.in_sync() {
                break;
            }
        }
        // La cadena leída contra el último checkpoint del equipo.
        let confirmed = match stark_mobile::checkpoint(&mut client, profile).await {
            Ok(Some((height, hash))) => wallet.confirm_checkpoint(height, &hash).map(|_| ()).map_err(err),
            Ok(None) => Ok(()),
            Err(reason) => Err(format!("checkpoint: {reason}")),
        };
        client.close().await;
        confirmed
    });
    // Lo leído hasta un error también vale.
    save(&path, &seed, &wallet)?;
    outcome?;
    Ok(status(&wallet).to_string())
}

/// Un checkpoint traído por HTTPS (`/checkpoint`): se verifica su firma acá y
/// se confirma contra lo leído. Si no coincide, la billetera vuelve a empezar.
fn confirm_checkpoint(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let checkpoint: zoka::checkpoint_authority::SignedCheckpoint =
        serde_json::from_value(request.checkpoint.clone().ok_or("falta checkpoint")?)
            .map_err(|e| format!("checkpoint invalido: {e}"))?;
    let (height, hash) = stark_mobile::verify_checkpoint(&checkpoint, profile)?;
    let _lock = state_lock();
    let (_, seed) = request.account()?;
    let path = request.state_path()?;
    let mut wallet = load(&path, &seed, profile, request.birth.unwrap_or(1))?;
    let confirmed = wallet.confirm_checkpoint(height, &hash).map_err(err);
    save(&path, &seed, &wallet)?;
    confirmed?;
    Ok(status(&wallet).to_string())
}

fn quote(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let _lock = state_lock();
    let (_, seed) = request.account()?;
    let wallet = load(&request.state_path()?, &seed, request.profile()?, 1)?;
    let amount = request.amount_atoms.ok_or("falta amount_atoms")?;
    let balance = wallet.balance();
    Ok(match wallet.quote(amount) {
        Ok((fee, inputs)) => json!({
            "enough": true,
            "fee_atoms": fee,
            "fee": zka(fee),
            "inputs": inputs,
            "max_sendable_atoms": balance.max_sendable_atoms,
        }),
        Err(reason) => json!({
            "enough": false,
            "reason": reason.to_string(),
            // Lo que costaría con una sola entrada, para mostrar algo.
            "fee_atoms": conventional_fee(1, 2),
            "max_sendable_atoms": balance.max_sendable_atoms,
        }),
    }
    .to_string())
}

/// La dirección completa de `text`: ella misma, o la que el directorio de
/// la red tiene para una corta (comprobada contra la corta).
async fn recipient(
    text: &str,
    profile: Profile,
    client: &mut zoka::quantic::stark_net::Client,
) -> Result<Address, String> {
    let text = text.trim();
    if let Ok(full) = Address::decode(text, profile.network) {
        return Ok(full);
    }
    let short = ShortAddress::decode(text, profile.network)
        .map_err(|_| "no es una direccion de esta red, ni completa ni corta".to_string())?;
    stark_mobile::resolve(client, &short, profile.network)
        .await?
        .ok_or_else(|| {
            "la red no conoce esa direccion corta: pedi la completa o que la publiquen".to_string()
        })
}

/// Armar, probar (minutos en un teléfono lento: llamarla en segundo plano) y
/// mandar un pago. Sus notas quedan reservadas hasta que se mine o venza; si
/// la red lo rechaza, se liberan.
fn send(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let route = request.route()?;
    let amount = request.amount_atoms.filter(|a| *a > 0).ok_or("el monto tiene que ser positivo")?;
    let memo = request.memo.clone().unwrap_or_default();
    if memo.len() > MEMO_BYTES {
        return Err("el memo pasa de 512 bytes".into());
    }
    let to_text = request.to.clone().ok_or("falta el destinatario")?;
    let _lock = state_lock();
    let (account, seed) = request.account()?;
    let path = request.state_path()?;
    let mut wallet = load(&path, &seed, profile, 1)?;
    let seeds = stark_mobile::builtin_seeds(profile)?;
    let rt = runtime()?;
    let to = rt.block_on(async {
        let mut client = stark_mobile::connect(&seeds, route).await?;
        let to = recipient(&to_text, profile, &mut client).await;
        client.close().await;
        to
    })?;
    let plan = wallet.plan(&account, to, amount, memo.as_bytes()).map_err(err)?;
    let fee = plan.fee;
    let expiry = plan.expiry;
    configure_mobile_prover_runtime();
    let started = std::time::Instant::now();
    let tx = std::thread::Builder::new()
        .name("zoka-stark-prover".into())
        .stack_size(PROVER_STACK)
        .spawn(move || plan.prove(&account))
        .map_err(err)?
        .join()
        .map_err(|_| "la prueba entro en panico")?
        .map_err(err)?;
    let proving_ms = started.elapsed().as_millis() as u64;
    wallet.reserve(&tx).map_err(err)?;
    save(&path, &seed, &wallet)?;
    let sent = rt.block_on(async {
        let mut client = stark_mobile::connect(&seeds, route).await.map_err(|e| (false, e))?;
        let sent = stark_mobile::submit(&mut client, &tx).await;
        client.close().await;
        // Un rechazo de la semilla es seguro: el pago no está en ningún lado.
        sent.map_err(|e| (e.starts_with("STARK peer rejected"), e))
    });
    match sent {
        Ok(txid) => Ok(json!({
            "txid": hex::encode(txid),
            "amount_atoms": amount,
            "fee_atoms": fee,
            "expiry": expiry,
            "proving_ms": proving_ms,
        })
        .to_string()),
        Err((refused, reason)) => {
            if refused {
                wallet.release();
                save(&path, &seed, &wallet)?;
                Err(format!("la red rechazo el pago: {reason}"))
            } else {
                // No se sabe si llegó: queda reservado hasta que venza.
                Err(format!(
                    "no se pudo confirmar el envio ({reason}); el pago queda reservado hasta la altura {expiry}"
                ))
            }
        }
    }
}

fn resolve(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let short = ShortAddress::decode(request.address.as_deref().ok_or("falta address")?.trim(), profile.network)
        .map_err(|_| "no es una direccion corta de esta red".to_string())?;
    let seeds = stark_mobile::builtin_seeds(profile)?;
    let found = runtime()?.block_on(async {
        let mut client = stark_mobile::connect(&seeds, request.route()?).await?;
        let found = stark_mobile::resolve(&mut client, &short, profile.network).await;
        client.close().await;
        found
    })?;
    Ok(match found {
        Some(full) => json!({ "address": full.encode().map_err(err)? }),
        None => json!({ "address": null }),
    }
    .to_string())
}

fn publish(json: &str) -> Result<String, String> {
    let request = Request::parse(json)?;
    let profile = request.profile()?;
    let (account, _) = request.account()?;
    let seeds = stark_mobile::builtin_seeds(profile)?;
    let published = runtime()?.block_on(async {
        let mut client = stark_mobile::connect(&seeds, request.route()?).await?;
        let published = stark_mobile::publish(&mut client, &account.address()).await;
        client.close().await;
        published
    })?;
    Ok(json!({ "published": published }).to_string())
}

// ── JNI: clase com.zoka.zkapriv.network.nativebridge.ZokaStark ──────────────

macro_rules! jni_json {
    ($name:ident, $what:literal, $f:path) => {
        #[no_mangle]
        pub extern "system" fn $name(mut env: JNIEnv, _class: JClass, request: JString) -> jstring {
            responder_json(&mut env, request, $what, $f)
        }
    };
}

jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_walletInfo0, "la billetera entro en panico", wallet_info);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_viewKey0, "la clave de vista entro en panico", view_key);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_checkAddress0, "el chequeo de direccion entro en panico", check_address);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_status0, "el estado entro en panico", wallet_status);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_addPages0, "las paginas entraron en panico", add_pages);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_sync0, "la sincronizacion entro en panico", sync);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_quote0, "la comision entro en panico", quote);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_confirmCheckpoint0, "el checkpoint entro en panico", confirm_checkpoint);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_send0, "el envio entro en panico", send);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_resolve0, "la direccion corta entro en panico", resolve);
jni_json!(Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_publish0, "publicar la direccion entro en panico", publish);

/// `{"phrase": "..."}`: 24 palabras nuevas, de la entropía del sistema.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_newPhrase0(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let response = new_phrase().unwrap_or_else(|e| json!({ "error": e }).to_string());
    super::java_string(&mut env, response)
}

#[cfg(test)]
mod tests {
    use super::*;

    const PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

    #[test]
    fn the_phrase_gives_the_zsilent_wallet() {
        let seed = seed_of(PHRASE).unwrap();
        assert_eq!(*seed, [0u8; 32]);
        assert!(seed_of("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about").is_err());
        let info: Value = serde_json::from_str(&wallet_info(&json!({"phrase": PHRASE}).to_string()).unwrap()).unwrap();
        let account = Account::from_seed(&[0; 32], Profile::TESTNET.network, 0).unwrap();
        assert_eq!(info["address"], account.address().encode().unwrap());
        assert_eq!(info["short"], account.address().short().unwrap());
        let fresh: Value = serde_json::from_str(&new_phrase().unwrap()).unwrap();
        assert_eq!(fresh["phrase"].as_str().unwrap().split(' ').count(), 24);
        let checked: Value = serde_json::from_str(
            &check_address(&json!({"address": info["short"]}).to_string()).unwrap(),
        )
        .unwrap();
        assert_eq!(checked["kind"], "short");
    }

    #[test]
    fn the_state_is_sealed_with_the_seed() {
        let dir = std::env::temp_dir().join(format!("zkapriv-stark-{}", std::process::id()));
        let path = dir.join("state.bin");
        let wallet = LightWallet::new(Profile::TESTNET.network, 7).unwrap();
        save(&path, &[1; 32], &wallet).unwrap();
        let back = load(&path, &[1; 32], Profile::TESTNET, 1).unwrap();
        assert_eq!(back.next(), 1);
        assert!(load(&path, &[2; 32], Profile::TESTNET, 1).is_err(), "another seed opens nothing");
        let fresh = load(&dir.join("missing.bin"), &[1; 32], Profile::TESTNET, 9).unwrap();
        assert_eq!(fresh.height(), 0);
        // A state of an older format is a stale cache: the wallet reads again.
        fs::write(dir.join("old.bin"), b"ZKLW2 older bytes of another layout").unwrap();
        assert_eq!(load(&dir.join("old.bin"), &[1; 32], Profile::TESTNET, 1).unwrap().height(), 0);
        fs::write(dir.join("junk.bin"), b"not a wallet state at all").unwrap();
        assert!(load(&dir.join("junk.bin"), &[1; 32], Profile::TESTNET, 1).is_err());
        let _ = fs::remove_dir_all(dir);
    }
}
