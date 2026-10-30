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
import com.example.frigus_mobile.data.repository.ShoppingListRepository
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

    private lateinit var shoppingRepo: ShoppingListRepository
    private var currentItem: ShoppingListItem? = null
    private var customImageUri: Uri? = null

    private val categorias = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas", "Grãos")
    private val unidades = listOf("Gramas", "Quilos", "Litros", "Mililitros", "Unidades")

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
        return inflater.inflate(R.layout.fragment_edit_shopping_list_item, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shoppingRepo = ShoppingListRepository(requireContext())

        val itemId = arguments?.getString(ARG_ITEM_ID)
        currentItem = itemId?.let { shoppingRepo.getItemById(it) }

        if (currentItem == null) {
            Toast.makeText(requireContext(), "Item não encontrado", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

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
            pickImageLauncher.launch("image/*")
        }

        configurarSpinners()
        preencherDados()

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
        if (customImageUri != null) return

        val nome = etNome.text.toString().trim()
        val categoria = spinnerCategoria.selectedItem?.toString()

        if (nome.contains("frango", ignoreCase = true)) {
            imgProduto.setPadding(0, 0, 0, 0)
            imgProduto.clearColorFilter()
            imgProduto.setImageResource(R.drawable.img_frango)
        } else {
            val savedImageUrl = currentItem?.imageUrl
            if (!savedImageUrl.isNullOrBlank() && (savedImageUrl.startsWith("content://") || savedImageUrl.startsWith("file://"))) {
                try {
                    imgProduto.setPadding(0, 0, 0, 0)
                    imgProduto.clearColorFilter()
                    imgProduto.setImageURI(Uri.parse(savedImageUrl))
                    return
                } catch (_: Exception) {
                    // fallback para ícone da categoria
                }
            }
            imgProduto.setPadding(28, 28, 28, 28)
            imgProduto.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_primary))
            imgProduto.setImageResource(ProductCategoryHelper.getIconeCategoria(categoria))
        }
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
        val catIndex = categorias.indexOfFirst { it.equals(catMapped, ignoreCase = true) }
        if (catIndex != -1) {
            spinnerCategoria.setSelection(catIndex)
        } else {
            spinnerCategoria.setSelection(0)
        }

        val uniNormalized = when (item.unit.lowercase()) {
            "g", "gramas" -> "Gramas"
            "kg", "quilos" -> "Quilos"
            "l", "litros" -> "Litros"
            "ml", "mililitros" -> "Mililitros"
            else -> "Unidades"
        }
        val uniIndex = unidades.indexOfFirst { it.equals(uniNormalized, ignoreCase = true) }
        if (uniIndex != -1) {
            spinnerUnidade.setSelection(uniIndex)
        } else {
            spinnerUnidade.setSelection(unidades.indexOf("Unidades").coerceAtLeast(0))
        }

        atualizarImagemProduto()
    }

    private fun salvarAlteracoes() {
        val item = currentItem ?: return

        val novoNome = etNome.text.toString().trim()
        if (novoNome.isBlank()) {
            etNome.error = "Informe o nome do alimento"
            etNome.requestFocus()
            return
        }

        val qtdStr = etQuantidade.text.toString().trim()
        val quantidade = qtdStr.toIntOrNull() ?: item.quantity
        if (quantidade <= 0) {
            etQuantidade.error = "Quantidade deve ser maior que zero"
            etQuantidade.requestFocus()
            return
        }

        val categoriaSelecionada = spinnerCategoria.selectedItem?.toString() ?: item.category
        val unidadeSelecionada = spinnerUnidade.selectedItem?.toString() ?: item.unit
        val observacoes = etObservacoes.text.toString().trim()
        val imageToSave = customImageUri?.toString() ?: item.imageUrl

        val updated = item.copy(
            name = novoNome,
            quantity = quantidade,
            unit = unidadeSelecionada,
            category = categoriaSelecionada,
            categoryCode = ShoppingListItem.mapCategoryToCode(categoriaSelecionada),
            observation = observacoes,
            imageUrl = imageToSave
        )

        shoppingRepo.updateItem(updated)
        Toast.makeText(requireContext(), "Item atualizado!", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    private fun confirmarRemocao() {
        val item = currentItem ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Remover item")
            .setMessage("Deseja remover \"${item.name}\" da sua lista de compras?")
            .setPositiveButton("Remover") { _, _ ->
                shoppingRepo.deleteItem(item.id)
                Toast.makeText(requireContext(), "Item removido!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Cancelar", null)
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
