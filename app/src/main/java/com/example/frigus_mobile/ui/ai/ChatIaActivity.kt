package com.example.frigus_mobile.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.recipes.ReceitaDetalhesActivity

/** Displays persisted M18 suggestions for a stock chosen by the user. M19 chat is unavailable. */
class ChatIaActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: AiSuggestionsViewModel
    private lateinit var spinner: Spinner
    private lateinit var recipes: AiSuggestionsAdapter
    private var displayedStockIds = emptyList<Int>()
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_chat_ia)
        model = ViewModelProvider(this)[AiSuggestionsViewModel::class.java]
        spinner = findViewById(R.id.aiStockSelector)
        recipes = AiSuggestionsAdapter { id ->
            if (session.isLoggedIn) startActivity(Intent(this, ReceitaDetalhesActivity::class.java)
                .putExtra(ReceitaDetalhesActivity.EXTRA_RECIPE_ID, id))
        }
        findViewById<RecyclerView>(R.id.aiSuggestionsList).apply {
            layoutManager = LinearLayoutManager(this@ChatIaActivity)
            adapter = recipes
        }
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.aiStockRetry).setOnClickListener { model.loadStocks() }
        findViewById<View>(R.id.aiRefresh).setOnClickListener { model.refreshSuggestions() }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != spinner.selectedItemPosition) return
                model.selectStock(displayedStockIds.getOrNull(position - 1))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        model.state.observe(this) { render(it) }
        model.loadIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }

    private fun render(state: AiSuggestionsState) {
        val ids = state.stocks.map { it.id }
        if (ids != displayedStockIds) {
            displayedStockIds = ids
            val names = listOf(getString(R.string.ai_select_stock)) + state.stocks.map {
                it.name?.takeIf(String::isNotBlank) ?: getString(R.string.ai_unnamed_stock, it.id)
            }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        val index = state.selectedStockId?.let(ids::indexOf)?.takeIf { it >= 0 }?.plus(1) ?: 0
        if (spinner.selectedItemPosition != index) spinner.setSelection(index)
        spinner.isEnabled = !state.loadingStocks && state.stockError == null && ids.isNotEmpty()

        findViewById<View>(R.id.aiStockProgress).visibility = if (state.loadingStocks) View.VISIBLE else View.GONE
        findViewById<View>(R.id.aiStockRetry).visibility = if (state.stockError != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.aiStockStatus).apply {
            visibility = if (state.loadingStocks || state.stockError != null || ids.isEmpty()) View.VISIBLE else View.GONE
            text = when {
                state.loadingStocks -> getString(R.string.ai_loading_stocks)
                state.stockError != null -> message(state.stockError, R.string.ai_stock_error)
                else -> getString(R.string.ai_no_stocks)
            }
        }

        findViewById<View>(R.id.aiSuggestionsProgress).visibility =
            if (state.loadingSuggestions) View.VISIBLE else View.GONE
        findViewById<View>(R.id.aiRefresh).isEnabled =
            state.selectedStockId != null && !state.loadingStocks && !state.loadingSuggestions
        findViewById<TextView>(R.id.aiSuggestionsStatus).apply {
            visibility = if (state.selectedStockId == null || state.loadingSuggestions ||
                state.suggestionsError != null || state.suggestions?.isEmpty() == true) View.VISIBLE else View.GONE
            text = when {
                state.selectedStockId == null -> getString(R.string.ai_choose_stock)
                state.loadingSuggestions -> getString(R.string.ai_loading_suggestions)
                state.suggestionsError != null -> message(state.suggestionsError, R.string.ai_suggestions_error)
                else -> getString(R.string.ai_empty_suggestions)
            }
        }
        recipes.submit(state.suggestions.orEmpty())
    }

    private fun message(error: ApiError, fallback: Int): String = error.userMessage(getString(when {
        error.status == 403 -> R.string.ai_forbidden
        error.code == "NETWORK_ERROR" -> R.string.ai_network_error
        error.code == "INVALID_RESPONSE" -> R.string.ai_invalid_response
        else -> fallback
    }))

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
