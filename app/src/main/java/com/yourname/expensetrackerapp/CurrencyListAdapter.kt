package com.yourname.expensetrackerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CurrencyListAdapter(
    private val items: List<Currency>,
    private val onClick: (Currency) -> Unit
) : RecyclerView.Adapter<CurrencyListAdapter.CurrencyViewHolder>() {

    class CurrencyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val symbol: TextView = itemView.findViewById(R.id.currencySymbolText)
        val name: TextView = itemView.findViewById(R.id.currencyNameText)
        val code: TextView = itemView.findViewById(R.id.currencyCodeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.currency_item, parent, false)
        return CurrencyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CurrencyViewHolder, position: Int) {
        val currency = items[position]
        holder.symbol.text = currency.symbol
        holder.name.text = currency.displayName
        holder.code.text = currency.code
        holder.itemView.setOnClickListener { onClick(currency) }
    }

    override fun getItemCount(): Int = items.size
}
