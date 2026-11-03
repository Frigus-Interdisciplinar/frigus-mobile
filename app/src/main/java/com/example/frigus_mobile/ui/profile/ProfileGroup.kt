package com.example.frigus_mobile.ui.profile

import android.content.Context

/** Membro (ou convite pendente) do grupo, guardado localmente até existir endpoint no BFF. */
data class MembroGrupo(
    val nome: String,
    val administrador: Boolean,
    val pendente: Boolean,
    val gerenciaEstoque: Boolean
)

class GrupoLocal(context: Context) {
    private val store = ProfileStore(context)

    fun listar(): List<MembroGrupo> = store.getList(CHAVE).mapNotNull {
        val p = it.split("|")
        if (p.size < 4) null else MembroGrupo(p[0], p[1] == "1", p[2] == "1", p[3] == "1")
    }

    fun adicionar(membro: MembroGrupo) {
        val limpo = membro.copy(nome = membro.nome.replace("|", " ").replace("\n", " ").trim())
        store.putList(CHAVE, (listar() + limpo).map {
            "${it.nome}|${flag(it.administrador)}|${flag(it.pendente)}|${flag(it.gerenciaEstoque)}"
        })
    }

    private fun flag(valor: Boolean) = if (valor) "1" else "0"

    private companion object {
        const val CHAVE = "grupo_membros"
    }
}
