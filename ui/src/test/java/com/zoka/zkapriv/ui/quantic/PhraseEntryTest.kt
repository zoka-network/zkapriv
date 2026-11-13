package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.security.wallet.ENGLISH_WORDLIST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseEntryTest {
    private val empty = List(PhraseEntry.WORDS) { "" }

    @Test
    fun the_list_has_the_2048_words_in_the_order_the_search_needs() {
        assertEquals(2048, ENGLISH_WORDLIST.size)
        assertEquals(ENGLISH_WORDLIST.sorted(), ENGLISH_WORDLIST)
    }

    @Test
    fun suggestions_follow_the_prefix_in_list_order() {
        assertEquals(listOf("access", "accident", "account", "accuse"), PhraseEntry.suggest("acc"))
        assertEquals(listOf("zone", "zoo"), PhraseEntry.suggest("zo"))
        assertEquals(listOf("zoo"), PhraseEntry.suggest("ZOO"))
        assertEquals(emptyList<String>(), PhraseEntry.suggest(""))
        assertEquals(emptyList<String>(), PhraseEntry.suggest("qq"))
    }

    @Test
    fun a_whole_word_comes_first_even_if_longer_ones_start_the_same() {
        assertEquals(listOf("act", "action", "actor", "actress"), PhraseEntry.suggest("act"))
    }

    @Test
    fun typing_stays_in_the_cell_and_a_space_after_a_known_word_moves_on() {
        val (typing, same) = PhraseEntry.type(empty, 0, "aban")
        assertEquals("aban", typing[0])
        assertEquals(0, same)

        val (done, next) = PhraseEntry.type(typing, 0, "abandon ")
        assertEquals("abandon", done[0])
        assertEquals(1, next)
    }

    @Test
    fun a_space_after_an_unknown_word_does_not_move_on() {
        val (cells, focus) = PhraseEntry.type(empty, 3, "abilty ")
        assertEquals("abilty", cells[3])
        assertEquals(3, focus)
        assertFalse(PhraseEntry.isKnown(cells[3]))
    }

    @Test
    fun the_last_cell_does_not_move_on() {
        val (cells, focus) = PhraseEntry.type(empty, 23, "zoo ")
        assertEquals("zoo", cells[23])
        assertEquals(23, focus)
    }

    @Test
    fun deleting_everything_leaves_the_cell_empty() {
        val (cells, focus) = PhraseEntry.type(empty.toMutableList().also { it[5] = "zoo" }, 5, "")
        assertEquals("", cells[5])
        assertEquals(5, focus)
    }

    @Test
    fun pasted_words_spread_from_the_cell_whatever_separates_them() {
        val words = ENGLISH_WORDLIST.take(24)
        val pasted = words.mapIndexed { i, word -> "${i + 1}. $word" }.joinToString("\n")
        val (cells, focus) = PhraseEntry.type(empty, 0, pasted)
        assertEquals(words, cells)
        assertEquals(23, focus)
        assertTrue(PhraseEntry.complete(cells))
        assertEquals(words.joinToString(" "), PhraseEntry.phrase(cells))
    }

    @Test
    fun pasted_words_that_do_not_fit_are_dropped() {
        val (cells, focus) = PhraseEntry.type(empty, 22, "zone zoo zebra zero")
        assertEquals("zone", cells[22])
        assertEquals("zoo", cells[23])
        assertEquals(23, focus)
    }

    @Test
    fun capitals_and_symbols_are_cleaned() {
        assertEquals("abandon", PhraseEntry.type(empty, 0, "ABANDON").first[0])
        assertEquals(listOf("abandon", "ability"), PhraseEntry.words("  Abandon,\tability. "))
    }

    @Test
    fun it_is_complete_only_with_24_known_words() {
        val cells = ENGLISH_WORDLIST.take(23) + "notaword"
        assertFalse(PhraseEntry.complete(cells))
        assertEquals(23, PhraseEntry.validCount(cells))
        assertFalse(PhraseEntry.complete(ENGLISH_WORDLIST.take(23)))
    }
}
