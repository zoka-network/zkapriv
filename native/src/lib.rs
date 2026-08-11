use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::Once;

/// How many CPU cores to keep free for the UI/system while proving, so the
/// phone stays responsive (the whole point of the compact profile). The prover
/// uses `max(available_cores - RESERVED, 1)` threads. On a 4-core phone that is
/// 3 proving threads; on a 2-core phone it falls back to 1.
const MOBILE_PROVER_RESERVED_CORES: usize = 1;
const MOBILE_PROVER_MIN_THREADS: usize = 1;

fn mobile_prover_thread_count() -> usize {
    let cores = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(2);
    cores
        .saturating_sub(MOBILE_PROVER_RESERVED_CORES)
        .max(MOBILE_PROVER_MIN_THREADS)
}

fn configure_mobile_prover_runtime() {
    static CONFIGURE: Once = Once::new();
    CONFIGURE.call_once(|| {
        if std::env::var_os("RAYON_NUM_THREADS").is_none() {
            std::env::set_var("RAYON_NUM_THREADS", mobile_prover_thread_count().to_string());
        }
    });
}

fn java_string(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value)
        .expect("JNI string allocation failed")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_buildPrivateTransaction(
    mut env: JNIEnv,
    _class: JClass,
    request_json: JString,
) -> jstring {
    configure_mobile_prover_runtime();
    let request: String = match env.get_string(&request_json) {
        Ok(value) => value.into(),
        Err(err) => {
            return java_string(
                &mut env,
                format!(r#"{{"error":"invalid JNI request string: {err}"}}"#),
            )
        }
    };
    let result = catch_unwind(AssertUnwindSafe(|| {
        zoka::mobile::build_private_transfer_json(&request)
    }));
    let response = match result {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"local Zoka proof generation panicked"}"#.to_string(),
    };
    java_string(&mut env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_benchmarkPrivateTransaction(
    mut env: JNIEnv,
    _class: JClass,
    input_count: jint,
    trusted_setup_dir: JString,
) -> jstring {
    configure_mobile_prover_runtime();
    let trusted_setup_dir: String = match env.get_string(&trusted_setup_dir) {
        Ok(value) => value.into(),
        Err(err) => {
            return java_string(
                &mut env,
                format!(r#"{{"error":"invalid trusted setup path: {err}"}}"#),
            )
        }
    };
    let result = catch_unwind(AssertUnwindSafe(|| {
        usize::try_from(input_count)
            .map_err(|_| "input_count must be positive".to_string())
            .and_then(|count| zoka::mobile::benchmark_private_tx_json(count, &trusted_setup_dir))
    }));
    let response = match result {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"synthetic Zoka benchmark panicked"}"#.to_string(),
    };
    java_string(&mut env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_preloadTrustedSetup(
    mut env: JNIEnv,
    _class: JClass,
    trusted_setup_dir: JString,
) -> jstring {
    configure_mobile_prover_runtime();
    let trusted_setup_dir: String = match env.get_string(&trusted_setup_dir) {
        Ok(value) => value.into(),
        Err(err) => {
            return java_string(
                &mut env,
                format!(r#"{{"error":"invalid trusted setup path: {err}"}}"#),
            )
        }
    };
    let result = catch_unwind(AssertUnwindSafe(|| {
        zoka::mobile::preload_trusted_setup_json(&trusted_setup_dir)
    }));
    let response = match result {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"trusted setup preload panicked"}"#.to_string(),
    };
    java_string(&mut env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeWallet_benchmarkPrivateTransactionV2I1Lab(
    mut env: JNIEnv,
    _class: JClass,
    trusted_setup_dir: JString,
) -> jstring {
    configure_mobile_prover_runtime();
    let trusted_setup_dir: String = match env.get_string(&trusted_setup_dir) {
        Ok(value) => value.into(),
        Err(err) => {
            return java_string(
                &mut env,
                format!(r#"{{"error":"invalid trusted setup path: {err}"}}"#),
            )
        }
    };
    let result = catch_unwind(AssertUnwindSafe(|| {
        zoka::mobile::benchmark_private_tx_v2_i1_lab_json(&trusted_setup_dir)
    }));
    let response = match result {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"v2 i1 lab benchmark panicked"}"#.to_string(),
    };
    java_string(&mut env, response)
}

fn json_string(value: &str) -> String {
    let mut out = String::with_capacity(value.len() + 2);
    out.push('"');
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c.is_control() => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

// ── Red movil ────────────────────────────────────────────────────────────────
//
// ZKAPriv hablaba con la red por una API REST contra nodos centrales. Esos
// nodos ya no existen y la wallet quedo muerta. Estas funciones la conectan
// como un participante mas: publica sus transacciones por gossip y pide los
// bloques que necesita para escanear sus notas.
//
// El nodo ya venia compilado dentro de este .so; lo unico que faltaba era
// exponerlo.

/// Ayudante comun: leer un JString, correr algo que devuelve JSON y responder
/// en el mismo formato que el resto del puente, con los panicos atrapados.
fn responder_json<F>(env: &mut JNIEnv, entrada: JString, que_fallo: &str, f: F) -> jstring
where
    F: FnOnce(&str) -> Result<String, String>,
{
    let texto: String = match env.get_string(&entrada) {
        Ok(v) => v.into(),
        Err(err) => {
            return java_string(env, format!(r#"{{"error":"cadena JNI invalida: {err}"}}"#))
        }
    };
    let salida = catch_unwind(AssertUnwindSafe(|| f(&texto)));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => format!(r#"{{"error":"{que_fallo}"}}"#),
    };
    java_string(env, respuesta)
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_connect0(
    mut env: JNIEnv,
    _class: JClass,
    config_json: JString,
) -> jstring {
    responder_json(&mut env, config_json, "la conexion a la red entro en panico", |cfg| {
        zoka::mobile_net::connect_json(cfg)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_publishPrivateTx0(
    mut env: JNIEnv,
    _class: JClass,
    tx_json: JString,
) -> jstring {
    responder_json(&mut env, tx_json, "la publicacion entro en panico", |tx| {
        zoka::mobile_net::publish_private_tx_json(tx)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_status0(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let salida = catch_unwind(AssertUnwindSafe(zoka::mobile_net::status_json));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"la consulta de estado entro en panico"}"#.to_string(),
    };
    java_string(&mut env, respuesta)
}

#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_disconnect0(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let salida = catch_unwind(AssertUnwindSafe(zoka::mobile_net::disconnect));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"la desconexion entro en panico"}"#.to_string(),
    };
    java_string(&mut env, respuesta)
}
