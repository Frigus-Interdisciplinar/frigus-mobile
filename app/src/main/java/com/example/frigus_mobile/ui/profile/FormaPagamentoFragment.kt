package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class FormaPagamentoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_forma_pagamento, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<View>(R.id.btnAdicionarCartao).setOnClickListener { abrirTela(AdicionarCartaoFragment()) }
    }

    override fun onResume() {
        super.onResume()
        carregarCartoes()
    }

    private fun carregarCartoes() {
        val raiz = view ?: return
        val lista = raiz.findViewById<LinearLayout>(R.id.listaCartoes)
        val cartoes = CartoesLocais(requireContext()).listar()
        lista.removeAllViews()
        raiz.findViewById<View>(R.id.tvCartoesVazio).visibility = if (cartoes.isEmpty()) View.VISIBLE else View.GONE

        cartoes.forEach { cartao ->
            val item = layoutInflater.inflate(R.layout.item_profile_payment_card, lista, false)
            val mastercard = cartao.bandeira == "Mastercard"
            item.findViewById<ImageView>(R.id.ivCartaoBandeira).visibility = if (mastercard) View.VISIBLE else View.GONE
            item.findViewById<TextView>(R.id.tvCartaoBandeira).apply {
                visibility = if (mastercard) View.GONE else View.VISIBLE
                text = cartao.bandeira
            }
            item.findViewById<TextView>(R.id.tvCartaoNumero).text = "•••• ${cartao.ultimos4}"
            item.findViewById<View>(R.id.tvCartaoPadrao).visibility = if (cartao.padrao) View.VISIBLE else View.GONE
            item.findViewById<View>(R.id.ivCartaoSeta).visibility = if (cartao.padrao) View.GONE else View.VISIBLE
            if (!cartao.padrao) item.setOnClickListener { mostrarOpcoes(cartao) }
            lista.addView(item)
        }
    }

    private fun mostrarOpcoes(cartao: CartaoSalvo) {
        val opcoes = arrayOf(getString(R.string.profile_card_set_default), getString(R.string.profile_card_remove))
        AlertDialog.Builder(requireContext())
            .setTitle("${cartao.bandeira} •••• ${cartao.ultimos4}")
            .setItems(opcoes) { _, qual ->
                val cartoes = CartoesLocais(requireContext())
                if (qual == 0) {
                    cartoes.definirPadrao(cartao)
                } else {
                    cartoes.remover(cartao)
                    mostrarToast(getString(R.string.profile_card_removed))
                }
                carregarCartoes()
            }
            .show()
    }
}
