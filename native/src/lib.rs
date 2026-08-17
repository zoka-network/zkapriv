mod tor;

use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
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

/// Altura de la punta de la red: `{"height":N}`.
///
/// Se aprende escuchando los anuncios de bloque de los pares, no
/// preguntandosela a ningun servidor. Recien conectado puede tardar hasta un
/// par de minutos, que es lo que tarda en pasar un bloque nuevo.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_chainHeight0(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let salida = catch_unwind(AssertUnwindSafe(zoka::mobile_net::chain_height_json));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"la consulta de altura entro en panico"}"#.to_string(),
    };
    java_string(&mut env, respuesta)
}

/// Bloques crudos de `from` a `to`, con la misma forma que devolvia la API REST
/// para que el escaner de la wallet no note el cambio de origen.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_blocksRange0(
    mut env: JNIEnv,
    _class: JClass,
    from: jlong,
    to: jlong,
) -> jstring {
    if from < 0 || to < 0 {
        return java_string(&mut env, r#"{"error":"las alturas no pueden ser negativas"}"#.to_string());
    }
    let salida = catch_unwind(AssertUnwindSafe(|| {
        zoka::mobile_net::blocks_range_json(from as u64, to as u64)
    }));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"la busqueda de bloques entro en panico"}"#.to_string(),
    };
    java_string(&mut env, respuesta)
}

/// Resumenes de bloque de un tramo: lo justo para escanear un saldo.
///
/// Un bloque entero pesa unos 4.500 bytes y mas de la mitad es la prueba de
/// rango del coinbase, que solo hace falta para gastar. Bajar la cadena asi son
/// 77 MB sobre Tor. Un resumen son unos 500 bytes. Si el par no conoce el
/// protocolo, por dentro se cae a bloques enteros sin que quien llama se entere.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_blocksDigestRange0(
    mut env: JNIEnv,
    _class: JClass,
    from: jlong,
    to: jlong,
) -> jstring {
    if from < 0 || to < 0 {
        return java_string(&mut env, r#"{"error":"las alturas no pueden ser negativas"}"#.to_string());
    }
    let salida = catch_unwind(AssertUnwindSafe(|| {
        zoka::mobile_net::blocks_digest_range_json(from as u64, to as u64)
    }));
    let respuesta = match salida {
        Ok(Ok(json)) => json,
        Ok(Err(err)) => format!(r#"{{"error":{}}}"#, json_string(&err)),
        Err(_) => r#"{"error":"la busqueda de resumenes entro en panico"}"#.to_string(),
    };
    java_string(&mut env, respuesta)
}

/// Cuerpo de una transaccion privada por su hash.
///
/// Casi siempre sale del cache que dejaron los bloques ya bajados; solo va a la
/// red si ese bloque llego sin el cuerpo.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_privateTxBody0(
    mut env: JNIEnv,
    _class: JClass,
    tx_hash: JString,
) -> jstring {
    responder_json(&mut env, tx_hash, "la busqueda del cuerpo entro en panico", |hash| {
        zoka::mobile_net::private_tx_body_json(hash)
    })
}

/// Muchos cuerpos de una sola vez: `{"bodies":{hash:cuerpo},"missing":[hash]}`.
///
/// Recibe los hashes como un JSON array, no como varargs, porque son cientos y
/// cruzar la frontera JNI una vez por hash seria repetir el problema que este
/// camino existe para resolver.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_privateTxBodies0(
    mut env: JNIEnv,
    _class: JClass,
    tx_hashes_json: JString,
) -> jstring {
    responder_json(
        &mut env,
        tx_hashes_json,
        "la busqueda de cuerpos en lote entro en panico",
        |json| {
            let hashes: Vec<String> = serde_json::from_str(json)
                .map_err(|e| format!("la lista de hashes no es un JSON array valido: {e}"))?;
            zoka::mobile_net::private_tx_bodies_json(&hashes)
        },
    )
}

/// Levanta Tor dentro de la app y devuelve la direccion del proxy SOCKS5 local.
///
/// El nodo la recibe despues en `connect`, igual que recibiria la del Tor del
/// sistema en una PC. Bloquea hasta que Tor termina de arrancar -- decenas de
/// segundos en una red movil --, asi que hay que llamarla desde un hilo de
/// fondo.
///
/// `cacheDir` tiene que ser el directorio privado de la aplicacion: es el unico
/// donde se puede escribir sin permisos y el unico que se limpia al
/// desinstalar.
#[no_mangle]
pub extern "system" fn Java_com_zoka_zkapriv_network_nativebridge_ZokaNativeNetwork_startTor0(
    mut env: JNIEnv,
    _class: JClass,
    cache_dir: JString,
) -> jstring {
    responder_json(&mut env, cache_dir, "el arranque de Tor entro en panico", |dir| {
        crate::tor::start_blocking(dir).map(|addr| format!(r#"{{"proxy":{}}}"#, json_string(&addr)))
    })
}
