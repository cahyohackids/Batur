package com.cahyo.batur.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentParsingTest {

    @Test
    fun durasiDariPerintah() {
        assertEquals(1500L, parseDurationSeconds("Timer 25 menit buat fokus belajar"))
        assertEquals(5400L, parseDurationSeconds("hitung mundur 1 jam 30 menit"))
        assertEquals(90L, parseDurationSeconds("Timer 90 detik masak mi"))
        assertEquals(1800L, parseDurationSeconds("timer 0,5 jam"))
        assertNull(parseDurationSeconds("pasang timer dong"))
    }

    @Test
    fun toolCallDariTeksJson() {
        val tools = setOf("set_timer", "set_alarm")
        val call = OnDeviceAgentEngine.parseTextToolCall(
            "{\n  \"name\": \"set_timer\",\n  \"arguments\": {\n    \"seconds\": 15000\n  }\n}",
            tools,
        )
        assertEquals("set_timer", call?.name)
        assertEquals(15000L, call?.args?.get("seconds")?.asLong)

        val fenced = OnDeviceAgentEngine.parseTextToolCall(
            "```json\n{\"name\":\"set_alarm\",\"arguments\":{\"hour\":5.0,\"minute\":30}}\n```",
            tools,
        )
        assertEquals("5", fenced?.args?.get("hour")?.toString())

        assertNull(OnDeviceAgentEngine.parseTextToolCall("{\"name\":\"hapus_semua\",\"arguments\":{}}", tools))
        assertNull(OnDeviceAgentEngine.parseTextToolCall("RAM itu memori sementara.", tools))
    }

    @Test
    fun blokThinkingDibuang() {
        assertEquals("Halo", OnDeviceAgentEngine.stripThinking("<think>\nhmm\n</think>\nHalo"))
    }

    @Test
    fun catatanEksplisit() {
        assertEquals("bayar kos tanggal 20", explicitNote("Catat: bayar kos tanggal 20"))
        assertEquals("beli galon", explicitNote("tulis catatan : beli galon"))
        assertNull(explicitNote("catat jadwal rapat besok"))
        assertNull(explicitNote("Catat:   "))
    }
}
