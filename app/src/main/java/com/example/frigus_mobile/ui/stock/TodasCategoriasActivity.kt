package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.R

class TodasCategoriasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todas_categorias)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.itemCarnes).setOnClickListener {
            abrirCategoria("MEAT", "Carnes")
        }

        findViewById<View>(R.id.itemLaticinios).setOnClickListener {
            abrirCategoria("DAIRY", "Laticínios")
        }

        findViewById<View>(R.id.itemHortifruti).setOnClickListener {
            abrirCategoria("VEGETABLE", "Hortifruti")
        }

        findViewById<View>(R.id.itemBebidas).setOnClickListener {
            abrirCategoria("BEVERAGE", "Bebidas")
        }
    }

    private fun abrirCategoria(code: String, name: String) {
        val intent = Intent(this, CategoriaItensActivity::class.java).apply {
            putExtra("CATEGORY_CODE", code)
            putExtra("CATEGORY_NAME", name)
        }
        startActivity(intent)
    }
}
