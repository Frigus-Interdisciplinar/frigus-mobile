package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.BillingTransaction
import com.google.android.material.button.MaterialButton

class HistoricoCobrancasFragment : SubscriptionScreenFragment(R.layout.fragment_historico_cobrancas) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        val adapter = TransactionsAdapter()
        view.findViewById<RecyclerView>(R.id.rvCobrancas).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        observeSubscription(view) { state ->
            adapter.items = state.transactions
            adapter.more = state.hasMoreTransactions
            adapter.loading = state.loading
            adapter.notifyDataSetChanged()
            view.findViewById<View>(R.id.progressCobrancas).visibility = if (state.loading) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.tvCobrancasVazio).visibility =
                if (!state.loading && state.transactions.isEmpty() && state.error == null) View.VISIBLE else View.GONE
            view.findViewById<TextView>(R.id.tvCobrancasErro).apply {
                visibility = if (state.error != null) View.VISIBLE else View.GONE
                text = state.error?.userMessage(getString(R.string.subscription_load_error)).orEmpty()
            }
        }
    }

    private inner class TransactionsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items = emptyList<BillingTransaction>()
        var more = false
        var loading = false
        override fun getItemCount() = items.size + if (more) 1 else 0
        override fun getItemViewType(position: Int) = if (position == items.size) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = if (viewType == 1) MaterialButton(requireContext()).apply {
                setText(R.string.subscription_more_transactions)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener { model.loadMoreTransactions() }
            } else layoutInflater.inflate(R.layout.item_profile_billing_charge, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position == items.size) { holder.itemView.isEnabled = !loading; return }
            val item = items[position]
            holder.itemView.findViewById<TextView>(R.id.tvCobrancaPlano).text =
                getString(R.string.subscription_member, value(item.planCode), value(item.paymentMethod))
            holder.itemView.findViewById<TextView>(R.id.tvCobrancaData).text = date(item.createdAt)
            holder.itemView.findViewById<TextView>(R.id.tvCobrancaValor).text = amount(item.amount)
            holder.itemView.findViewById<TextView>(R.id.tvCobrancaStatus).text = value(item.status)
        }
    }
}
