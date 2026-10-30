package com.example.frigus_mobile.ui.shopping

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.ShoppingListRepository
import com.example.frigus_mobile.data.repository.StockRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.Calendar

class ShoppingListFragment : Fragment() {

    private lateinit var shoppingRepo: ShoppingListRepository
    private lateinit var adapter: ShoppingListAdapter
    private var allItems: List<ShoppingListItem> = emptyList()
    private var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

    private lateinit var etBuscarAdicionar: EditText
    private lateinit var rvLista: RecyclerView
    private lateinit var tvVazia: TextView
    private lateinit var fabAdicionar: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shopping_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shoppingRepo = ShoppingListRepository(requireContext())

        etBuscarAdicionar = view.findViewById(R.id.etAdicionarProdutoLista)
        rvLista = view.findViewById(R.id.rvListaCompras)
        tvVazia = view.findViewById(R.id.tvListaComprasVazia)
        fabAdicionar = view.findViewById(R.id.btnAdicionarItemLista)

        view.findViewById<View>(R.id.btnVoltarListaCompras).setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.nav_home
        }

        setupRecyclerView()

        fabAdicionar.setOnClickListener {
            abrirAdicionarItem(etBuscarAdicionar.text.toString().trim().takeIf { it.isNotBlank() })
        }

        etBuscarAdicionar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val texto = etBuscarAdicionar.text.toString().trim()
                if (texto.isNotBlank()) {
                    abrirAdicionarItem(texto)
                    true
                } else false
            } else false
        }

        etBuscarAdicionar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarItens(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        carregarItens()
    }

    override fun onResume() {
        super.onResume()
        carregarItens()
        firestoreListener?.remove()
        firestoreListener = shoppingRepo.listenToChanges { remoteItems ->
            allItems = remoteItems
            filtrarItens(etBuscarAdicionar.text.toString())
        }
    }

    override fun onPause() {
        super.onPause()
        firestoreListener?.remove()
        firestoreListener = null
    }

    private fun setupRecyclerView() {
        rvLista.layoutManager = LinearLayoutManager(requireContext())
        adapter = ShoppingListAdapter(
            onItemClick = { item ->
                abrirEditarItem(item.id)
            },
            onCheckChanged = { item, isChecked ->
                if (isChecked) {
                    mostrarPopupAdicionarAoEstoque(item)
                } else {
                    shoppingRepo.toggleChecked(item.id, false)
                    carregarItens()
                }
            }
        )
        rvLista.adapter = adapter
    }

    private fun mostrarPopupAdicionarAoEstoque(item: ShoppingListItem) {
        val locais = arrayOf("Geladeira", "Freezer", "Despensa")

        viewLifecycleOwner.lifecycleScope.launch {
            val stockRepo = StockRepository(requireContext())
            val catalog = try { stockRepo.getCatalogProducts() } catch (_: Exception) { emptyList() }
            val matched = catalog.firstOrNull { it.name.equals(item.name, ignoreCase = true) }
                ?: catalog.firstOrNull { it.name?.contains(item.name, ignoreCase = true) == true }

            val localPadraoIdx = when (matched?.storagePlace?.uppercase()) {
                "FREEZER" -> 1
                "PANTRY" -> 2
                else -> 0
            }

            var localEscolhidoIdx = localPadraoIdx

            AlertDialog.Builder(requireContext())
                .setTitle("Adicionar ao estoque")
                .setSingleChoiceItems(locais, localPadraoIdx) { _, which ->
                    localEscolhidoIdx = which
                }
                .setPositiveButton("Continuar") { _, _ ->
                    val localEscolhido = locais[localEscolhidoIdx]
                    val storagePlaceCode = when (localEscolhido) {
                        "Freezer" -> "FREEZER"
                        "Despensa" -> "PANTRY"
                        else -> "FRIDGE"
                    }
                    solicitarDataValidadeEEnviar(item, storagePlaceCode, localEscolhido)
                }
                .setNegativeButton("Apenas marcar") { _, _ ->
                    shoppingRepo.toggleChecked(item.id, true)
                    carregarItens()
                }
                .setOnCancelListener {
                    shoppingRepo.toggleChecked(item.id, true)
                    carregarItens()
                }
                .show()
        }
    }

    private fun solicitarDataValidadeEEnviar(item: ShoppingListItem, storagePlaceCode: String, localNome: String) {
        val calendario = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val expireDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                adicionarAoEstoque(item, expireDate, storagePlaceCode, localNome)
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.setTitle("Validade ($localNome) *")
        datePicker.datePicker.minDate = calendario.timeInMillis
        datePicker.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar") { _, _ ->
            shoppingRepo.toggleChecked(item.id, true)
            carregarItens()
        }
        datePicker.show()
    }

    private fun adicionarAoEstoque(item: ShoppingListItem, expireDate: String, storagePlaceCode: String, localNome: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val stockRepo = StockRepository(requireContext())
                val catalog = stockRepo.getCatalogProducts()
                val matched = catalog.firstOrNull {
                    it.name.equals(item.name, ignoreCase = true) && it.storagePlace.equals(storagePlaceCode, ignoreCase = true)
                } ?: catalog.firstOrNull {
                    it.name.equals(item.name, ignoreCase = true)
                } ?: catalog.firstOrNull {
                    it.name?.contains(item.name, ignoreCase = true) == true
                }

                val productToUse: StockProductProductResponse = if (matched != null && matched.id != null) {
                    matched
                } else {
                    stockRepo.createProduct(
                        name = item.name,
                        category = item.categoryCode,
                        storagePlace = storagePlaceCode,
                        unitPrice = 1.0,
                        unitOfMeasure = ShoppingListItem.mapUnitToCode(item.unit)
                    )
                }

                val targetProductId = productToUse.id ?: throw IllegalStateException("ID do produto indisponível.")
                val validPrice = productToUse.unitPrice ?: 1.0

                val stockItem = StockProductResponse(
                    productId = targetProductId,
                    product = StockProductProductResponse(
                        id = targetProductId,
                        name = productToUse.name ?: item.name,
                        category = productToUse.category ?: item.categoryCode,
                        storagePlace = storagePlaceCode,
                        unitPrice = validPrice,
                        unitOfMeasure = productToUse.unitOfMeasure ?: ShoppingListItem.mapUnitToCode(item.unit)
                    ),
                    quantity = item.quantity,
                    minimalQuantity = 1,
                    purchaseUnitPrice = validPrice,
                    expireDate = expireDate,
                    productStatus = "FRESH",
                    category = productToUse.category ?: item.categoryCode,
                    storagePlace = storagePlaceCode
                )

                stockRepo.addStockProduct(stockItem)
                shoppingRepo.toggleChecked(item.id, true)

                val localDisplay = when (storagePlaceCode) {
                    "FREEZER" -> "ao seu freezer"
                    "PANTRY" -> "à sua despensa"
                    else -> "à sua geladeira"
                }
                Toast.makeText(requireContext(), "\"${item.name}\" foi adicionado $localDisplay!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                shoppingRepo.toggleChecked(item.id, true)
                val msg = e.message ?: "Conexão indisponível"
                Toast.makeText(requireContext(), "Item marcado na lista. (Estoque: $msg)", Toast.LENGTH_LONG).show()
            }
            carregarItens()
        }
    }

    private fun carregarItens() {
        allItems = shoppingRepo.getItems()
        filtrarItens(etBuscarAdicionar.text.toString())
    }

    private fun filtrarItens(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.name.lowercase().contains(q) }
        }

        adapter.setItems(filtered)

        if (filtered.isEmpty()) {
            tvVazia.visibility = View.VISIBLE
            rvLista.visibility = View.GONE
        } else {
            tvVazia.visibility = View.GONE
            rvLista.visibility = View.VISIBLE
        }
    }

    private fun abrirAdicionarItem(initialName: String?) {
        val fragment = AddShoppingListItemFragment.newInstance(
            initialName = initialName,
            initialDestination = AddShoppingListItemFragment.DESTINATION_SHOPPING_LIST
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun abrirEditarItem(itemId: String) {
        val fragment = EditShoppingListItemFragment.newInstance(itemId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
