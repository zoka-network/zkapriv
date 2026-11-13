package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.security.wallet.ENGLISH_WORDLIST

/**
 * Escribir una frase de recuperación casilla por casilla, sin interfaz:
 * sugerir palabras, repartir lo pegado y armar la frase.
 *
 * La lista es la de BIP-39 en inglés, la misma que usa el motor (`bip39` 2).
 * Que cada palabra exista no alcanza: el checksum lo valida el motor.
 */
internal object PhraseEntry {
    const val WORDS = 24

    private val known: Set<String> = ENGLISH_WORDLIST.toHashSet()

    /** Todo lo que no es letra separa palabras: espacios, saltos, comas, "1." */
    private val separators = Regex("[^a-z]+")

    /** Las palabras de un texto escrito o pegado, en minúscula. */
    fun words(text: String): List<String> =
        text.lowercase().split(separators).filter { it.isNotEmpty() }

    fun isKnown(word: String): Boolean = word in known

    /** Hasta `limit` palabras que empiezan con `prefix`, en el orden de la lista. */
    fun suggest(prefix: String, limit: Int = 4): List<String> {
        val start = words(prefix).firstOrNull() ?: return emptyList()
        // La lista está ordenada: se busca dónde empezaría el prefijo.
        var index = ENGLISH_WORDLIST.binarySearch(start).let { if (it < 0) -it - 1 else it }
        val found = ArrayList<String>(limit)
        while (index < ENGLISH_WORDLIST.size && found.size < limit) {
            val word = ENGLISH_WORDLIST[index++]
            if (!word.startsWith(start)) break
            found.add(word)
        }
        return found
    }

    /**
     * Lo escrito o pegado en la casilla `at`. Una sola palabra queda en esa
     * casilla, y un espacio después de una palabra de la lista pasa a la
     * siguiente. Varias palabras se reparten desde esa casilla; las que no
     * entran se descartan. Devuelve las casillas y cuál toma el foco.
     */
    fun type(cells: List<String>, at: Int, text: String): Pair<List<String>, Int> {
        val parts = words(text)
        val next = cells.toMutableList()
        if (parts.size <= 1) {
            next[at] = parts.firstOrNull().orEmpty()
            val closed = text.isNotEmpty() && text.last().lowercaseChar() !in 'a'..'z'
            val advance = closed && isKnown(next[at]) && at < WORDS - 1
            return next to if (advance) at + 1 else at
        }
        var index = at
        for (part in parts) {
            if (index >= WORDS) break
            next[index++] = part
        }
        return next to index.coerceAtMost(WORDS - 1)
    }

    /** Cuántas casillas tienen una palabra de la lista. */
    fun validCount(cells: List<String>): Int = cells.count(::isKnown)

    fun complete(cells: List<String>): Boolean = cells.size == WORDS && cells.all(::isKnown)

    /** La frase, con un espacio entre palabras. */
    fun phrase(cells: List<String>): String = cells.joinToString(" ")
}
