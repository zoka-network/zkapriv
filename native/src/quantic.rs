//! Puente JNI del motor de Zoka Quantic (`zoka::mobile_quantic`) y de su red
//! (`zoka::mobile_net::quantic_light_json`).
//!
//! Mismo contrato que el resto del puente: entra un JSON, sale un JSON, y un
//! error vuelve como `{"error": "..."}`. Ninguna de estas funciones toca la
//! interfaz: la app decide cuándo llamarlas.

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

use super::{configure_mobile_prover_runtime, java_string, responder_json};

/// La dirección de la wallet de Quantic de la semilla:
/// `{"seed_hex":"...","network":"quantic-testnet"}` →
/// `{"network","chain_id","address"}`.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_quanticWalletInfo(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    responder_json(&mut env, request_json, "la wallet de Quantic entro en panico", |json| {
        zoka::mobile_quantic::quantic_wallet_info_json(json)
    })
}

/// `{"address","network"}` → `{"valid":true}` o `{"valid":false,"reason"}`.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_quanticCheckAddress(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    responder_json(&mut env, request_json, "el chequeo de direccion entro en panico", |json| {
        zoka::mobile_quantic::quantic_check_address_json(json)
    })
}

/// Escanea bloques compactos desde el estado guardado. Ver
/// `zoka::mobile_quantic::QuanticSyncRequest`: la app guarda `current` y, si
/// viene, `safe`; ante un error que empieza con `reorg:` vuelve a `safe`.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_quanticSync(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    responder_json(&mut env, request_json, "la sincronizacion de Quantic entro en panico", |json| {
        zoka::mobile_quantic::quantic_sync_json(json)
    })
}

/// Arma y prueba un envío (unos 20 s en el teléfono de 32 bits): llamarla
/// desde un hilo de fondo. Devuelve `{"tx_hex","txid","fee_atoms",...}`; la
/// app lo publica con `quanticLight0({"kind":"submit_tx","tx_hex":...})`.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_quanticBuildTransfer(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    configure_mobile_prover_runtime();
    responder_json(&mut env, request_json, "el envio de Quantic entro en panico", |json| {
        zoka::mobile_quantic::quantic_build_transfer_json(json)
    })
}

/// Arma la clave de prueba de Halo 2 (40 s la primera vez en el teléfono de
/// pruebas). Para llamarla en segundo plano al abrir la app.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_quanticPreloadProvingKey(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    configure_mobile_prover_runtime();
    let result = catch_unwind(AssertUnwindSafe(zoka::mobile_quantic::quantic_preload_proving_key_json));
    let response = match result {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, super::json_string(&err)),
        Err(_) => r#"{"error":"la clave de prueba de Quantic entro en panico"}"#.to_string(),
    };
    java_string(&mut env, response)
}

/// Un pedido a los nodos de Quantic por `/zoka-quantic/light/1`: `{"kind":"info"}`,
/// `{"kind":"compact","from":F,"to":T}` o `{"kind":"submit_tx","tx_hex":"..."}`.
/// La red tiene que estar conectada con `"network":"quantic-testnet"`.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_quanticLight0(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    responder_json(&mut env, request_json, "el pedido a Quantic entro en panico", |json| {
        zoka::mobile_net::quantic_light_json(json)
    })
}
