package com.zoka.zkapriv.network.nativebridge

/**
 * Puente a la red P2P que corre dentro del `.so`.
 *
 * ZKAPriv hablaba con la red por una API REST contra nodos centrales. Esos
 * nodos se dieron de baja y la wallet quedó muerta: apuntaba a dos direcciones
 * que ya no existen. Esto la conecta como un participante más — publica sus
 * transacciones por gossip y pide los bloques que necesita para escanear sus
 * notas, sin depender de que nadie mantenga un servidor.
 *
 * El nodo ya venía compilado dentro de esta misma biblioteca; lo único que
 * faltaba era exponerlo.
 *
 * Todas las llamadas devuelven JSON, igual que [ZokaNativeWallet], y ninguna
 * lanza excepciones desde el lado nativo: los errores viajan como
 * `{"error": "..."}`.
 */
object ZokaNativeNetwork {
    private val loadError: Throwable? = try {
        System.loadLibrary("zoka_mobile")
        null
    } catch (error: Throwable) {
        error
    }

    /** Si la biblioteca nativa está disponible en este dispositivo. */
    val isAvailable: Boolean get() = loadError == null

    private fun requireLibrary() {
        val error = loadError
        check(error == null) {
            "El motor de red de Zoka no está empaquetado para este dispositivo: ${error?.message}"
        }
    }

    /**
     * Levanta la red y marca a las semillas.
     *
     * Es idempotente: si ya estaba conectada devuelve el estado actual en vez
     * de arrancar un segundo nodo — dos nodos en el mismo proceso pelearían por
     * la misma identidad de red.
     *
     * Puede tardar decenas de segundos: armar un circuito Tor no es inmediato,
     * y en una red móvil menos. Llamarla desde un hilo de fondo.
     *
     * @param configJson `{"seeds":["/onion3/..."],"tor_proxy":"127.0.0.1:9050"}`
     */
    fun connect(configJson: String): String {
        requireLibrary()
        return connect0(configJson)
    }

    /**
     * Publica una transacción privada ya construida y probada en el teléfono.
     *
     * La wallet la arma y la prueba localmente; esto solo la pone en la red.
     * Los mineros la validan como a cualquier otra: este cliente no pide ni
     * recibe ningún trato especial.
     */
    fun publishPrivateTx(txJson: String): String {
        requireLibrary()
        return publishPrivateTx0(txJson)
    }

    /** Estado actual: si hay red y con cuántos pares. */
    fun status(): String {
        requireLibrary()
        return status0()
    }

    /**
     * Corta la red y libera el nodo.
     *
     * Conviene llamarla cuando la app pasa a segundo plano: mantener circuitos
     * Tor abiertos con la pantalla apagada gasta batería sin que nadie mire el
     * resultado.
     */
    fun disconnect(): String {
        requireLibrary()
        return disconnect0()
    }

    /**
     * Levanta Tor dentro de la app y devuelve `{"proxy":"127.0.0.1:PUERTO"}`.
     *
     * Bloquea hasta que Tor termina de arrancar — decenas de segundos en una
     * red móvil, porque hay que bajar el directorio de la red y armar los
     * primeros circuitos. Llamar desde un hilo de fondo.
     *
     * @param cacheDir el directorio privado de la app (`context.filesDir`).
     */
    fun startTor(cacheDir: String): String {
        requireLibrary()
        return startTor0(cacheDir)
    }

    /**
     * Levanta Tor y conecta a la red en un solo paso, que es como la wallet lo
     * necesita: las semillas son direcciones onion y sin Tor no se alcanzan.
     *
     * Si Tor falla devuelve su error tal cual, sin intentar conectar: sin Tor
     * no hay forma de llegar a una onion, así que reintentar solo daría un
     * segundo error menos claro que el primero.
     *
     * Devuelve el mismo JSON de estado que [status].
     */
    fun startTorAndConnect(cacheDir: String, seedsJson: String): String {
        requireLibrary()
        val tor = startTor0(cacheDir)
        val proxy = PROXY_REGEX.find(tor)?.groupValues?.get(1) ?: return tor
        // El directorio va explícito. El nodo guarda su identidad de red en una
        // ruta relativa al directorio de trabajo, que en un servidor es el suyo
        // pero en Android es `/` y es de solo lectura: sin decírselo no llega ni
        // a arrancar.
        return connect0(
            "{\"seeds\":" + seedsJson +
                ",\"tor_proxy\":\"" + proxy + "\"" +
                ",\"data_dir\":" + comoJson(cacheDir) + "}"
        )
    }

    /** Una ruta con contrabarras o comillas rompería el JSON si fuera pegada tal cual. */
    private fun comoJson(texto: String): String =
        "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * Altura de la punta de la red: `{"height":N}`.
     *
     * Se aprende escuchando lo que anuncian los pares, no preguntándosela a
     * ningún servidor. Recién conectada puede tardar hasta un par de minutos:
     * hasta que no pase un bloque nuevo, no hay nada que escuchar.
     */
    fun chainHeight(): String {
        requireLibrary()
        return chainHeight0()
    }

    /**
     * Bloques crudos del rango, con la misma forma que devolvía la API REST.
     *
     * Cada bloque es un pedido punto a punto repartido entre los pares
     * conectados. Sobre Tor un rango grande tarda; llamarla desde un hilo de
     * fondo.
     */
    fun blocksRange(from: Long, to: Long): String {
        requireLibrary()
        return blocksRange0(from, to)
    }

    /**
     * Cuerpo de una transacción privada por su hash.
     *
     * Casi siempre sale del caché que dejaron los bloques ya bajados.
     */
    fun privateTxBody(txHash: String): String {
        requireLibrary()
        return privateTxBody0(txHash)
    }

    private val PROXY_REGEX = Regex("\"proxy\"\\s*:\\s*\"([^\"]+)\"")

    @JvmStatic private external fun startTor0(cacheDir: String): String
    @JvmStatic private external fun chainHeight0(): String
    @JvmStatic private external fun blocksRange0(from: Long, to: Long): String
    @JvmStatic private external fun privateTxBody0(txHash: String): String
    @JvmStatic private external fun connect0(configJson: String): String
    @JvmStatic private external fun publishPrivateTx0(txJson: String): String
    @JvmStatic private external fun status0(): String
    @JvmStatic private external fun disconnect0(): String
}
