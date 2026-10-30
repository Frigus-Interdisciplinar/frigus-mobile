package com.example.frigus_mobile.ui.qrcode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import java.text.NumberFormat
import java.util.Locale

class QrCodeProdutoAdapter(
    private val onIncludedChanged: (String, Boolean) -> Boolean,
    private val onQuantityChanged: (String, Int) -> Boolean,
    private val onExpirationRequested: (String) -> Unit
) : RecyclerView.Adapter<QrCodeProdutoAdapter.LineViewHolder>() {
    private var lines: List<ReceiptLineReview> = emptyList()

    fun updateList(value: List<ReceiptLineReview>) {
        lines = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder = LineViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_qr_code_produto, parent, false)
    )

    override fun onBindViewHolder(holder: LineViewHolder, position: Int) = holder.bind(lines[position])

    override fun getItemCount(): Int = lines.size

    inner class LineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.tvQrItemNome)
        private val description = view.findViewById<TextView>(R.id.tvQrItemDescricao)
        private val prices = view.findViewById<TextView>(R.id.tvQrItemPreco)
        private val details = view.findViewById<TextView>(R.id.tvQrItemDetails)
        private val evidence = view.findViewById<TextView>(R.id.tvQrItemEvidence)
        private val match = view.findViewById<TextView>(R.id.tvQrItemMatch)
        private val include = view.findViewById<CheckBox>(R.id.qrIncludeItem)
        private val controls = view.findViewById<View>(R.id.qrImportControls)
        private val quantity = view.findViewById<TextView>(R.id.tvQrItemQtd)
        private val decrease = view.findViewById<View>(R.id.btnDiminuirQtd)
        private val increase = view.findViewById<View>(R.id.btnAumentarQtd)
        private val expiration = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnQrExpireDate)

        fun bind(line: ReceiptLineReview) {
            val item = line.item
            val context = itemView.context
            name.text = item.normalizedProductName?.takeIf(String::isNotBlank)
                ?: item.description?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.receipt_item_unknown)
            description.text = item.description.orEmpty().takeIf(String::isNotBlank).orEmpty()
            description.visibility = if (description.text.isNullOrBlank()) View.GONE else View.VISIBLE
            prices.text = context.getString(
                R.string.receipt_item_prices,
                item.quantity?.let(::formatNumber) ?: context.getString(R.string.receipt_value_unavailable),
                item.unit?.takeIf(String::isNotBlank) ?: item.unitOfMeasure?.takeIf(String::isNotBlank)
                    ?: context.getString(R.string.receipt_value_unavailable),
                item.unitPrice?.let(::formatCurrency) ?: context.getString(R.string.receipt_value_unavailable),
                item.totalPrice?.let(::formatCurrency) ?: context.getString(R.string.receipt_value_unavailable)
            )
            details.text = context.getString(
                R.string.receipt_item_details,
                item.brand?.takeIf(String::isNotBlank) ?: context.getString(R.string.receipt_value_unavailable),
                item.category?.takeIf(String::isNotBlank) ?: context.getString(R.string.receipt_value_unavailable),
                item.storagePlace?.takeIf(String::isNotBlank) ?: context.getString(R.string.receipt_value_unavailable),
                item.unitOfMeasure?.takeIf(String::isNotBlank) ?: context.getString(R.string.receipt_value_unavailable),
                item.confidence?.takeIf { it in 0.0..1.0 }
                    ?.let { context.getString(R.string.receipt_confidence, (it * 100).toInt()) }
                    ?: context.getString(R.string.receipt_value_unavailable)
            )
            item.evidence?.takeIf(String::isNotBlank)?.let {
                evidence.text = context.getString(R.string.receipt_item_evidence, it)
                evidence.visibility = View.VISIBLE
            } ?: run { evidence.text = null; evidence.visibility = View.GONE }

            val productId = item.matchedProduct?.productId?.takeIf { it > 0 }
            val canImport = productId != null && line.quantity != null
            match.text = when {
                productId == null -> context.getString(R.string.receipt_item_unmatched)
                line.quantity == null -> context.getString(R.string.receipt_quantity_not_importable)
                else -> context.getString(R.string.receipt_item_match,
                    item.matchedProduct?.name?.takeIf(String::isNotBlank) ?: productId.toString())
            }
            include.setOnCheckedChangeListener(null)
            include.isChecked = line.include
            include.isEnabled = canImport
            include.setOnCheckedChangeListener { _, checked ->
                if (!onIncludedChanged(item.lineId.orEmpty(), checked)) {
                    include.setOnCheckedChangeListener(null)
                    include.isChecked = line.include
                    include.setOnCheckedChangeListener { _, next -> onIncludedChanged(item.lineId.orEmpty(), next) }
                }
            }
            controls.visibility = if (line.include) View.VISIBLE else View.GONE
            quantity.text = line.quantity?.toString() ?: context.getString(R.string.receipt_value_unavailable)
            decrease.isEnabled = line.quantity?.let { it > 1 } == true
            increase.isEnabled = line.quantity != null && line.quantity < Int.MAX_VALUE
            decrease.setOnClickListener { onQuantityChanged(item.lineId.orEmpty(), -1) }
            increase.setOnClickListener { onQuantityChanged(item.lineId.orEmpty(), 1) }
            expiration.text = line.expireDate?.let { context.getString(R.string.receipt_expiration_value, it) }
                ?: context.getString(R.string.receipt_expiration_choose)
            expiration.setOnClickListener { onExpirationRequested(item.lineId.orEmpty()) }
        }
    }

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    private fun formatNumber(value: Double): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        maximumFractionDigits = 3
    }.format(value)
}
