package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager

class MeuGrupoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_meu_grupo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener {
            abrirTela(ConvidarMembroFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        carregarMembros()
    }

    private fun carregarMembros() {
        val root = view ?: return
        val card = root.findViewById<LinearLayout>(R.id.cardMembrosGrupo)
        card.removeAllViews()

        val session = SessionManager(requireContext())
        val nomeAtual = session.userName?.ifBlank { null } ?: "Juliana"
        val grupoLocal = GrupoLocal(requireContext())
        val membrosSalvos = grupoLocal.listar()

        // Se ainda não houver membros salvos, pré-carrega os membros do mockup (João, Maria, Ana)
        val listaExibicao = if (membrosSalvos.isEmpty()) {
            listOf(
                MembroGrupo(getString(R.string.profile_you_suffix, nomeAtual), administrador = true, pendente = false, gerenciaEstoque = true),
                MembroGrupo("João", administrador = false, pendente = false, gerenciaEstoque = false),
                MembroGrupo("Maria", administrador = false, pendente = false, gerenciaEstoque = false),
                MembroGrupo("Ana", administrador = false, pendente = false, gerenciaEstoque = false)
            )
        } else {
            val lista = mutableListOf<MembroGrupo>()
            lista.add(MembroGrupo(getString(R.string.profile_you_suffix, nomeAtual), administrador = true, pendente = false, gerenciaEstoque = true))
            lista.addAll(membrosSalvos)
            lista
        }

        listaExibicao.forEachIndexed { index, membro ->
            val item = layoutInflater.inflate(R.layout.item_profile_family_member, card, false)
            item.findViewById<TextView>(R.id.tvMembroNome).text = membro.nome
            item.findViewById<TextView>(R.id.tvMembroFuncao).text = if (membro.administrador) {
                getString(R.string.profile_role_admin)
            } else {
                getString(R.string.profile_role_member)
            }
            card.addView(item)

            if (index < listaExibicao.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        2
                    ).apply {
                        setMargins(
                            32,
                            0,
                            32,
                            0
                        )
                    }
                    setBackgroundColor(resources.getColor(R.color.profile_divider, null))
                }
                card.addView(divider)
            }
        }
    }
}
