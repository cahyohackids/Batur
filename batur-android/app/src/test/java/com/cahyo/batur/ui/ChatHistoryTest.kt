package com.cahyo.batur.ui

import com.cahyo.batur.ui.agent.ChatHistory
import com.cahyo.batur.ui.agent.ChatMessage
import com.cahyo.batur.ui.agent.ConfirmState
import com.cahyo.batur.ui.agent.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHistoryTest {

    private fun confirmation(id: Long, state: ConfirmState) = ChatMessage.Confirmation(
        id = id,
        title = "Pasang alarm",
        skillName = "Alarm",
        icon = "alarm",
        functionName = "set_alarm",
        details = listOf("Jam" to "05.00"),
        mechanism = "AlarmClock.ACTION_SET_ALARM",
        mode = "cloud",
        state = state,
    )

    @Test
    fun konfirmasiTertundaDipulihkanSebagaiKedaluwarsa() {
        val messages = listOf(
            ChatMessage.User(1, "bangunin jam 5"),
            ChatMessage.Thinking(2),
            confirmation(3, ConfirmState.PENDING),
            confirmation(4, ConfirmState.DONE),
            ChatMessage.Assistant(5, "Alarm dipasang", mode = "cloud", tone = Tone.SUCCESS, taskId = 9),
        )
        val restored = ChatHistory.decode(ChatHistory.encode(messages))
        assertEquals(4, restored.size)
        assertEquals(ConfirmState.EXPIRED, (restored[1] as ChatMessage.Confirmation).state)
        assertEquals(ConfirmState.DONE, (restored[2] as ChatMessage.Confirmation).state)
        assertEquals(listOf("Jam" to "05.00"), (restored[1] as ChatMessage.Confirmation).details)
        assertEquals(messages[4], restored[3])
    }

    @Test
    fun hanyaPesanTerakhirYangDisimpan() {
        val messages = (1L..80L).map { ChatMessage.User(it, "pesan $it") }
        val restored = ChatHistory.decode(ChatHistory.encode(messages))
        assertEquals(ChatHistory.MAX_MESSAGES, restored.size)
        assertEquals(31L, restored.first().id)
    }

    @Test
    fun kosongAtauRusak() {
        assertNull(ChatHistory.encode(emptyList()))
        assertEquals(emptyList<ChatMessage>(), ChatHistory.decode("bukan json"))
    }
}
