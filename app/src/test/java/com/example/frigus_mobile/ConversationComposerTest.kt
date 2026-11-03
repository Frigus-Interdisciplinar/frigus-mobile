package com.example.frigus_mobile

import com.example.frigus_mobile.ui.conversation.ConversationComposer
import org.junit.Assert.*
import org.junit.Test

class ConversationComposerTest {
    @Test fun lateConfirmationPreservesTheNextMessageAlreadyBeingTyped() {
        val composer = ConversationComposer()
        composer.edit("Comprei o leite.")
        val inFlight = composer.snapshot()
        composer.edit("Também precisamos de ovos.")
        assertFalse(composer.confirm(inFlight))
        assertEquals("Também precisamos de ovos.", composer.text)
        val nextInFlight = composer.snapshot()
        assertTrue(composer.confirm(nextInFlight))
        assertEquals("", composer.text)
    }

    @Test fun editingAwayAndBackToTheSameTextIsStillANewDraftRevision() {
        val composer = ConversationComposer()
        composer.edit("Lista pronta")
        val inFlight = composer.snapshot()
        composer.edit("Lista pronta para amanhã")
        composer.edit("Lista pronta")
        assertFalse(composer.confirm(inFlight))
        assertEquals("Lista pronta", composer.text)
    }
}
