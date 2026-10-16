package com.zoka.zkapriv.ui.qrscanner

import java.security.MessageDigest

/**
 * Textos largos en varios QR chicos que se muestran en secuencia.
 *
 * Una `zkq1` tiene 1.715 caracteres: en un solo QR son unos 141 módulos por
 * lado, y la cámara no lo lee desde una pantalla (15/10/2026: ni ZKAPriv ni
 * ZSilent leían el QR del otro). Partida en trozos de [PART_CHARS], cada QR
 * queda de unos 61 módulos y se lee al instante; el lector junta las partes y
 * comprueba el resultado con su huella.
 *
 * Formato de cada parte: `zkqp:<i>/<n>:<huella>:<trozo>`, con `i` desde 1 y la
 * huella = los primeros 8 hex del SHA-256 del texto completo. ZSilent usa el
 * mismo formato (`com.zsilent.core.ui.components.MultiPartQr`).
 */
object MultiPartQr {
    const val PREFIX = "zkqp:"
    const val PART_CHARS = 300
    /** Hasta este largo un solo QR se lee bien y no se parte. */
    const val SPLIT_ABOVE_CHARS = 400
    const val MAX_PARTS = 64

    data class Part(val index: Int, val total: Int, val fingerprint: String, val chunk: String)

    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }

    /** Las partes a mostrar; un texto corto queda en una sola, sin cabecera. */
    fun split(text: String, partChars: Int = PART_CHARS): List<String> {
        require(partChars > 0) { "partChars must be positive" }
        if (text.length <= SPLIT_ABOVE_CHARS) return listOf(text)
        val chunks = text.chunked(partChars)
        require(chunks.size <= MAX_PARTS) { "text too long for a multi-part QR" }
        val huella = fingerprint(text)
        return chunks.mapIndexed { index, chunk ->
            "$PREFIX${index + 1}/${chunks.size}:$huella:$chunk"
        }
    }

    fun isPart(text: String): Boolean = text.startsWith(PREFIX)

    fun parse(text: String): Part? {
        if (!isPart(text)) return null
        val rest = text.removePrefix(PREFIX)
        val first = rest.indexOf(':').takeIf { it >= 0 } ?: return null
        val second = rest.indexOf(':', first + 1).takeIf { it >= 0 } ?: return null
        val position = rest.substring(0, first).split('/')
        val index = position.getOrNull(0)?.toIntOrNull() ?: return null
        val total = position.getOrNull(1)?.toIntOrNull() ?: return null
        if (position.size != 2 || total !in 1..MAX_PARTS || index !in 1..total) return null
        val huella = rest.substring(first + 1, second)
        if (huella.length != 8) return null
        return Part(index, total, huella, rest.substring(second + 1))
    }
}

/**
 * Junta las partes de un QR múltiple, en cualquier orden y aunque se repitan
 * (la animación da vueltas). Si aparece una parte de otro texto, empieza de
 * nuevo con ese.
 */
class MultiPartQrCollector {
    sealed interface Result {
        data class Partial(val have: Int, val total: Int) : Result
        data class Complete(val text: String) : Result
        object Ignored : Result
    }

    private var fingerprint: String? = null
    private var total = 0
    private val chunks = HashMap<Int, String>()

    fun accept(text: String): Result {
        val part = MultiPartQr.parse(text) ?: return Result.Ignored
        if (part.fingerprint != fingerprint || part.total != total) {
            fingerprint = part.fingerprint
            total = part.total
            chunks.clear()
        }
        chunks[part.index] = part.chunk
        if (chunks.size < total) return Result.Partial(chunks.size, total)
        val joined = (1..total).joinToString("") { chunks.getValue(it) }
        // La huella cubre el texto entero: una parte mal leída o de otro texto
        // no puede colarse en el resultado.
        if (MultiPartQr.fingerprint(joined) != part.fingerprint) {
            reset()
            return Result.Ignored
        }
        return Result.Complete(joined)
    }

    fun reset() {
        fingerprint = null
        total = 0
        chunks.clear()
    }
}
