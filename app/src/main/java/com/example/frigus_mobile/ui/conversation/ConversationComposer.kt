package com.example.frigus_mobile.ui.conversation

data class ConversationDraftSnapshot internal constructor(val text: String, internal val revision: Long)

/** A late send confirmation may clear only the exact editing revision that was submitted. */
class ConversationComposer {
    var text: String = ""
        private set
    private var revision = 0L
    fun edit(value: String) {
        if (value != text) { text = value; revision++ }
    }
    fun snapshot(): ConversationDraftSnapshot = ConversationDraftSnapshot(text, revision)
    fun confirm(submitted: ConversationDraftSnapshot): Boolean {
        if (revision != submitted.revision || text != submitted.text) return false
        edit("")
        return true
    }
}
