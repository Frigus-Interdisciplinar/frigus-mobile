package com.example.frigus_mobile.ui.conversation

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar

class ConversationActivity : HumanConversationActivity() {
    private lateinit var model: ConversationViewModel
    private lateinit var adapter: ConversationMessagesAdapter
    private var group: Boolean? = null
    private var shownError: com.example.frigus_mobile.data.model.ApiError? = null
    private var seenSentRevision = 0L
    private var readWasConfirmed = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        val id = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (id.isNullOrBlank()) { finish(); return }
        setContentView(R.layout.activity_conversation)
        model = ViewModelProvider(this)[ConversationViewModel::class.java]
        if (!model.hasCurrentSession()) { openLogin(); return }
        seenSentRevision = model.state.value?.sentRevision ?: 0L
        findViewById<View>(R.id.btnConversationBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnConversationEditName).visibility = View.GONE
        findViewById<View>(R.id.layoutConversationHeaderTitle).setOnClickListener { showActions() }
        findViewById<View>(R.id.layoutConversationHeaderTitle).contentDescription = getString(R.string.conversation_actions)
        findViewById<EditText>(R.id.etConversationMessage).apply {
            setText(model.draft)
            doAfterTextChanged { model.setDraft(it?.toString().orEmpty()) }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { model.send(); true } else false
            }
        }
        findViewById<View>(R.id.btnConversationSend).setOnClickListener { model.send() }
        findViewById<View>(R.id.tvConversationMessagesError).setOnClickListener { model.load(true) }
        findViewById<RecyclerView>(R.id.rvConversationMessages).layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        model.state.observe(this, ::render)
        model.open(id)
    }
    private fun showActions() {
        val state = model.state.value ?: return
        val actions = mutableListOf(R.string.conversation_refresh)
        if (state.page >= 0 && state.page + 1 < state.totalPages) actions += R.string.conversation_load_previous
        if (model.canMarkRead() && !state.readConfirmed) actions += R.string.conversation_mark_latest_read
        AlertDialog.Builder(this).setTitle(R.string.conversation_actions)
            .setItems(actions.map(::getString).toTypedArray()) { _, position ->
                when (actions[position]) {
                    R.string.conversation_refresh -> model.load(true)
                    R.string.conversation_load_previous -> model.load(more = true)
                    R.string.conversation_mark_latest_read -> model.markRead()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun render(state: ConversationState) {
        if (!currentSession() || !model.hasCurrentSession()) return
        val info = state.conversation
        if (group != info?.isGroup || !::adapter.isInitialized) {
            group = info?.isGroup
            adapter = ConversationMessagesAdapter(info?.isGroup == true)
            findViewById<RecyclerView>(R.id.rvConversationMessages).adapter = adapter
        }
        adapter.submitList(state.messages)
        findViewById<TextView>(R.id.tvConversationTitle).text = info?.title ?: getString(R.string.conversation_generic_title)
        findViewById<TextView>(R.id.tvConversationSubtitle).apply {
            visibility = if (info?.isGroup == true) View.VISIBLE else View.GONE
            text = info?.let { getString(R.string.conversation_subtitle_group, it.memberCount) }
        }
        findViewById<ShapeableImageView>(R.id.ivConversationHeaderAvatar).apply {
            visibility = if (info != null) View.VISIBLE else View.GONE
            if (info != null) setImageDrawable(AvatarUtils.createAvatarDrawable(this@ConversationActivity, info.title, 38, info.isGroup))
        }
        findViewById<View>(R.id.progressConversationMessages).visibility = if (state.loading || state.sending) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnConversationSend).isEnabled = info != null && !state.loading && !state.sending && !state.uncertain
        findViewById<View>(R.id.tvConversationMessagesEmpty).visibility =
            if (info != null && state.messages.isEmpty() && !state.loading && state.error == null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvConversationMessagesError).apply {
            visibility = if (state.messages.isEmpty() && state.error != null) View.VISIBLE else View.GONE
            text = state.error?.userMessage(getString(R.string.conversation_error))
        }
        if (state.sentRevision > seenSentRevision) {
            seenSentRevision = state.sentRevision
            findViewById<EditText>(R.id.etConversationMessage).apply {
                if (text.toString() != model.draft) setText(model.draft)
            }
            findViewById<RecyclerView>(R.id.rvConversationMessages).scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        }
        if (state.readConfirmed && !readWasConfirmed) Toast.makeText(this, R.string.conversation_read_confirmed, Toast.LENGTH_SHORT).show()
        readWasConfirmed = state.readConfirmed
        if (state.error == null && !state.uncertain) shownError = null
        else if (state.uncertain || shownError !== state.error) {
            shownError = state.error
            val message = if (state.uncertain) getString(R.string.conversation_uncertain)
                else state.error?.userMessage(getString(R.string.conversation_error)) ?: getString(R.string.conversation_error)
            Snackbar.make(findViewById(R.id.rvConversationMessages), message, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.conversation_refresh) { model.load(true) }.show()
        }
    }
    companion object { const val EXTRA_CONVERSATION_ID = "extra_conversation_id" }
}
