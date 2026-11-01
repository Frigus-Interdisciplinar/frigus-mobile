package com.example.frigus_mobile.ui.conversation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.google.android.material.snackbar.Snackbar

class ConversationsActivity : HumanConversationActivity() {
    private lateinit var model: ConversationsViewModel
    private lateinit var adapter: ConversationsAdapter
    private var shownError: com.example.frigus_mobile.data.model.ApiError? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        setContentView(R.layout.fragment_conversations)
        model = ViewModelProvider(this)[ConversationsViewModel::class.java]
        if (!model.hasCurrentSession()) { openLogin(); return }
        findViewById<View>(R.id.btnConversationsBack).setOnClickListener { finish() }
        adapter = ConversationsAdapter(onItemClick = { item ->
            startActivity(Intent(this, ConversationActivity::class.java).putExtra(ConversationActivity.EXTRA_CONVERSATION_ID, item.id))
        }, onItemLongClick = { item ->
            if (item.isGroup) Snackbar.make(findViewById(R.id.rvConversations), R.string.conversation_rename_unavailable, Snackbar.LENGTH_LONG).show()
        })
        findViewById<RecyclerView>(R.id.rvConversations).adapter = adapter
        findViewById<EditText>(R.id.etSearchConversations).apply {
            setText(model.query)
            doAfterTextChanged { model.setQuery(it?.toString().orEmpty()); render(model.state.value!!) }
        }
        findViewById<TextView>(R.id.tvConversationsFooterHelper).apply {
            setText(R.string.conversation_refresh)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { model.load(true) }
        }
        findViewById<View>(R.id.tvConversationsError).setOnClickListener { model.load(true) }
        model.state.observe(this, ::render)
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized && !isFinishing) model.load(true)
    }
    private fun render(state: ConversationsState) {
        if (!currentSession() || !model.hasCurrentSession()) return
        val filtered = state.items.filter { it.title.contains(model.query.trim(), true) || it.lastMessage.contains(model.query.trim(), true) }
        adapter.submitList(filtered)
        findViewById<View>(R.id.progressConversations).visibility = if (state.loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvConversationsFooterHelper).isEnabled = !state.loading
        findViewById<TextView>(R.id.tvConversationsEmpty).apply {
            visibility = if (!state.loading && state.error == null && filtered.isEmpty()) View.VISIBLE else View.GONE
            setText(if (model.query.isBlank()) R.string.conversations_empty else R.string.conversation_search_empty)
        }
        findViewById<TextView>(R.id.tvConversationsError).apply {
            visibility = if (state.error != null && state.items.isEmpty()) View.VISIBLE else View.GONE
            text = state.error?.userMessage(getString(R.string.conversations_error))
        }
        if (state.error == null) shownError = null
        else if (shownError !== state.error) {
            shownError = state.error
            Snackbar.make(findViewById(R.id.rvConversations), state.error.userMessage(getString(R.string.conversations_error)), Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.conversation_refresh) { model.load(true) }.show()
        }
    }
}
