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
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.ShoppingListRepository
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
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

    private lateinit var shoppingRepo: ShoppingListRepository
    private lateinit var stockRepo: StockRepository

    private var allCatalogProducts: List<StockProductProductResponse> = emptyList()

    private var currentDestination = DESTINATION_SHOPPING_LIST
    private var customImageUri: Uri? = null

    private val lugaresEstoque = listOf("Geladeira", "Freezer", "Despensa")
    private val unidades = listOf("Gramas", "Quilos", "Litros", "Mililitros", "Unidades")

    // Categorias correspondentes a cada local do estoque
    private val categoriasGeladeira = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasFreezer = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasDespensa = listOf("Grãos", "Hortifruti", "Laticínios", "Bebidas")
    private val categoriasListaCompras = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas", "Grãos")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
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

        shoppingRepo = ShoppingListRepository(requireContext())
        stockRepo = StockRepository(requireContext())

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

        spinnerCategoria = view.findViewById(R.id.spinnerCategoriaLista)
        etObservacoes = view.findViewById(R.id.etObservacoesLista)
        btnSalvar = view.findViewById(R.id.btnSalvarItemLista)
        progressSalvar = view.findViewById(R.id.progressSalvarItemLista)
        imgProduto = view.findViewById(R.id.imgProdutoAdicionarLista)

        view.findViewById<View>(R.id.btnVoltarAdicionarItemLista).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnCameraAdicionarItemLista).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        configurarDestinoToggle()
        configurarLugarEstoqueSpinner()
        configurarUnidadeSpinner()
        configurarValidadePicker()
        configurarNomeInput()
        carregarSugestoesCatalogo()

        val initialName = arguments?.getString(ARG_INITIAL_NAME)
        if (!initialName.isNullOrBlank()) {
            etNome.setText(initialName)
            etNome.setSelection(initialName.length)
        }

        val initialDestination = arguments?.getString(ARG_INITIAL_DESTINATION) ?: DESTINATION_SHOPPING_LIST
        selecionarDestino(initialDestination)

        btnSalvar.setOnClickListener {
            salvarItem()
        }
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
            atualizarCategoriasPorLocal(spinnerLugarEstoque.selectedItem?.toString() ?: "Geladeira")
            atualizarSugestoesNome()
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
            etValidade.error = null
            ivValidadeCalendario?.visibility = View.VISIBLE
            carregarCategorias(categoriasListaCompras)
            atualizarSugestoesNome()
        }
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
        val cats = when (local.lowercase()) {
            "freezer" -> categoriasFreezer
            "despensa" -> categoriasDespensa
            else -> categoriasGeladeira
        }
        carregarCategorias(cats)
        atualizarSugestoesNome()
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

        etNome.setOnItemClickListener { parent, _, position, _ ->
            val selecionado = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            val prod = allCatalogProducts.firstOrNull { it.name.equals(selecionado, ignoreCase = true) }
            if (prod != null) {
                val catLabel = ShoppingListItem.mapCodeToCategory(prod.category ?: "")
                for (i in 0 until spinnerCategoria.count) {
                    if (spinnerCategoria.getItemAtPosition(i).toString().equals(catLabel, ignoreCase = true)) {
                        spinnerCategoria.setSelection(i)
                        break
                    }
                }
            }
        }
    }

    private fun carregarSugestoesCatalogo() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allCatalogProducts = stockRepo.getCatalogProducts()
                atualizarSugestoesNome()
            } catch (_: Exception) {}
        }
    }

    private fun atualizarSugestoesNome() {
        if (!isAdded || currentDestination != DESTINATION_STOCK || allCatalogProducts.isEmpty()) {
            etNome.setAdapter(null)
            return
        }

        val lugarEstoqueNome = spinnerLugarEstoque.selectedItem?.toString() ?: "Geladeira"
        val storagePlaceCode = when (lugarEstoqueNome.lowercase()) {
            "freezer" -> "FREEZER"
            "despensa" -> "PANTRY"
            else -> "FRIDGE"
        }

        val nomes = allCatalogProducts
            .filter { it.storagePlace.equals(storagePlaceCode, ignoreCase = true) }
            .mapNotNull { it.name }
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nomes)
        etNome.setAdapter(adapter)
        etNome.threshold = 1
    }

    private fun atualizarImagemProduto() {
        if (customImageUri != null) return

        val nome = etNome.text.toString().trim()
        val categoria = spinnerCategoria.selectedItem?.toString()

        if (nome.contains("frango", ignoreCase = true)) {
            imgProduto.setPadding(0, 0, 0, 0)
            imgProduto.clearColorFilter()
            imgProduto.setImageResource(R.drawable.img_frango)
        } else {
            imgProduto.setPadding(28, 28, 28, 28)
            imgProduto.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_primary))
            imgProduto.setImageResource(ProductCategoryHelper.getIconeCategoria(categoria))
        }
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
        val nome = etNome.text.toString().trim()
        if (nome.isBlank()) {
            etNome.error = "Informe o nome do item"
            etNome.requestFocus()
            return
        }

        val quantidade = etQuantidade.text.toString().toIntOrNull() ?: 1
        val unidadeSelecionada = spinnerUnidade.selectedItem?.toString() ?: "Unidades"
        val categoriaSelecionada = spinnerCategoria.selectedItem?.toString() ?: "Outros"
        val categoriaCodigo = ShoppingListItem.mapCategoryToCode(categoriaSelecionada)
        val observacoes = etObservacoes.text.toString().trim()

        if (currentDestination == DESTINATION_STOCK) {
            val dataValidadeTexto = etValidade.text.toString().trim()
            if (dataValidadeTexto.isBlank()) {
                etValidade.error = "A data de validade é obrigatória"
                ivValidadeCalendario?.visibility = View.INVISIBLE
                Toast.makeText(requireContext(), "Informe a data de validade do alimento.", Toast.LENGTH_SHORT).show()
                return
            }
            salvarItemEstoque(nome, quantidade, unidadeSelecionada, categoriaSelecionada, categoriaCodigo)
        } else {
            salvarItemListaCompras(nome, quantidade, unidadeSelecionada, categoriaSelecionada, categoriaCodigo, observacoes)
        }
    }

    private fun salvarItemListaCompras(
        nome: String,
        quantidade: Int,
        unidade: String,
        categoria: String,
        categoriaCodigo: String,
        observacoes: String
    ) {
        val item = ShoppingListItem(
            name = nome,
            quantity = quantidade,
            unit = unidade,
            category = categoria,
            categoryCode = categoriaCodigo,
            observation = observacoes
        )
        shoppingRepo.addItem(item)
        Toast.makeText(requireContext(), "Item adicionado à sua lista de compras!", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    private fun salvarItemEstoque(
        nome: String,
        quantidade: Int,
        unidade: String,
        categoria: String,
        categoriaCodigo: String
    ) {
        val dataValidadeTexto = etValidade.text.toString().trim()
        if (dataValidadeTexto.isBlank()) {
            etValidade.error = "A data de validade é obrigatória"
            ivValidadeCalendario?.visibility = View.INVISIBLE
            Toast.makeText(requireContext(), "Informe a data de validade do alimento.", Toast.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        if (!NetworkGuard.ensureConnected(requireActivity()) {
            salvarItemEstoque(nome, quantidade, unidade, categoria, categoriaCodigo)
        }) return

        setLoading(true)

        val lugarEstoqueNome = spinnerLugarEstoque.selectedItem?.toString() ?: "Geladeira"
        val storagePlaceCode = when (lugarEstoqueNome.lowercase()) {
            "freezer" -> "FREEZER"
            "despensa" -> "PANTRY"
            else -> "FRIDGE"
        }

        val isoExpireDate = if (dataValidadeTexto.contains("/")) {
            val parts = dataValidadeTexto.split("/")
            if (parts.size == 3) {
                "${parts[2]}-${parts[1]}-${parts[0]}"
            } else {
                dataValidadeTexto
            }
        } else {
            dataValidadeTexto
        }

        val parsedDate = try { java.time.LocalDate.parse(isoExpireDate) } catch (e: Exception) { null }
        if (parsedDate != null && parsedDate.isBefore(java.time.LocalDate.now())) {
            etValidade.error = "A validade não pode ser no passado"
            ivValidadeCalendario?.visibility = View.INVISIBLE
            Toast.makeText(context, "A data de validade deve ser hoje ou uma data futura.", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val catalog = if (allCatalogProducts.isNotEmpty()) allCatalogProducts else stockRepo.getCatalogProducts()
                val targetPlace = storagePlaceCode.uppercase().trim()

                // Filtra o catálogo estritamente para o local selecionado (FRIDGE, FREEZER ou PANTRY)
                val placeCatalog = catalog.filter { it.storagePlace.equals(targetPlace, ignoreCase = true) }

                // Busca produto pelo nome no catálogo do local selecionado
                val matched = placeCatalog.firstOrNull {
                    it.name.equals(nome, ignoreCase = true)
                } ?: placeCatalog.firstOrNull {
                    it.name?.contains(nome, ignoreCase = true) == true
                }

                                if (matched == null || matched.id == null) {
                    criarEAdicionarAoEstoque(nome, quantidade, unidade, categoria, categoriaCodigo, storagePlaceCode, isoExpireDate, lugarEstoqueNome)
                    return@launch
                }

                val targetProductId = matched.id
                val validPrice = matched.unitPrice ?: 1.0

                val stockItem = StockProductResponse(
                    productId = targetProductId,
                    product = StockProductProductResponse(
                        id = targetProductId,
                        name = matched.name ?: nome,
                        category = matched.category ?: categoriaCodigo,
                        storagePlace = matched.storagePlace ?: storagePlaceCode,
                        unitPrice = validPrice,
                        unitOfMeasure = matched.unitOfMeasure ?: ShoppingListItem.mapUnitToCode(unidade)
                    ),
                    quantity = quantidade,
                    minimalQuantity = 1,
                    purchaseUnitPrice = validPrice,
                    expireDate = isoExpireDate,
                    productStatus = "FRESH",
                    category = matched.category ?: categoriaCodigo,
                    storagePlace = matched.storagePlace ?: storagePlaceCode
                )

                stockRepo.addStockProduct(stockItem)

                val localDisplay = when (storagePlaceCode) {
                    "FREEZER" -> "no freezer"
                    "PANTRY" -> "na despensa"
                    else -> "na geladeira"
                }

                setLoading(false)
                Toast.makeText(context, "\"$nome\" adicionado com sucesso $localDisplay!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                setLoading(false)
                val msg = e.message ?: "Ocorreu um erro ao salvar o produto no estoque."
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Atenção")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun criarEAdicionarAoEstoque(
        nome: String,
        quantidade: Int,
        unidade: String,
        categoria: String,
        categoriaCodigo: String,
        storagePlaceCode: String,
        isoExpireDate: String,
        lugarEstoqueNome: String
    ) {
        val context = requireContext()
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val novoProduto = stockRepo.createProduct(
                    name = nome,
                    category = categoriaCodigo,
                    storagePlace = storagePlaceCode,
                    unitPrice = 1.0,
                    unitOfMeasure = ShoppingListItem.mapUnitToCode(unidade)
                )

                val targetProductId = novoProduto.id ?: throw IllegalStateException("ID do novo produto não retornado.")

                val stockItem = StockProductResponse(
                    productId = targetProductId,
                    product = novoProduto,
                    quantity = quantidade,
                    minimalQuantity = 1,
                    purchaseUnitPrice = 1.0,
                    expireDate = isoExpireDate,
                    productStatus = "FRESH",
                    category = categoriaCodigo,
                    storagePlace = storagePlaceCode
                )

                stockRepo.addStockProduct(stockItem)

                val localDisplay = when (storagePlaceCode) {
                    "FREEZER" -> "no freezer"
                    "PANTRY" -> "na despensa"
                    else -> "na geladeira"
                }

                setLoading(false)
                Toast.makeText(context, "\"$nome\" cadastrado e adicionado com sucesso $localDisplay!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                setLoading(false)
                val msg = e.message ?: "Ocorreu um erro ao cadastrar o alimento."
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Atenção")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
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
