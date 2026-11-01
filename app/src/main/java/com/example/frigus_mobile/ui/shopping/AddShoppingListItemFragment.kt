package com.example.frigus_mobile.ui.shopping

import android.app.DatePickerDialog
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AlertDialog
import com.example.frigus_mobile.data.model.ShoppingDraft
import com.example.frigus_mobile.data.model.validShoppingPrice
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import com.example.frigus_mobile.R
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class AddShoppingListItemFragment : Fragment() {

    private lateinit var rgDestino: RadioGroup
    private lateinit var rbEstoque: RadioButton
    private lateinit var rbListaCompras: RadioButton

    private lateinit var layoutLugarEstoque: LinearLayout
    private lateinit var spinnerLugarEstoque: Spinner

    private lateinit var etNome: AutoCompleteTextView
    private lateinit var etQuantidade: EditText
    private lateinit var spinnerUnidade: Spinner

    private lateinit var layoutValidade: LinearLayout
    private lateinit var etValidade: EditText
    private var ivValidadeCalendario: ImageView? = null

    private lateinit var spinnerCategoria: Spinner
    private lateinit var etObservacoes: EditText
    private lateinit var btnSalvar: MaterialButton
    private lateinit var progressSalvar: View
    private lateinit var imgProduto: ImageView

    private lateinit var model: ShoppingListViewModel
    private lateinit var layoutPreco: View
    private lateinit var etPreco: EditText
    private var shownError: com.example.frigus_mobile.data.model.ApiError? = null
    private var stockDialog: AlertDialog? = null

    private var currentDestination = DESTINATION_SHOPPING_LIST
    private var customImageUri: Uri? = null

    private val lugaresEstoque get() = resources.getStringArray(R.array.shopping_remote_place_labels).toList()
    private val unidades get() = resources.getStringArray(R.array.shopping_remote_unit_labels).toList()
    private val categoriasGeladeira get() = resources.getStringArray(R.array.shopping_form_categories).take(4)
    private val categoriasFreezer get() = categoriasGeladeira
    private val categoriasDespensa get() = resources.getStringArray(R.array.shopping_form_categories).toList()
    private val categoriasListaCompras get() = categoriasDespensa

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            customImageUri = uri
            imgProduto.setPadding(0, 0, 0, 0)
            imgProduto.clearColorFilter()
            imgProduto.setImageURI(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_shopping_list_item, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model = ViewModelProvider(requireActivity())[ShoppingListViewModel::class.java]

        rgDestino = view.findViewById(R.id.rgDestinoAdicionarLista)
        rbEstoque = view.findViewById(R.id.rbDestinoEstoqueLista)
        rbListaCompras = view.findViewById(R.id.rbDestinoListaCompras)

        layoutLugarEstoque = view.findViewById(R.id.layoutLugarEstoque)
        spinnerLugarEstoque = view.findViewById(R.id.spinnerLugarEstoque)

        etNome = view.findViewById(R.id.etNomeItemLista)
        etQuantidade = view.findViewById(R.id.etQuantidadeLista)
        spinnerUnidade = view.findViewById(R.id.spinnerUnidadeLista)

        layoutValidade = view.findViewById(R.id.layoutValidade)
        etValidade = view.findViewById(R.id.etValidadeLista)
        ivValidadeCalendario = view.findViewById(R.id.ivValidadeCalendario)

        layoutPreco = view.findViewById(R.id.layoutPrecoLista)
        etPreco = view.findViewById(R.id.etPrecoLista)
        spinnerCategoria = view.findViewById(R.id.spinnerCategoriaLista)
        etObservacoes = view.findViewById(R.id.etObservacoesLista)
        btnSalvar = view.findViewById(R.id.btnSalvarItemLista)
        progressSalvar = view.findViewById(R.id.progressSalvarItemLista)
        imgProduto = view.findViewById(R.id.imgProdutoAdicionarLista)

        view.findViewById<View>(R.id.btnVoltarAdicionarItemLista).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnCameraAdicionarItemLista).setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        configurarDestinoToggle()
        configurarLugarEstoqueSpinner()
        configurarUnidadeSpinner()
        configurarValidadePicker()
        configurarNomeInput()


        val initialName = arguments?.getString(ARG_INITIAL_NAME)
        if (!initialName.isNullOrBlank()) {
            etNome.setText(initialName)
            etNome.setSelection(initialName.length)
        }

        val initialDestination = arguments?.getString(ARG_INITIAL_DESTINATION) ?: DESTINATION_SHOPPING_LIST
        selecionarDestino(initialDestination)

        btnSalvar.setOnClickListener { salvarItem() }
        etObservacoes.setHint(R.string.shopping_remote_note_hint)
        model.state.observe(viewLifecycleOwner) { state ->
            val remote = currentDestination == DESTINATION_STOCK
            setLoading(remote && state.transferring)
            btnSalvar.isEnabled = !remote || (!state.transferring && !state.uncertain)
            val error = state.error
            if (error == null) shownError = null
            else if (shownError !== error) {
                shownError = error
                if (error.code == "SHOPPING_CHOOSE_STOCK" && state.stock == null && state.stocks.size > 1) {
                    stockDialog = showShoppingStockSelection(requireContext(), state) {
                        model.selectStock(it)
                        Toast.makeText(requireContext(), R.string.shopping_stock_selected, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Snackbar.make(view, shoppingErrorMessage(requireContext(), state), Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.shopping_remote_refresh) { model.reload(explicit = true) }.show()
                }
            }
            state.completion?.takeIf { it.kind == ShoppingCompletionKind.ADDED ||
                it.kind == ShoppingCompletionKind.TRANSFERRED }?.let { completion ->
                model.consumeCompletion(completion.id)
                Toast.makeText(requireContext(), if (completion.kind == ShoppingCompletionKind.ADDED)
                    R.string.shopping_local_added else R.string.shopping_form_stock_added, Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
        model.loadIfNeeded()
    }

    private fun configurarDestinoToggle() {
        rgDestino.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbDestinoEstoqueLista && currentDestination != DESTINATION_STOCK) {
                selecionarDestino(DESTINATION_STOCK)
            } else if (checkedId == R.id.rbDestinoListaCompras && currentDestination != DESTINATION_SHOPPING_LIST) {
                selecionarDestino(DESTINATION_SHOPPING_LIST)
            }
        }
        rbEstoque.setOnClickListener {
            selecionarDestino(DESTINATION_STOCK)
        }
        rbListaCompras.setOnClickListener {
            selecionarDestino(DESTINATION_SHOPPING_LIST)
        }
    }

    private fun selecionarDestino(destino: String) {
        currentDestination = destino
        val isEstoque = destino == DESTINATION_STOCK
        btnSalvar.setText(R.string.lista_salvar)

        if (isEstoque) {
            if (rgDestino.checkedRadioButtonId != R.id.rbDestinoEstoqueLista) {
                rgDestino.check(R.id.rbDestinoEstoqueLista)
            }
            rbEstoque.isChecked = true
            rbListaCompras.isChecked = false
            rbEstoque.setBackgroundResource(R.drawable.bg_shopping_destination_selected)
            rbEstoque.setTextColor(ContextCompat.getColor(requireContext(), R.color.pure_white))
            rbEstoque.setTypeface(null, Typeface.BOLD)

            rbListaCompras.setBackgroundResource(R.drawable.bg_shopping_destination_unselected)
            rbListaCompras.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary))
            rbListaCompras.setTypeface(null, Typeface.NORMAL)

            layoutLugarEstoque.visibility = View.VISIBLE
            layoutValidade.visibility = View.VISIBLE
            layoutPreco.visibility = View.VISIBLE
            atualizarCategoriasPorLocal(spinnerLugarEstoque.selectedItem?.toString().orEmpty())
        } else {
            if (rgDestino.checkedRadioButtonId != R.id.rbDestinoListaCompras) {
                rgDestino.check(R.id.rbDestinoListaCompras)
            }
            rbListaCompras.isChecked = true
            rbEstoque.isChecked = false
            rbListaCompras.setBackgroundResource(R.drawable.bg_shopping_destination_selected)
            rbListaCompras.setTextColor(ContextCompat.getColor(requireContext(), R.color.pure_white))
            rbListaCompras.setTypeface(null, Typeface.BOLD)

            rbEstoque.setBackgroundResource(R.drawable.bg_shopping_destination_unselected)
            rbEstoque.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary))
            rbEstoque.setTypeface(null, Typeface.NORMAL)

            layoutLugarEstoque.visibility = View.GONE
            layoutValidade.visibility = View.GONE
            layoutPreco.visibility = View.GONE
            etPreco.error = null
            etValidade.error = null
            ivValidadeCalendario?.visibility = View.VISIBLE
            carregarCategorias(categoriasListaCompras)
        }
        val state = model.state.value
        setLoading(isEstoque && state?.transferring == true)
        btnSalvar.isEnabled = !isEstoque || (state?.transferring != true && state?.uncertain != true)
    }

    private fun configurarLugarEstoqueSpinner() {
        val adapterLugar = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, lugaresEstoque).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerLugarEstoque.adapter = adapterLugar

        spinnerLugarEstoque.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (currentDestination == DESTINATION_STOCK) {
                    atualizarCategoriasPorLocal(lugaresEstoque[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun atualizarCategoriasPorLocal(local: String) {
        val cats = when (lugaresEstoque.indexOf(local)) {
            1 -> categoriasFreezer
            2 -> categoriasDespensa
            else -> categoriasGeladeira
        }
        carregarCategorias(cats)
    }

    private fun carregarCategorias(categorias: List<String>) {
        val currentCategory = spinnerCategoria.selectedItem?.toString()
        val adapterCat = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, categorias).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerCategoria.adapter = adapterCat

        val newIndex = if (currentCategory != null && categorias.contains(currentCategory)) {
            categorias.indexOf(currentCategory)
        } else {
            0
        }
        spinnerCategoria.setSelection(newIndex)

        spinnerCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                atualizarImagemProduto()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        atualizarImagemProduto()
    }

    private fun configurarUnidadeSpinner() {
        val adapterUni = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, unidades).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerUnidade.adapter = adapterUni
    }

    private fun configurarValidadePicker() {
        val abrirPickerAction = View.OnClickListener {
            abrirDatePicker()
        }
        etValidade.setOnClickListener(abrirPickerAction)
        view?.findViewById<View>(R.id.layoutValidadeInput)?.setOnClickListener(abrirPickerAction)
        ivValidadeCalendario?.setOnClickListener(abrirPickerAction)

        etValidade.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank()) {
                    etValidade.error = null
                    ivValidadeCalendario?.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun abrirDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(requireContext(), { _, y, m, d ->
            val formatted = String.format("%02d/%02d/%04d", d, m + 1, y)
            etValidade.setText(formatted)
            etValidade.error = null
            ivValidadeCalendario?.visibility = View.VISIBLE
        }, year, month, day)

        // Impede selecionar datas no passado
        dpd.datePicker.minDate = calendar.timeInMillis
        dpd.show()
    }

    private fun configurarNomeInput() {
        etNome.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                atualizarImagemProduto()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun atualizarImagemProduto() {
        if (customImageUri != null) return

        val categoria = spinnerCategoria.selectedItem?.toString()

        imgProduto.setPadding(28, 28, 28, 28)
        imgProduto.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_primary))
        imgProduto.setImageResource(ProductCategoryHelper.getIconeCategoria(categoria))
    }

    private fun setLoading(loading: Boolean) {
        btnSalvar.isEnabled = !loading
        if (loading) {
            btnSalvar.text = ""
            progressSalvar.visibility = View.VISIBLE
        } else {
            btnSalvar.text = getString(R.string.lista_salvar)
            progressSalvar.visibility = View.GONE
        }
    }

    private fun salvarItem() {
        val name = etNome.text.toString().trim()
        if (name.isBlank()) {
            etNome.error = getString(R.string.shopping_form_name_required)
            etNome.requestFocus()
            return
        }
        val quantity = etQuantidade.text.toString().toIntOrNull()
        if (quantity == null || quantity <= 0) {
            etQuantidade.error = getString(R.string.shopping_quantity_positive)
            return
        }
        val toStock = currentDestination == DESTINATION_STOCK
        val expiry = if (toStock) runCatching {
            LocalDate.parse(etValidade.text.toString().trim(),
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT))
        }.getOrNull() else null
        if (toStock && (expiry == null || expiry.isBefore(LocalDate.now()))) {
            etValidade.error = getString(R.string.lista_validade_obrigatoria_erro)
            return
        }
        val unitCodes = resources.getStringArray(R.array.shopping_remote_unit_codes)
        val categoryCodes = resources.getStringArray(R.array.shopping_form_category_codes)
        val places = resources.getStringArray(R.array.shopping_remote_place_codes)
        val price = if (toStock) etPreco.text.toString().trim().replace(',', '.').toBigDecimalOrNull() else null
        if (toStock && !validShoppingPrice(price)) {
            etPreco.error = getString(R.string.shopping_remote_price_invalid)
            etPreco.requestFocus()
            return
        }
        model.submitDraft(ShoppingDraft(
            name = name, quantity = quantity,
            unitCode = unitCodes[spinnerUnidade.selectedItemPosition],
            categoryCode = categoryCodes[spinnerCategoria.selectedItemPosition],
            storagePlace = places[spinnerLugarEstoque.selectedItemPosition],
            expireDate = expiry?.toString(),
            observation = etObservacoes.text.toString().trim(),
            imageUri = customImageUri?.toString(), unitPrice = price
        ), toStock)
    }

    override fun onDestroyView() {
        stockDialog?.dismiss()
        stockDialog = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_INITIAL_NAME = "INITIAL_NAME"
        const val ARG_INITIAL_DESTINATION = "INITIAL_DESTINATION"

        const val DESTINATION_STOCK = "STOCK"
        const val DESTINATION_SHOPPING_LIST = "SHOPPING_LIST"

        fun newInstance(
            initialName: String? = null,
            initialDestination: String = DESTINATION_SHOPPING_LIST
        ): AddShoppingListItemFragment {
            return AddShoppingListItemFragment().apply {
                arguments = Bundle().apply {
                    if (!initialName.isNullOrBlank()) {
                        putString(ARG_INITIAL_NAME, initialName)
                    }
                    putString(ARG_INITIAL_DESTINATION, initialDestination)
                }
            }
        }
    }
}
