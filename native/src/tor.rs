//! Tor embebido para el telefono, expuesto como proxy SOCKS5 local.
//!
//! El nodo alcanza direcciones onion a traves de un proxy SOCKS5 configurable
//! -- en una PC, el Tor del sistema o el que trae ZSilent. En un telefono no
//! hay ninguno, y pedirle al usuario que instale otra aplicacion para poder
//! usar su wallet es una barrera que no corresponde en algo que se vende como
//! privado por defecto.
//!
//! Asi que Arti (Tor en Rust) corre dentro de este mismo proceso y expone un
//! SOCKS5 en localhost. Desde el punto de vista del nodo no cambia nada: le
//! pasas `127.0.0.1:puerto` igual que le pasarias el Tor del sistema.
//!
//! **Por que un SOCKS propio y no el proxy de Arti.** El crate `arti` incluye
//! su proxy, pero arrastra la interfaz de linea de comandos y su configuracion
//! por archivos, que en Android no aplican. Este puente son unas pocas decenas
//! de lineas y solo implementa lo que el nodo realmente usa: CONNECT a un
//! nombre de dominio, sin autenticacion.

use arti_client::{TorClient, TorClientConfig};
use std::io;
use std::sync::OnceLock;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

/// Version del protocolo SOCKS y codigos que usa este puente.
const SOCKS5: u8 = 0x05;
const SIN_AUTENTICACION: u8 = 0x00;
const CMD_CONNECT: u8 = 0x01;
const TIPO_IPV4: u8 = 0x01;
const TIPO_DOMINIO: u8 = 0x03;
const TIPO_IPV6: u8 = 0x04;
const RESP_OK: u8 = 0x00;
const RESP_FALLO: u8 = 0x01;
const RESP_NO_SOPORTADO: u8 = 0x07;

/// La direccion del proxy una vez levantado. Se guarda para que llamadas
/// repetidas devuelvan el mismo Tor en vez de arrancar un segundo cliente:
/// dos clientes Tor en un telefono serian el doble de circuitos y el doble de
/// bateria, sin ninguna ventaja.
static PROXY: OnceLock<String> = OnceLock::new();

/// Levanta Tor y devuelve la direccion del proxy SOCKS5 local, lista para
/// pasarle al nodo.
///
/// Bloquea hasta que Tor termina de arrancar, que en una red movil puede tomar
/// decenas de segundos: hay que bajar el directorio de la red y armar los
/// primeros circuitos. Llamar desde un hilo de fondo.
///
/// `cache_dir` debe ser un directorio propio de la aplicacion (el de datos
/// internos), porque Arti guarda ahi el directorio de la red y sus claves.
pub fn start_blocking(cache_dir: &str) -> Result<String, String> {
    if let Some(addr) = PROXY.get() {
        return Ok(addr.clone());
    }

    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|e| format!("no pude crear el runtime de Tor: {e}"))?;

    let cache = cache_dir.to_string();
    let (tx, rx) = std::sync::mpsc::channel();

    std::thread::Builder::new()
        .name("zoka-tor".to_string())
        .spawn(move || {
            rt.block_on(async move {
                match arrancar(&cache).await {
                    Ok((addr, listener, cliente)) => {
                        let _ = tx.send(Ok(addr));
                        servir(listener, cliente).await;
                    }
                    Err(e) => {
                        let _ = tx.send(Err(e));
                    }
                }
            });
        })
        .map_err(|e| format!("no pude crear el hilo de Tor: {e}"))?;

    let addr = rx
        .recv()
        .map_err(|_| "el hilo de Tor murio al arrancar".to_string())??;
    let _ = PROXY.set(addr.clone());
    Ok(addr)
}

/// Si Tor ya esta levantado, su direccion.
pub fn proxy_addr() -> Option<String> {
    PROXY.get().cloned()
}

