package com.example.frigus_mobile.ui.stock

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.example.frigus_mobile.utils.ProductImageTracker
import com.example.frigus_mobile.utils.StockObservationsTracker
import com.example.frigus_mobile.utils.StockQuantityTracker
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class EditarAlimentoActivity : AppCompatActivity() {

    private lateinit var repository: StockRepository
    private var item: StockProductResponse? = null

    private lateinit var imgEditarAlimento: ImageView
    private lateinit var etNome: EditText
    private lateinit var etValidade: EditText
    private lateinit var etQuantidade: EditText
    private lateinit var etObservacoes: EditText
    private lateinit var spinnerLocal: Spinner
    private lateinit var spinnerCategoria: Spinner
    private lateinit var spinnerUnidade: Spinner

    private var customImageUri: Uri? = null

    private val locais = listOf("Geladeira", "Freezer", "Despensa")
    private val categoriasGeladeira = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasFreezer = listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
    private val categoriasDespensa = listOf("Grãos", "Hortifruti", "Laticínios", "Bebidas")
    private val unidades = listOf("Gramas", "Quilos", "Litros", "Mililitros", "Unidades")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            customImageUri = uri
            imgEditarAlimento.setPadding(0, 0, 0, 0)
            imgEditarAlimento.clearColorFilter()
            imgEditarAlimento.scaleType = ImageView.ScaleType.CENTER_CROP
            imgEditarAlimento.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_alimento)

        repository = StockRepository(this)
        @Suppress("DEPRECATION")
        item = intent.getSerializableExtra("STOCK_PRODUCT") as? StockProductResponse

        imgEditarAlimento = findViewById(R.id.imgEditarAlimento)
        etNome = findViewById(R.id.etNomeAlimento)
        etValidade = findViewById(R.id.etValidade)
        etQuantidade = findViewById(R.id.etQuantidadeTotal)
        etObservacoes = findViewById(R.id.etObservacoesAlimento)
        spinnerLocal = findViewById(R.id.spinnerLocal)
        spinnerCategoria = findViewById(R.id.spinnerCategoria)
        spinnerUnidade = findViewById(R.id.spinnerUnidade)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        // Permitir editar imagem pelo botão de câmera ou tocando na foto
        findViewById<View>(R.id.btnEditarFotoAlimento)?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.layoutFotoEditarAlimento)?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // DatePicker para o campo Validade (somente data de hoje para frente)
        etValidade.setOnClickListener {
            abrirDatePickerValidade()
        }

        configurarSpinners()
        preencherDados()

        findViewById<View>(R.id.btnSalvarAlteracoes).setOnClickListener {
            salvarAlteracoes()
        }
    }

    private fun abrirDatePickerValidade() {
        val calendar = Calendar.getInstance()
        val textoAtual = etValidade.text.toString().trim()

        if (textoAtual.isNotEmpty()) {
            try {
                if (textoAtual.contains("/")) {
                    val partes = textoAtual.split("/")
                    if (partes.size == 3) {
                        calendar.set(Calendar.DAY_OF_MONTH, partes[0].toInt())
                        calendar.set(Calendar.MONTH, partes[1].toInt() - 1)
                        calendar.set(Calendar.YEAR, partes[2].toInt())
                    }
                } else if (textoAtual.contains("-")) {
                    val partes = textoAtual.split("-")
                    if (partes.size == 3) {
                        calendar.set(Calendar.YEAR, partes[0].toInt())
                        calendar.set(Calendar.MONTH, partes[1].toInt() - 1)
                        calendar.set(Calendar.DAY_OF_MONTH, partes[2].toInt())
                    }
                }
            } catch (_: Exception) {}
        }

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatado = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                etValidade.setText(formatado)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Limita a seleção estritamente da data de hoje para a frente
        val hoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        datePicker.datePicker.minDate = hoje.timeInMillis
        datePicker.show()
    }

    private fun configurarSpinners() {
        spinnerLocal.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locais)
        spinnerUnidade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unidades)

        // O usuário seleciona primeiro o local, e a categoria é atualizada com base no local escolhido
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

        val prodId = prod.id ?: 0
        val currentQty = prod.quantity ?: 0
        if (prodId > 0 && currentQty > 0) {
            StockQuantityTracker.getOrSetInitialQuantity(this, prodId, currentQty)
        }

        etNome.setText(prod.product?.name ?: "")
        etValidade.setText(formatarDataBr(prod.expireDate))
        etQuantidade.setText(currentQty.takeIf { it > 0 }?.toString() ?: "1")

        // Recupera observação salva se houver
        if (prodId > 0) {
            val savedObs = StockObservationsTracker.getObservation(this, prodId)
            etObservacoes.setText(savedObs ?: "")
        }

        // 1. Configura Local
        val rawPlace = (prod.storagePlace ?: prod.product?.storagePlace ?: "").uppercase()
        val localIndex = when (rawPlace) {
            "FREEZER" -> 1
            "PANTRY", "DESPENSA" -> 2
            else -> 0
        }
        spinnerLocal.setSelection(localIndex)

        // 2. Configura Categoria adaptada ao local
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

        // Foto do alimento (customizada pelo usuário ou padrão da categoria)
        carregarFotoAlimento(prod)
    }

    private fun carregarFotoAlimento(prod: StockProductResponse) {
        val prodId = prod.id ?: 0
        val customUriStr = ProductImageTracker.getImageUri(this, prodId)

        if (!customUriStr.isNullOrBlank()) {
            try {
                imgEditarAlimento.setPadding(0, 0, 0, 0)
                imgEditarAlimento.clearColorFilter()
                imgEditarAlimento.scaleType = ImageView.ScaleType.CENTER_CROP
                imgEditarAlimento.setImageURI(Uri.parse(customUriStr))
                return
            } catch (_: Exception) {}
        }

        val imgRes = ProductCategoryHelper.getImagemParaProduto(prod.product?.name, prod.category ?: prod.product?.category)
        imgEditarAlimento.setPadding(0, 0, 0, 0)
        imgEditarAlimento.clearColorFilter()
        if (imgRes == R.drawable.img_frango) {
            imgEditarAlimento.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            imgEditarAlimento.scaleType = ImageView.ScaleType.FIT_CENTER
        }
        imgEditarAlimento.setImageResource(imgRes)
    }

    private fun formatarDataBr(dataStr: String?): String {
        if (dataStr.isNullOrBlank()) return ""
        val trimmed = dataStr.trim()
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            val p = trimmed.split("-")
            return "${p[2]}/${p[1]}/${p[0]}"
        }
        return trimmed
    }

    private fun salvarAlteracoes() {
        val prodId = item?.id ?: return
        if (!NetworkGuard.ensureConnected(this) { salvarAlteracoes() }) return

        val novaQtd = etQuantidade.text.toString().toIntOrNull()
        if (novaQtd == null || novaQtd <= 0) {
            etQuantidade.error = "Informe uma quantidade válida"
            return
        }

        val novaValidade = etValidade.text.toString().trim()
        val localSelecionado = when (spinnerLocal.selectedItemPosition) {
            1 -> "FREEZER"
            2 -> "PANTRY"
            else -> "FRIDGE"
        }

        val observacoes = etObservacoes.text.toString().trim()

        lifecycleScope.launch {
            try {
                repository.updateStockProduct(
                    id = prodId,
                    quantity = novaQtd,
                    expireDate = novaValidade,
                    storagePlace = localSelecionado
                )

                // Salva observações localmente
                StockObservationsTracker.setObservation(this@EditarAlimentoActivity, prodId, observacoes)

                // Salva foto personalizada caso o usuário tenha escolhido uma nova
                customImageUri?.let { uri ->
                    ProductImageTracker.setImageUri(this@EditarAlimentoActivity, prodId, uri.toString())
                }

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
