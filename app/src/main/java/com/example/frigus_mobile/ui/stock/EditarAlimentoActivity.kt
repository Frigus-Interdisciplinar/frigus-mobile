package com.example.frigus_mobile.ui.stock

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ProductCategoryHelper
import kotlinx.coroutines.launch

class EditarAlimentoActivity : AppCompatActivity() {

    private lateinit var repository: StockRepository
    private var item: StockProductResponse? = null

    private lateinit var etNome: EditText
    private lateinit var etValidade: EditText
    private lateinit var etQuantidade: EditText
    private lateinit var spinnerCategoria: Spinner
    private lateinit var spinnerLocal: Spinner
    private lateinit var spinnerUnidade: Spinner

    private val locais = listOf("Geladeira", "Freezer", "Despensa")
    private val categoriasGeladeira = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasFreezer = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasDespensa = listOf("Grãos", "Hortifruti", "Laticínios", "Bebidas")
    private val unidades = listOf("Gramas", "Quilos", "Litros", "Mililitros", "Unidades")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_alimento)

        repository = StockRepository(this)

        item = intent.getSerializableExtra("STOCK_PRODUCT") as? StockProductResponse

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        etNome = findViewById(R.id.etNomeAlimento)
        etValidade = findViewById(R.id.etValidade)
        etQuantidade = findViewById(R.id.etQuantidadeTotal)
        spinnerCategoria = findViewById(R.id.spinnerCategoria)
        spinnerLocal = findViewById(R.id.spinnerLocal)
        spinnerUnidade = findViewById(R.id.spinnerUnidade)

        configurarSpinners()
        preencherDados()

        findViewById<View>(R.id.btnSalvarAlteracoes).setOnClickListener {
            salvarAlteracoes()
        }
    }

    private fun configurarSpinners() {
        spinnerLocal.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locais)
        spinnerUnidade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unidades)

        spinnerLocal.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                atualizarCategoriasPorLocal(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun atualizarCategoriasPorLocal(localIndex: Int, categoriaSelecionada: String? = null) {
        val cats = when (localIndex) {
            2 -> categoriasDespensa
            1 -> categoriasFreezer
            else -> categoriasGeladeira
        }
        val currentCategory = categoriaSelecionada ?: spinnerCategoria.selectedItem?.toString()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats)
        spinnerCategoria.adapter = adapter

        val index = if (currentCategory != null && cats.contains(currentCategory)) {
            cats.indexOf(currentCategory)
        } else {
            0
        }
        spinnerCategoria.setSelection(index)
    }

    private fun preencherDados() {
        val prod = item ?: return
        etNome.setText(prod.product?.name ?: "")
        etValidade.setText(prod.expireDate ?: "")
        etQuantidade.setText(prod.quantity?.toString() ?: "")

        val rawPlace = (prod.storagePlace ?: prod.product?.storagePlace ?: "").uppercase()
        val localIndex = when (rawPlace) {
            "FREEZER" -> 1
            "PANTRY", "DESPENSA" -> 2
            else -> 0
        }
        spinnerLocal.setSelection(localIndex)

        val catName = when (prod.category ?: prod.product?.category) {
            "MEAT" -> "Carnes"
            "DAIRY" -> "Laticínios"
            "FRUIT", "VEGETABLE" -> "Hortifruti"
            "BEVERAGE" -> "Bebidas"
            "GRAIN" -> "Grãos"
            else -> "Carnes"
        }
        atualizarCategoriasPorLocal(localIndex, catName)

        val unitIndex = when (prod.product?.unitOfMeasure) {
            "GRAM" -> 0
            "KILOGRAM" -> 1
            "LITER" -> 2
            "MILLILITER" -> 3
            else -> 4
        }
        spinnerUnidade.setSelection(unitIndex)

        val img = findViewById<ImageView>(R.id.imgEditarAlimento)
        val imgRes = ProductCategoryHelper.getImagemParaProduto(prod.product?.name, prod.category ?: prod.product?.category)
        if (imgRes == R.drawable.img_frango) {
            img.clearColorFilter()
            img.setImageResource(imgRes)
        } else {
            img.setImageResource(imgRes)
        }
    }

    private fun salvarAlteracoes() {
        val prodId = item?.id ?: return
        if (!NetworkGuard.ensureConnected(this) { salvarAlteracoes() }) return

        val novaQtd = etQuantidade.text.toString().toIntOrNull()
        val novaValidade = etValidade.text.toString().trim()
        val localSelecionado = when (spinnerLocal.selectedItemPosition) {
            1 -> "FREEZER"
            2 -> "PANTRY"
            else -> "FRIDGE"
        }

        lifecycleScope.launch {
            try {
                repository.updateStockProduct(
                    id = prodId,
                    quantity = novaQtd,
                    expireDate = novaValidade,
                    storagePlace = localSelecionado
                )
                Toast.makeText(this@EditarAlimentoActivity, "Alimento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@EditarAlimentoActivity, e.message) {
                    salvarAlteracoes()
                }
            }
        }
    }
}
