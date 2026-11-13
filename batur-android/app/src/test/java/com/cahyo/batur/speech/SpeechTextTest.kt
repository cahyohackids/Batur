package com.cahyo.batur.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    @Test
    fun teksPendekHanyaDirapikan() {
        assertEquals("Alarm 05.00 dipasang.", SpeechPlayer.prepareText("  Alarm  05.00\n dipasang. "))
    }

    @Test
    fun teksPanjangDipotongDiAkhirKalimat() {
        val sentence = "Ini kalimat yang cukup panjang untuk dibacakan Batur. "
        val text = SpeechPlayer.prepareText(sentence.repeat(40))
        assertTrue(text.length <= SpeechPlayer.MAX_TEXT)
        assertTrue(text.endsWith("Batur."))
    }

    @Test
    fun teksPanjangTanpaTitikDipotongDiSpasi() {
        val text = SpeechPlayer.prepareText("kata ".repeat(300))
        assertTrue(text.length <= SpeechPlayer.MAX_TEXT + 1)
        assertTrue(text.endsWith("kata…"))
    }

    @Test
    fun cacheBedaUntukSuaraBerbeda() {
        val a = SpeechPlayer.cacheName("Halo", "perempuan")
        assertEquals(a, SpeechPlayer.cacheName("Halo", "perempuan"))
        assertNotEquals(a, SpeechPlayer.cacheName("Halo", "laki-laki"))
        assertTrue(a.matches(Regex("[0-9a-f]{64}\\.mp3")))
    }
}
