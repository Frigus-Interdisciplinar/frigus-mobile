package com.example.frigus_mobile.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.ChatUiMessage
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.recipes.ReceitaDetalhesActivity

/** Original chat presentation from 93933ee, backed by confirmed M19/M18 responses. */
class ChatIaActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: AiChatViewModel
    private lateinit var input: EditText
    private lateinit var scroll: NestedScrollView
    private lateinit var adapter: ChatIaAdapter
    private var leaving = false
    private var shownErrorVersion = 0
    private var shownSavedVersion = 0
    private var displayedMessages: List<ChatUiMessage>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        setContentView(R.layout.activity_chat_ia)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        model = ViewModelProvider(this)[AiChatViewModel::class.java]
        input = findViewById(R.id.etChatInput)
        scroll = findViewById(R.id.scrollChat)
        adapter = ChatIaAdapter(this)
        findViewById<RecyclerView>(R.id.rvChatMessages).apply {
            layoutManager = LinearLayoutManager(this@ChatIaActivity)
            adapter = this@ChatIaActivity.adapter
        }
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        root.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard))
            if (keyboard > 0) scrollDown()
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        shownErrorVersion = minOf(savedInstanceState?.getInt(SHOWN_ERROR) ?: 0, model.state.value?.errorVersion ?: 0)
        shownSavedVersion = minOf(savedInstanceState?.getInt(SHOWN_SAVED) ?: 0, model.state.value?.savedVersion ?: 0)
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEnviarMensagem).setOnClickListener { model.send(input.text.toString()) }
        input.doAfterTextChanged { text ->
            if (text?.toString().orEmpty() != model.state.value?.draft) model.setDraft(text?.toString().orEmpty())
        }
        input.setOnEditorActionListener { _, action, _ ->
            action == EditorInfo.IME_ACTION_SEND && model.send(input.text.toString())
        }
        mapOf(
            R.id.sugestaoCozinhar to R.string.ai_chat_prompt_cook,
            R.id.sugestaoVencimento to R.string.ai_chat_prompt_expiration,
            R.id.sugestaoListaCompras to R.string.ai_chat_prompt_shopping,
            R.id.sugestaoEconomia to R.string.ai_chat_prompt_saving
        ).forEach { (id, prompt) -> findViewById<View>(id).setOnClickListener { model.send(getString(prompt), false) } }
        findViewById<View>(R.id.btnSavedAiSuggestions).setOnClickListener { model.loadSavedSuggestions() }
        findViewById<View>(R.id.chatContextStatus).setOnClickListener {
            model.state.value?.let { state ->
                if (state.uncertain) model.loadSavedSuggestions()
                else if (state.error != null && !state.contextReady) model.reloadContext()
            }
        }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        model.state.observe(this) { state -> render(state) }
        model.loadIfNeeded()
    }

    private fun render(state: AiChatState) {
        val busy = state.sending || state.loadingContext || state.checkingSaved
        val canSend = state.contextReady && !busy && !state.uncertain
        findViewById<View>(R.id.btnEnviarMensagem).isEnabled = canSend && state.draft.isNotBlank()
        listOf(R.id.sugestaoCozinhar, R.id.sugestaoVencimento, R.id.sugestaoListaCompras, R.id.sugestaoEconomia)
            .forEach { findViewById<View>(it).isEnabled = canSend }
        input.isEnabled = !state.sending && !state.uncertain
        if (input.text.toString() != state.draft) {
            input.setText(state.draft)
            input.setSelection(input.length())
        }
        findViewById<View>(R.id.btnSavedAiSuggestions).isEnabled = state.contextReady && !busy
        findViewById<TextView>(R.id.chatContextSubtitle).text = if (state.contextReady)
            getString(R.string.ai_chat_active_stock, state.stockName?.takeIf(String::isNotBlank) ?: getString(R.string.ai_chat_context_name))
            else getString(R.string.ai_chat_subtitle)
        findViewById<TextView>(R.id.chatContextStatus).apply {
            text = when {
                state.loadingContext -> getString(R.string.ai_chat_loading_context)
                state.sending -> getString(R.string.ai_chat_thinking)
                state.checkingSaved -> getString(R.string.ai_chat_loading_saved)
                state.uncertain -> getString(R.string.ai_chat_uncertain)
                state.error != null -> errorMessage(state.error)
                else -> ""
            }
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            isEnabled = !busy
        }
        if (displayedMessages !== state.messages) {
            displayedMessages = state.messages
            adapter.submit(state.messages)
            scrollDown()
        }
        if (state.error != null && state.errorVersion > shownErrorVersion && !isFinishing) {
            shownErrorVersion = state.errorVersion
            showError(state)
        }
        if (state.savedVersion > shownSavedVersion && !isFinishing) {
            shownSavedVersion = state.savedVersion
            showSaved(state)
        }
    }

    private fun showError(state: AiChatState) {
        val dialog = AlertDialog.Builder(this)
        if (state.uncertain) {
            dialog.setTitle(R.string.ai_chat_uncertain_title).setMessage(R.string.ai_chat_uncertain)
                .setPositiveButton(R.string.ai_chat_check_saved) { _, _ -> model.loadSavedSuggestions() }
                .setNegativeButton(R.string.ai_chat_close, null)
        } else {
            dialog.setMessage(state.error?.let(::errorMessage).orEmpty())
            if (!state.contextReady) {
                dialog.setPositiveButton(R.string.ai_retry) { _, _ -> model.reloadContext() }
                    .setNegativeButton(R.string.ai_chat_return_to_app) { _, _ -> finish() }
            } else dialog.setPositiveButton(R.string.ai_chat_close, null)
        }
        dialog.show()
    }

    private fun showSaved(state: AiChatState) {
        val names = state.saved.map { it.recipeName?.takeIf(String::isNotBlank) ?: getString(R.string.recipe_browser_unnamed) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.ai_chat_saved_title)
        if (state.uncertain) {
            val recipes = if (names.isEmpty()) getString(R.string.ai_empty_suggestions) else names.joinToString("\n")
            dialog.setMessage(getString(R.string.ai_chat_history_limit) + "\n\n" + recipes)
                .setPositiveButton(R.string.ai_chat_new_conversation) { _, _ ->
                    AlertDialog.Builder(this).setMessage(R.string.ai_chat_confirm_new)
                        .setPositiveButton(R.string.ai_chat_new_conversation) { _, _ -> model.startNewConversationAfterReview() }
                        .setNegativeButton(R.string.ai_chat_close, null).show()
                }.setNegativeButton(R.string.ai_chat_close, null)
        } else {
            if (names.isEmpty()) dialog.setMessage(R.string.ai_empty_suggestions)
            else dialog.setItems(names.toTypedArray()) { _, index ->
                state.saved[index].recipeId?.takeIf { it > 0 }?.let { id ->
                    startActivity(Intent(this, ReceitaDetalhesActivity::class.java)
                        .putExtra(ReceitaDetalhesActivity.EXTRA_RECIPE_ID, id))
                }
            }
            dialog.setPositiveButton(R.string.ai_chat_close, null)
        }
        dialog.show()
    }

    private fun errorMessage(error: ApiError): String = error.userMessage(getString(when {
        error.code == "AI_NO_STOCK" -> R.string.ai_chat_no_stock
        error.code == "AI_STOCK_AMBIGUOUS" -> R.string.ai_chat_ambiguous_stock
        error.code == "AI_STOCK_CHANGED" -> R.string.ai_chat_changed_stock
        error.code == "AI_INPUT_TOO_LONG" -> R.string.ai_chat_input_too_long
        error.code == "AI_STATE_STORAGE_ERROR" -> R.string.ai_chat_storage_error
        error.status == 403 -> R.string.ai_chat_forbidden
        error.code == "INVALID_RESPONSE" -> R.string.ai_invalid_response
        error.code == "NETWORK_ERROR" -> R.string.ai_network_error
        else -> R.string.ai_chat_send_error
    }))

    private fun scrollDown() {
        scroll.post { if (!isFinishing && scroll.childCount > 0) scroll.smoothScrollTo(0, scroll.getChildAt(0).height) }
    }
    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
        else if (::model.isInitialized) model.loadIfNeeded()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SHOWN_ERROR, shownErrorVersion)
        outState.putInt(SHOWN_SAVED, shownSavedVersion)
        super.onSaveInstanceState(outState)
    }
    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    companion object {
        private const val SHOWN_ERROR = "shown_chat_error"
        private const val SHOWN_SAVED = "shown_chat_suggestions"
    }
}
