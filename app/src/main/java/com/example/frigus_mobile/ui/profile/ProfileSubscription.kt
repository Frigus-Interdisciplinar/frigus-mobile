package com.example.frigus_mobile.ui.profile

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Cartão salvo localmente. Guarda SOMENTE bandeira, últimos 4 dígitos, nome e validade
 * (nunca número completo, CVV ou CPF).
 */
data class CartaoSalvo(
    val bandeira: String,
    val ultimos4: String,
    val nome: String,
    val validade: String,
    val padrao: Boolean = false
)

/** Detecta a bandeira pelo prefixo do número (só dígitos). */
fun detectarBandeira(digitos: String): String = when {
    digitos.startsWith("4") -> "VISA"
    digitos.length >= 2 && digitos.substring(0, 2).toIntOrNull() in 51..55 -> "Mastercard"
    digitos.length >= 4 && digitos.substring(0, 4).toIntOrNull() in 2221..2720 -> "Mastercard"
    else -> "Cartão"
}

/** Lista local de cartões (ProfileStore). O BFF mobile ainda não expõe cartões. */
class CartoesLocais(context: Context) {
    private val store = ProfileStore(context)

    private fun limpar(texto: String) = texto.replace("|", " ").replace("\n", " ").trim()
    private fun idDe(c: CartaoSalvo) = "${c.bandeira}|${c.ultimos4}"

    private fun linhas(): List<List<String>> =
        store.getList(CHAVE_CARTOES).map { it.split("|") }.filter { it.size >= 4 }

    fun listar(): List<CartaoSalvo> {
        val brutos = linhas()
        if (brutos.isEmpty() && !store.getBoolean("cartoes_inicializados", false)) {
            val padraoMock = listOf(
                CartaoSalvo("Mastercard", "1234", "Juliana Martins", "10/28", padrao = true),
                CartaoSalvo("VISA", "5678", "Juliana Martins", "08/29", padrao = false)
            )
            store.putBoolean("cartoes_inicializados", true)
            padraoMock.forEach { adicionar(it, it.padrao) }
            return padraoMock
        }
        val ids = brutos.map { "${it[0]}|${it[1]}" }
        val salvo = store.getString(CHAVE_PADRAO, null)
        val idPadrao = if (salvo in ids) salvo else ids.firstOrNull()
        return brutos.mapIndexed { i, l -> CartaoSalvo(l[0], l[1], l[2], l[3], ids[i] == idPadrao) }
    }

    fun padrao(): CartaoSalvo? = listar().firstOrNull { it.padrao }

    fun adicionar(cartao: CartaoSalvo, definirPadrao: Boolean) {
        val restantes = listar().filter { idDe(it) != idDe(cartao) }
        val todos = restantes + cartao
        store.putList(CHAVE_CARTOES, todos.map { "${limpar(it.bandeira)}|${it.ultimos4}|${limpar(it.nome)}|${limpar(it.validade)}" })
        if (definirPadrao || restantes.isEmpty()) store.putString(CHAVE_PADRAO, idDe(cartao))
    }

    fun definirPadrao(cartao: CartaoSalvo) = store.putString(CHAVE_PADRAO, idDe(cartao))

    fun remover(cartao: CartaoSalvo) {
        val restantes = listar().filter { idDe(it) != idDe(cartao) }
        store.putList(CHAVE_CARTOES, restantes.map { "${it.bandeira}|${it.ultimos4}|${limpar(it.nome)}|${limpar(it.validade)}" })
        if (cartao.padrao) store.putString(CHAVE_PADRAO, null)
    }

    private companion object {
        const val CHAVE_CARTOES = "cartoes"
        const val CHAVE_PADRAO = "cartao_padrao"
    }
}

/** Dados do plano exibidos nas telas de assinatura. [proximaCobranca] vira "benefícios até" se [cancelada]. */
data class InfoPlano(
    val nome: String,
    val preco: Double,
    val renovacao: String,
    val proximaCobranca: String,
    val cartao: CartaoSalvo?,
    val cancelada: Boolean
)

/**
 * Plano atual do usuário, compartilhado por todas as telas de plano.
 * Tenta a API; como `getMySubscription()` não está liberado no BFF (lança IOException),
 * usa um plano local de fallback (Premium, R$ 29,90/mês) sem mostrar erro bloqueante.
 */
object PlanoAtual {
    private const val CHAVE_CANCELADA = "assinatura_cancelada_ate"
    private const val NOME_PADRAO = "Premium"
    private const val PRECO_PADRAO = 29.90

    suspend fun carregar(context: Context): InfoPlano {
        var nome = NOME_PADRAO
        var preco = PRECO_PADRAO
        var proxima: String? = null
        try {
            val sub = withContext(Dispatchers.IO) {
                val resp = RetrofitClient.subscriptionService.subscription().execute()
                if (resp.isSuccessful) resp.body() else null
            }
            if (sub?.plan != null && sub.status == "ACTIVE") {
                nome = sub.plan.name ?: nome
                preco = sub.plan.price ?: preco
                proxima = formatarDataIso(sub.currentPeriodEnd)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Endpoint indisponível: segue com o plano local de fallback.
        }
        val canceladaAte = ProfileStore(context).getString(CHAVE_CANCELADA, null)
        return InfoPlano(
            nome = nome,
            preco = preco,
            renovacao = context.getString(R.string.profile_monthly),
            proximaCobranca = canceladaAte ?: proxima ?: hojeMaisUmMes(),
            cartao = CartoesLocais(context).padrao(),
            cancelada = canceladaAte != null
        )
    }

    /** Marca a assinatura como cancelada localmente, com benefícios até [ate] (dd/MM/yyyy). */
    fun cancelar(context: Context, ate: String) = ProfileStore(context).putString(CHAVE_CANCELADA, ate)

    private fun hojeMaisUmMes(): String {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
        return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time)
    }
}

/** Preenche o card do plano (ids tvPlanoNome, tvPlanoPreco, tvPlanoValidade). */
fun View.preencherCardPlano(info: InfoPlano, beneficios: Boolean = false) {
    findViewById<TextView>(R.id.tvPlanoNome).text = info.nome
    findViewById<TextView>(R.id.tvPlanoPreco).text =
        context.getString(R.string.profile_per_month, formatarMoeda(info.preco))
    val textoValidade = if (info.cancelada || beneficios) R.string.profile_benefits_until else R.string.profile_valid_until
    findViewById<TextView>(R.id.tvPlanoValidade).text = context.getString(textoValidade, info.proximaCobranca)
}
