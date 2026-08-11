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

    @JvmStatic private external fun connect0(configJson: String): String
    @JvmStatic private external fun publishPrivateTx0(txJson: String): String
    @JvmStatic private external fun status0(): String
    @JvmStatic private external fun disconnect0(): String
}
