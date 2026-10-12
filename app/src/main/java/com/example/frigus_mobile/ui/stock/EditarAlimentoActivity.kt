package com.example.frigus_mobile.ui.stock

import android.os.Bundle
import android.view.View
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

    private val categorias = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas", "Outros")
    private val locais = listOf("Geladeira", "Freezer", "Despensa")
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
        spinnerCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
        spinnerLocal.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locais)
        spinnerUnidade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unidades)
    }

    private fun preencherDados() {
        val prod = item ?: return
        etNome.setText(prod.product?.name ?: "")
        etValidade.setText(prod.expireDate ?: "")
        etQuantidade.setText(prod.quantity?.toString() ?: "")

        val catIndex = when (prod.category ?: prod.product?.category) {
            "MEAT" -> 0
            "DAIRY" -> 1
            "FRUIT", "VEGETABLE" -> 2
            "BEVERAGE" -> 3
            else -> 4
        }
        spinnerCategoria.setSelection(catIndex)

        val localIndex = when (prod.product?.storagePlace) {
            "FREEZER" -> 1
            "PANTRY" -> 2
            else -> 0
        }
        spinnerLocal.setSelection(localIndex)

        val unitIndex = when (prod.product?.unitOfMeasure) {
            "GRAM" -> 0
            "KILOGRAM" -> 1
            "LITER" -> 2
            "MILLILITER" -> 3
            else -> 4
        }
        spinnerUnidade.setSelection(unitIndex)

        val img = findViewById<ImageView>(R.id.imgEditarAlimento)
        when (prod.category ?: prod.product?.category) {
            "MEAT" -> img.setImageResource(R.drawable.ic_cat_meat)
            "DAIRY" -> img.setImageResource(R.drawable.ic_cat_dairy)
            "FRUIT", "VEGETABLE" -> img.setImageResource(R.drawable.ic_cat_hortifruti)
            "BEVERAGE" -> img.setImageResource(R.drawable.ic_cat_beverages)
            else -> img.setImageResource(R.drawable.ic_food_package)
        }
    }

    private fun salvarAlteracoes() {
        val prodId = item?.id ?: return
        val novaQtd = etQuantidade.text.toString().toIntOrNull()
        val novaValidade = etValidade.text.toString().trim()
        val localSelecionado = when (spinnerLocal.selectedItemPosition) {
            1 -> "FREEZER"
            2 -> "PANTRY"
            else -> "FRIDGE"
        }

        lifecycleScope.launch {
            repository.updateStockProduct(
                id = prodId,
                quantity = novaQtd,
                expireDate = novaValidade,
                storagePlace = localSelecionado
            )
            Toast.makeText(this@EditarAlimentoActivity, "Alimento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
