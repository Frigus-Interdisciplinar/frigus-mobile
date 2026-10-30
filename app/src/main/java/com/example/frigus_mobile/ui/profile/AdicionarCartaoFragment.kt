package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import java.util.Calendar

class AdicionarCartaoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_adicionar_cartao, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val tvPreviewNumero = view.findViewById<TextView>(R.id.tvPreviewNumero)
        val tvPreviewBandeira = view.findViewById<TextView>(R.id.tvPreviewBandeira)
        val etNome = view.findViewById<EditText>(R.id.etNomeCartao)
        val etNumero = view.findViewById<EditText>(R.id.etNumeroCartao)
        val etValidade = view.findViewById<EditText>(R.id.etValidadeCartao)
        val etCvv = view.findViewById<EditText>(R.id.etCvvCartao)
        val etCpf = view.findViewById<EditText>(R.id.etCpfCartao)
        val cbPrincipal = view.findViewById<CheckBox>(R.id.cbCartaoPrincipal)
        val btnSalvar = view.findViewById<View>(R.id.btnSalvarCartao)

        // Formatação e preview do número do cartão
        etNumero.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().replace(" ", "").filter { it.isDigit() }
                val formatted = digits.chunked(4).joinToString(" ")
                if (formatted != s.toString()) {
                    s?.replace(0, s.length, formatted)
                }

                val ultimos = if (digits.length >= 4) digits.takeLast(4) else ""
                tvPreviewNumero.text = if (ultimos.isNotEmpty()) "•••• $ultimos" else "•••• ••••"
                tvPreviewBandeira.text = detectarBandeira(digits)
                isFormatting = false
            }
        })

        // Máscara MM/AA na validade
        etValidade.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().replace("/", "").filter { it.isDigit() }
                val formatted = if (digits.length >= 3) {
                    "${digits.substring(0, 2)}/${digits.substring(2, minOf(digits.length, 4))}"
                } else digits
                if (formatted != s.toString()) {
                    s?.replace(0, s.length, formatted)
                }
                isFormatting = false
            }
        })

        // Máscara CPF 000.000.000-00
        etCpf.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().filter { it.isDigit() }
                val sb = StringBuilder()
                for (i in digits.indices) {
                    if (i == 3 || i == 6) sb.append('.')
                    if (i == 9) sb.append('-')
                    if (i < 11) sb.append(digits[i])
                }
                val formatted = sb.toString()
                if (formatted != s.toString()) {
                    s?.replace(0, s.length, formatted)
                }
                isFormatting = false
            }
        })

        btnSalvar.setOnClickListener {
            val nome = etNome.text.toString().trim()
            val numeroLimpo = etNumero.text.toString().replace(" ", "").filter { it.isDigit() }
            val validade = etValidade.text.toString().trim()
            val cvv = etCvv.text.toString().trim()
            val cpfLimpo = etCpf.text.toString().filter { it.isDigit() }

            var valido = true
            if (nome.length < 3) {
                etNome.error = "Informe o nome impresso no cartão"
                valido = false
            } else {
                etNome.error = null
            }

            if (numeroLimpo.length < 13 || !validarLuhn(numeroLimpo)) {
                etNumero.error = "Número de cartão inválido"
                valido = false
            } else {
                etNumero.error = null
            }

            if (!validarValidade(validade)) {
                etValidade.error = "Data inválida (MM/AA)"
                valido = false
            } else {
                etValidade.error = null
            }

            if (cvv.length < 3) {
                etCvv.error = "CVV inválido"
                valido = false
            } else {
                etCvv.error = null
            }

            if (cpfLimpo.length != 11) {
                etCpf.error = "CPF inválido"
                valido = false
            } else {
                etCpf.error = null
            }

            if (!valido) return@setOnClickListener

            // Segurança: nunca persistir o número completo, CVV ou CPF.
            val bandeira = detectarBandeira(numeroLimpo)
            val ultimos4 = numeroLimpo.takeLast(4)
            val novoCartao = CartaoSalvo(
                bandeira = bandeira,
                ultimos4 = ultimos4,
                nome = nome,
                validade = validade,
                padrao = cbPrincipal.isChecked
            )

            CartoesLocais(requireContext()).adicionar(novoCartao, cbPrincipal.isChecked)
            mostrarToast("Cartão adicionado com sucesso")
            voltarTela()
        }
    }

    private fun validarLuhn(numero: String): Boolean {
        var soma = 0
        var alternar = false
        for (i in numero.length - 1 downTo 0) {
            var n = numero[i].digitToInt()
            if (alternar) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            soma += n
            alternar = !alternar
        }
        return (soma % 10 == 0)
    }

    private fun validarValidade(validade: String): Boolean {
        if (!validade.matches(Regex("""^(0[1-9]|1[0-2])\/\d{2}$"""))) return false
        val partes = validade.split("/")
        val mes = partes[0].toIntOrNull() ?: return false
        val ano = (partes[1].toIntOrNull() ?: return false) + 2000

        val calAtual = Calendar.getInstance()
        val anoAtual = calAtual.get(Calendar.YEAR)
        val mesAtual = calAtual.get(Calendar.MONTH) + 1

        return if (ano > anoAtual) true else ano == anoAtual && mes >= mesAtual
    }
}