async fn arrancar(
    cache_dir: &str,
) -> Result<(String, TcpListener, std::sync::Arc<TorClient<tor_rtcompat::PreferredRuntime>>), String> {
    let mut cfg = TorClientConfig::builder();
    // Arti necesita un lugar donde guardar el directorio de la red y sus
    // claves. En Android tiene que ser el directorio privado de la app: es el
    // unico al que se puede escribir sin permisos y el unico que se borra
    // cuando el usuario desinstala.
    let base = std::path::PathBuf::from(cache_dir);
    cfg.storage()
        .cache_dir(arti_client::config::CfgPath::new_literal(base.join("tor-cache")))
        .state_dir(arti_client::config::CfgPath::new_literal(base.join("tor-state")));
    let cfg = cfg
        .build()
        .map_err(|e| format!("configuracion de Tor invalida: {e}"))?;

    let cliente = TorClient::create_bootstrapped(cfg)
        .await
        .map_err(|e| format!("Tor no pudo arrancar: {e}"))?;

    // Puerto 0: que el sistema elija uno libre. Fijar un puerto invitaria a
    // chocar con otra aplicacion del telefono.
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(|e| format!("no pude abrir el proxy local: {e}"))?;
    let addr = listener
        .local_addr()
        .map_err(|e| format!("no pude leer el puerto del proxy: {e}"))?
        .to_string();

    Ok((addr, listener, cliente))
}

async fn servir(
    listener: TcpListener,
    cliente: std::sync::Arc<TorClient<tor_rtcompat::PreferredRuntime>>,
) {
    loop {
        let Ok((socket, _)) = listener.accept().await else {
            continue;
        };
        let cliente = cliente.isolated_client();
        tokio::spawn(async move {
            let _ = atender(socket, cliente).await;
        });
    }
}

/// Atiende una conexion SOCKS5: saludo, pedido y luego copiar bytes.
async fn atender(
    mut socket: TcpStream,
    cliente: std::sync::Arc<TorClient<tor_rtcompat::PreferredRuntime>>,
) -> io::Result<()> {
    // Saludo: version, cuantos metodos, y la lista. Solo se acepta "sin
    // autenticacion" porque el proxy escucha en localhost y nadie mas que esta
    // misma aplicacion puede alcanzarlo.
    let mut cabecera = [0u8; 2];
    socket.read_exact(&mut cabecera).await?;
    if cabecera[0] != SOCKS5 {
        return Ok(());
    }
    let mut metodos = vec![0u8; cabecera[1] as usize];
    socket.read_exact(&mut metodos).await?;
    socket.write_all(&[SOCKS5, SIN_AUTENTICACION]).await?;

    // Pedido: version, comando, reservado, tipo de direccion.
    let mut pedido = [0u8; 4];
    socket.read_exact(&mut pedido).await?;
    if pedido[1] != CMD_CONNECT {
        responder(&mut socket, RESP_NO_SOPORTADO).await?;
        return Ok(());
    }

    let host = match pedido[3] {
        TIPO_DOMINIO => {
            let mut largo = [0u8; 1];
            socket.read_exact(&mut largo).await?;
            let mut nombre = vec![0u8; largo[0] as usize];
            socket.read_exact(&mut nombre).await?;
            String::from_utf8_lossy(&nombre).to_string()
        }
        TIPO_IPV4 => {
            let mut ip = [0u8; 4];
            socket.read_exact(&mut ip).await?;
            std::net::Ipv4Addr::from(ip).to_string()
        }
        TIPO_IPV6 => {
            let mut ip = [0u8; 16];
            socket.read_exact(&mut ip).await?;
            std::net::Ipv6Addr::from(ip).to_string()
        }
        _ => {
            responder(&mut socket, RESP_NO_SOPORTADO).await?;
            return Ok(());
        }
    };

    let mut puerto = [0u8; 2];
    socket.read_exact(&mut puerto).await?;
    let puerto = u16::from_be_bytes(puerto);

    let mut flujo = match cliente.connect((host.as_str(), puerto)).await {
        Ok(f) => f,
        Err(_) => {
            responder(&mut socket, RESP_FALLO).await?;
            return Ok(());
        }
    };
    responder(&mut socket, RESP_OK).await?;

    // De aca en mas es un tubo: lo que entra por un lado sale por el otro.
    let (mut lee_socket, mut escribe_socket) = socket.split();
    let (mut lee_tor, mut escribe_tor) = flujo.split();
    let ida = tokio::io::copy(&mut lee_socket, &mut escribe_tor);
    let vuelta = tokio::io::copy(&mut lee_tor, &mut escribe_socket);
    let _ = tokio::try_join!(ida, vuelta);
    Ok(())
}

/// Respuesta SOCKS5. La direccion enlazada va en cero: el nodo no la usa, y
/// devolver la real filtraria por donde salio el circuito.
async fn responder(socket: &mut TcpStream, codigo: u8) -> io::Result<()> {
    socket
        .write_all(&[SOCKS5, codigo, 0x00, TIPO_IPV4, 0, 0, 0, 0, 0, 0])
        .await
}
