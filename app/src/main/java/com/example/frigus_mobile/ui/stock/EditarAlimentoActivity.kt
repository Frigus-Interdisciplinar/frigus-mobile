package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.example.frigus_mobile.ui.login.LoginActivity
import java.time.LocalDate
import java.time.format.DateTimeParseException

class EditarAlimentoActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: StockLotViewModel
    private lateinit var inputs: Map<String, EditText>
    private lateinit var category: Spinner
    private lateinit var place: Spinner
    private var leaving = false
    private var rendering = false
    private var lotId = 0
    private val inputIds = mapOf(
        "name" to R.id.etNomeAlimento, "quantity" to R.id.etQuantidadeTotal,
        "initialQuantity" to R.id.lotInitialQuantity, "minimalQuantity" to R.id.lotMinimum,
        "expireDate" to R.id.etValidade, "batch" to R.id.lotBatch
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_editar_alimento)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lotEditRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.lotEditRoot))
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        lotId = intent.getIntExtra(AlimentoDetalhesActivity.EXTRA_LOT_ID, 0)
        model = ViewModelProvider(this)[StockLotViewModel::class.java]
        inputs = inputIds.mapValues { findViewById(it.value) }
        inputs.forEach { (key, input) ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!rendering) model.edit(key, s.toString())
                }
            })
        }
        category = findViewById(R.id.spinnerCategoria)
        place = findViewById(R.id.spinnerLocal)
        setupSpinner(category, R.array.inventory_category_labels, R.array.inventory_category_codes, "category")
        setupSpinner(place, R.array.inventory_place_labels, R.array.inventory_place_codes, "storagePlace")
        findViewById<View>(R.id.lotBack).setOnClickListener { finish() }
        findViewById<View>(R.id.lotReload).setOnClickListener { model.load(lotId, reconcile = true) }
        findViewById<View>(R.id.btnSalvarAlteracoes).setOnClickListener { save() }
        model.state.observe(this) { state ->
            if (model.changed) setResult(RESULT_OK)
            if (state.saved) { setResult(RESULT_OK); finish(); return@observe }
            if (state.error == null) inputs.values.forEach { it.error = null }
            rendering = true
            state.lot?.let { lot ->
                val values = mapOf("name" to lot.product?.name.orEmpty(), "quantity" to lot.quantity?.toString().orEmpty(),
                    "initialQuantity" to lot.initialQuantity?.toString().orEmpty(), "minimalQuantity" to lot.minimalQuantity?.toString().orEmpty(),
                    "expireDate" to lot.expireDate.orEmpty(), "batch" to lot.batch.orEmpty())
                inputs.forEach { (key, input) ->
                    val value = model.draft(key, values.getValue(key))
                    if (input.text.toString() != value) input.setText(value)
                }
                select(category, R.array.inventory_category_codes, model.draft("category", lot.category ?: lot.product?.category.orEmpty()))
                select(place, R.array.inventory_place_codes, model.draft("storagePlace", lot.product?.storagePlace.orEmpty()))
                findViewById<TextView>(R.id.lotUnit).text = getString(R.string.lot_unit_read_only,
                    lotUnitLabel(lot.product?.unitOfMeasure))
            }
            rendering = false
            findViewById<View>(R.id.lotProgress).visibility = if (state.loading || state.busy) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lotForm).visibility = if (state.lot != null) View.VISIBLE else View.GONE
            val enabled = state.lot != null && !state.busy && !state.loading && !state.uncertain
            inputs.values.forEach { it.isEnabled = enabled }
            category.isEnabled = enabled
            place.isEnabled = enabled
            findViewById<View>(R.id.btnSalvarAlteracoes).isEnabled = enabled
            findViewById<View>(R.id.lotReload).isEnabled = !state.loading && !state.busy
            findViewById<TextView>(R.id.lotStatus).text = when {
                state.uncertain -> getString(R.string.lot_uncertain)
                state.busy -> getString(R.string.lot_sending)
                state.loading -> getString(R.string.inventory_loading)
                state.error != null -> state.error.userMessage(getString(when(state.error.status) {
                    403 -> R.string.lot_forbidden
                    404 -> R.string.lot_not_found
                    else -> R.string.lot_error
                }))
                else -> ""
            }
            state.error?.fields.orEmpty().forEach { field ->
                field.displayMessage?.let { message ->
                    inputs[field.fieldName]?.error = message
                }
            }
            if (!state.loading && !state.busy && !state.uncertain && state.error != null) {
                val messages = state.error.fields.orEmpty().mapNotNull { it.displayMessage }
                if (messages.isNotEmpty()) findViewById<TextView>(R.id.lotStatus).append("\n" + messages.joinToString("\n"))
            }
        }
        model.loadIfNeeded(lotId)
    }

    private fun setupSpinner(spinner: Spinner, labelsId: Int, codesId: Int, key: String) {
        val labels = resources.getStringArray(labelsId).toMutableList()
        labels[0] = getString(R.string.lot_keep_value)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!rendering && spinner.isEnabled) model.edit(key, resources.getStringArray(codesId)[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }
    private fun select(spinner: Spinner, codesId: Int, code: String) {
        spinner.setSelection(resources.getStringArray(codesId).indexOf(code).coerceAtLeast(0))
    }

    private fun save() {
        val original = model.state.value?.lot ?: return
        inputs.values.forEach { it.error = null }
        var invalid = false
        fun error(key: String) { inputs.getValue(key).error = getString(R.string.lot_invalid_field); invalid = true }
        fun number(key: String, previous: Int?): Int? {
            val raw = inputs.getValue(key).text.toString()
            if (raw.isBlank() && previous == null) return null
            val value = raw.toIntOrNull()
            if (value == null || value < 0) { error(key); return null }
            return value.takeIf { it != previous }
        }
        val rawName = inputs.getValue("name").text.toString().trim()
        val oldName = original.product?.name
        val name = rawName.takeIf { it != oldName && !(it.isEmpty() && oldName == null) }
        if (name != null && (name.isBlank() || name.length > 255)) error("name")
        val quantity = number("quantity", original.quantity)
        val initial = number("initialQuantity", original.initialQuantity)
        val minimum = number("minimalQuantity", original.minimalQuantity)
        val rawDate = inputs.getValue("expireDate").text.toString().trim()
        val date = rawDate.takeIf { it != original.expireDate && !(it.isEmpty() && original.expireDate == null) }
        if (date != null) {
            try {
                if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) error("expireDate")
                LocalDate.parse(date)
            } catch (_: DateTimeParseException) { error("expireDate") }
        }
        val rawBatch = inputs.getValue("batch").text.toString()
        val batch = rawBatch.takeIf { it != original.batch && !(it.isEmpty() && original.batch == null) }
        if (batch != null && batch.length > 120) error("batch")
        fun choice(spinner: Spinner, codesId: Int, originalValue: String?): String? {
            val code = resources.getStringArray(codesId)[spinner.selectedItemPosition]
            return code.takeIf { it.isNotEmpty() && it != originalValue }
        }
        val request = StockProductUpdateRequest(name = name, quantity = quantity, initialQuantity = initial,
            minimalQuantity = minimum, expireDate = date, batch = batch,
            category = choice(category, R.array.inventory_category_codes, original.category ?: original.product?.category),
            storagePlace = choice(place, R.array.inventory_place_codes, original.product?.storagePlace))
        if (invalid) return
        if (listOf(request.name, request.quantity, request.initialQuantity, request.minimalQuantity,
                request.expireDate, request.batch, request.category, request.storagePlace).all { it == null }) {
            findViewById<TextView>(R.id.lotStatus).setText(R.string.lot_no_changes)
            return
        }
        model.save(lotId, request)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }
    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
