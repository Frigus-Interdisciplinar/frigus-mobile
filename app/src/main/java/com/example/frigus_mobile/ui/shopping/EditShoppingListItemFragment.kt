package com.example.frigus_mobile.ui.shopping

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.ShoppingDraft
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.google.android.material.button.MaterialButton

class EditShoppingListItemFragment : Fragment() {

    private lateinit var imgProduto: ImageView
    private lateinit var btnCamera: ImageButton
    private lateinit var etNome: EditText
    private lateinit var etQuantidade: EditText
    private lateinit var spinnerUnidade: Spinner
    private lateinit var spinnerCategoria: Spinner
    private lateinit var etObservacoes: EditText
    private lateinit var btnRemover: View
    private lateinit var btnSalvar: MaterialButton

    private val model by lazy { ViewModelProvider(requireActivity())[ShoppingListViewModel::class.java] }
    private var lastError: com.example.frigus_mobile.data.model.ApiError? = null
    private var currentItem: ShoppingListItem? = null
    private var customImageUri: Uri? = null

    private val categorias get() = resources.getStringArray(R.array.shopping_form_categories).toList()
    private val unidades get() = resources.getStringArray(R.array.shopping_remote_unit_labels).toList()

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
        return inflater.inflate(R.layout.fragment_edit_shopping_list_item, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemId = arguments?.getString(ARG_ITEM_ID)

        imgProduto = view.findViewById(R.id.imgProdutoEditarLista)
        btnCamera = view.findViewById(R.id.btnCameraEditarItemLista)
        etNome = view.findViewById(R.id.etNomeItemLista)
        etQuantidade = view.findViewById(R.id.etQuantidadeLista)
        spinnerUnidade = view.findViewById(R.id.spinnerUnidadeLista)
        spinnerCategoria = view.findViewById(R.id.spinnerCategoriaLista)
        etObservacoes = view.findViewById(R.id.etObservacoesLista)
        btnRemover = view.findViewById(R.id.btnRemoverItemLista)
        btnSalvar = view.findViewById(R.id.btnSalvarItemLista)

        view.findViewById<View>(R.id.btnVoltarEditarItemLista).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnCamera.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        configurarSpinners()
        etObservacoes.setHint(R.string.shopping_remote_note_hint)
        btnCamera.contentDescription = getString(R.string.shopping_remote_metadata_local)
        etNome.contentDescription = getString(R.string.shopping_remote_edit_metadata)
        model.state.observe(viewLifecycleOwner) { state ->
            if (state.error == null && !state.uncertain) lastError = null
            val item = itemId?.let(model::item)
            if (currentItem == null && item != null) {
                currentItem = item
                customImageUri = item.imageUrl?.let(Uri::parse)
                preencherDados()
            }
            btnSalvar.isEnabled = item != null
            btnRemover.isEnabled = item != null
            state.completion?.takeIf { it.kind in setOf(ShoppingCompletionKind.UPDATED, ShoppingCompletionKind.REMOVED) }?.let {
                model.consumeCompletion(it.id)
                Toast.makeText(requireContext(), if (it.kind == ShoppingCompletionKind.REMOVED)
                    R.string.shopping_local_removed else R.string.shopping_local_updated, Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            if (state.error != null && state.error != lastError && !state.loading) {
                lastError = state.error
                AlertDialog.Builder(requireContext()).setMessage(shoppingErrorMessage(requireContext(), state))
                    .setPositiveButton(R.string.shopping_remote_refresh) { _, _ -> model.reload(true) }
                    .setNegativeButton(R.string.shopping_remote_cancel, null).show()
            }
        }
        model.loadIfNeeded()

        etNome.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                atualizarImagemProduto()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSalvar.setOnClickListener {
            salvarAlteracoes()
        }

        btnRemover.setOnClickListener {
            confirmarRemocao()
        }
    }

    private fun configurarSpinners() {
        val adapterCat = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, categorias).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerCategoria.adapter = adapterCat

        val adapterUni = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, unidades).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerUnidade.adapter = adapterUni

        spinnerCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                atualizarImagemProduto()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun atualizarImagemProduto() {
        customImageUri?.let { uri ->
            val shown = runCatching {
                imgProduto.setPadding(0, 0, 0, 0)
                imgProduto.clearColorFilter()
                imgProduto.setImageURI(uri)
            }.isSuccess
            if (shown) return
        }
        imgProduto.setPadding(28, 28, 28, 28)
        imgProduto.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_primary))
        imgProduto.setImageResource(ProductCategoryHelper.getIconeCategoria(spinnerCategoria.selectedItem?.toString()))
    }

    private fun preencherDados() {
        val item = currentItem ?: return

        etNome.setText(item.name)
        etQuantidade.setText(item.quantity.toString())
        etObservacoes.setText(item.observation)

        val catMapped = if (categorias.any { it.equals(item.category, ignoreCase = true) }) {
            item.category
        } else {
            ShoppingListItem.mapCodeToCategory(item.categoryCode)
        }
        val categoryLabels = (categorias + catMapped).distinct()
        spinnerCategoria.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, categoryLabels)
        spinnerCategoria.setSelection(categoryLabels.indexOf(catMapped).coerceAtLeast(0))
        val unitLabel = shoppingUnitLabel(requireContext(), item.unitCode ?: item.unit)
        val unitLabels = (unidades + unitLabel).distinct()
        spinnerUnidade.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, unitLabels)
        spinnerUnidade.setSelection(unitLabels.indexOf(unitLabel).coerceAtLeast(0))

        atualizarImagemProduto()
    }

    private fun salvarAlteracoes() {
        val item = currentItem ?: return

        val novoNome = etNome.text.toString().trim()
        if (novoNome.isBlank()) {
            etNome.error = getString(R.string.shopping_form_name_required)
            etNome.requestFocus()
            return
        }

        val qtdStr = etQuantidade.text.toString().trim()
        val quantidade = qtdStr.toIntOrNull()
        if (quantidade == null || quantidade <= 0) {
            etQuantidade.error = getString(R.string.shopping_quantity_positive)
            etQuantidade.requestFocus()
            return
        }

        val observacoes = etObservacoes.text.toString().trim()
        val imageToSave = customImageUri?.toString() ?: item.imageUrl

        val unitCodes = resources.getStringArray(R.array.shopping_remote_unit_codes)
        val categoryCode = ShoppingListItem.mapCategoryToCode(spinnerCategoria.selectedItem.toString())
        val unitCode = unitCodes.getOrNull(spinnerUnidade.selectedItemPosition) ?: item.unitCode ?: "UNIT"
        model.updateItem(item, ShoppingDraft(name = novoNome, quantity = quantidade,
            unitCode = unitCode, categoryCode = categoryCode,
            observation = observacoes, imageUri = imageToSave))
    }

    private fun confirmarRemocao() {
        val item = currentItem ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.shopping_remote_remove_title)
            .setMessage(getString(R.string.shopping_remote_remove_message, item.name))
            .setPositiveButton(R.string.shopping_remote_remove) { _, _ ->
                model.deleteItem(item)
            }
            .setNegativeButton(R.string.shopping_remote_cancel, null)
            .show()
    }

    companion object {
        private const val ARG_ITEM_ID = "ITEM_ID"

        fun newInstance(itemId: String): EditShoppingListItemFragment {
            return EditShoppingListItemFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_ID, itemId)
                }
            }
        }
    }
}
