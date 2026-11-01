package com.example.frigus_mobile.utils

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExpirationStatus(
    val formattedDate: String,
    val statusText: String,
    @get:ColorRes val colorRes: Int
)

object ProductCategoryHelper {

    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    /**
     * Calcula o status de vencimento inteligente baseado na data da API.
     */
    fun calcularStatusValidade(dataStr: String?): ExpirationStatus? {
        if (dataStr.isNullOrBlank()) return null
        return try {
            val expDate = inputDateFormat.parse(dataStr) ?: return null
            val formattedDate = outputDateFormat.format(expDate)

            val hoje = zerarHora(Date())
            val exp = zerarHora(expDate)
            val diffMs = exp.time - hoje.time
            val diffDias = TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS).toInt()

            val (statusText, colorRes) = when {
                diffDias < 0 -> {
                    val diasAtras = -diffDias
                    val txt = if (diasAtras == 1) "Venceu ontem" else "Venceu há $diasAtras dias"
                    Pair(txt, R.color.color_red)
                }
                diffDias == 0 -> {
                    Pair("Vence hoje", R.color.color_gold)
                }
                diffDias <= 3 -> {
                    val txt = if (diffDias == 1) "Vence amanhã" else "Vence em $diffDias dias"
                    Pair(txt, R.color.color_gold)
                }
                else -> {
                    Pair("Vence em $diffDias dias", R.color.color_green)
                }
            }

            ExpirationStatus(formattedDate, statusText, colorRes)
        } catch (_: Exception) {
            null
        }
    }

    private fun zerarHora(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    /**
     * Retorna o recurso de ícone correspondente à categoria oficial do backend.
     */
    @DrawableRes
    fun getIconeCategoria(categoria: String?): Int {
        val cat = categoria?.uppercase() ?: ""
        return when {
            cat.contains("MEAT") || cat.contains("CARNE") || cat.contains("PEIXE") -> R.drawable.ic_cat_meat
            cat.contains("DAIRY") || cat.contains("LATICINIO") -> R.drawable.ic_cat_dairy
            cat.contains("VEGETABLE") || cat.contains("FRUIT") || cat.contains("HORTIFRUTI") -> R.drawable.ic_cat_hortifruti
            cat.contains("BEVERAGE") || cat.contains("BEBIDA") -> R.drawable.ic_cat_beverages
            cat.contains("GRAIN") || cat.contains("GRAO") || cat.contains("GRÃO") || cat.contains("CEREAL") -> R.drawable.ic_cat_grains
            cat.contains("FREEZER") || cat.contains("CONGELADO") -> R.drawable.ic_freezer
            cat.contains("TEMPERO") || cat.contains("SEASONING") -> R.drawable.ic_food_garlic
            else -> R.drawable.ic_food_package
        }
    }

    /**
     * Retorna imagem específica (ex.: Frango) ou ícone padrão da categoria.
     */
    @DrawableRes
    fun getImagemParaProduto(nome: String?, categoria: String?): Int {
        val n = nome?.trim()?.lowercase() ?: ""
        if (n.contains("frango")) {
            return R.drawable.img_frango
        }
        return getIconeCategoria(categoria)
    }

    /**
     * Formata quantidade de itens de forma singular/plural.
     */
    fun formatarQtdItens(qtd: Int): String {
        return if (qtd == 1) "1 item" else "$qtd itens"
    }

    /**
     * Conta a quantidade de alimentos que pertencem à categoria informada.
     */
    fun contarPorCategoria(lista: List<StockProductResponse>, categoriaCodigo: String): Int {
        val target = categoriaCodigo.uppercase()
        return lista.count { item ->
            val cat = (item.category ?: item.product?.category ?: "").uppercase()
            val name = (item.product?.name ?: "").lowercase()
            when (target) {
                "MEAT" -> cat == "MEAT" || cat.contains("CARNE") || cat.contains("PEIXE") || name.contains("carne") || name.contains("frango") || name.contains("linguiça") || name.contains("filé")
                "DAIRY" -> cat == "DAIRY" || cat.contains("LATICINIO") || name.contains("leite") || name.contains("queijo") || name.contains("iogurte") || name.contains("sorvete") || name.contains("picolé")
                "VEGETABLE", "FRUIT", "HORTIFRUTI" -> cat == "VEGETABLE" || cat == "FRUIT" || cat.contains("HORTIFRUTI") || name.contains("tomate") || name.contains("banana") || name.contains("maçã") || name.contains("ervilha") || name.contains("legume") || name.contains("batata") || name.contains("polpa") || name.contains("açaí")
                "BEVERAGE" -> cat == "BEVERAGE" || cat.contains("BEBIDA") || name.contains("suco") || name.contains("café") || name.contains("água") || name.contains("gelo")
                "GRAIN" -> cat == "GRAIN" || cat.contains("GRAO") || name.contains("arroz") || name.contains("feijão") || name.contains("farinha")
                else -> cat == target
            }
        }
    }
}
