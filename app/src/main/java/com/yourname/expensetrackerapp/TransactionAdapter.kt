package com.yourname.expensetrackerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** [onEditClick] is optional — Home's recent-transactions preview passes none and the edit
 *  button stays hidden there; only Transaction History wires it up. [onSettleToggle] is likewise
 *  optional but both Home and History wire it up since marking a debt settled is core to what
 *  debt tracking is for. */
class TransactionAdapter(
    private val items: MutableList<Transaction>,
    private val onDeleteClick: (Int) -> Unit,
    private val onEditClick: ((Int) -> Unit)? = null,
    private val onSettleToggle: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val description: TextView = itemView.findViewById(R.id.Description)
        val date: TextView = itemView.findViewById(R.id.Date)
        val category: TextView = itemView.findViewById(R.id.Category)
        val amount: TextView = itemView.findViewById(R.id.Amount)
        val debtMeta: TextView = itemView.findViewById(R.id.DebtMeta)
        val settledCheckBox: CheckBox = itemView.findViewById(R.id.SettledCheckBox)
        val editButton: ImageButton = itemView.findViewById(R.id.Edit)
        val deleteButton: ImageButton = itemView.findViewById(R.id.Delete)
    }

    private val transactionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = items[position]
        val context = holder.itemView.context
        holder.description.text = transaction.description.ifBlank {
            transaction.category ?: transaction.type
        }
        holder.date.text = transaction.date
        holder.amount.text = CurrencyFormatter.format(context, transaction.amount)

        when (transaction.type) {
            "Income" -> {
                holder.category.text = transaction.category ?: ""
                holder.amount.setTextColor(context.getColor(R.color.income))
                holder.debtMeta.visibility = View.GONE
                holder.settledCheckBox.visibility = View.GONE
            }
            "Expense" -> {
                holder.category.text = transaction.category ?: ""
                holder.amount.setTextColor(context.getColor(R.color.expense))
                holder.debtMeta.visibility = View.GONE
                holder.settledCheckBox.visibility = View.GONE
            }
            else -> bindDebt(holder, transaction, context)
        }

        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onDeleteClick(adapterPosition)
            }
        }

        if (onEditClick != null) {
            holder.editButton.visibility = View.VISIBLE
            holder.editButton.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick.invoke(adapterPosition)
                }
            }
        } else {
            holder.editButton.visibility = View.GONE
        }
    }

    private fun bindDebt(holder: TransactionViewHolder, transaction: Transaction, context: android.content.Context) {
        val isPayable = transaction.category == "Payable"
        holder.category.text = if (isPayable) context.getString(R.string.radio_payable) else context.getString(R.string.radio_receivable)
        val directionColor = if (isPayable) R.color.expense else R.color.income
        holder.amount.setTextColor(context.getColor(directionColor))

        val overdue = isOverdue(transaction)
        holder.debtMeta.visibility = View.VISIBLE
        val personLabel = if (isPayable) {
            context.getString(R.string.label_payable_to, transaction.personName ?: "")
        } else {
            context.getString(R.string.label_receivable_from, transaction.personName ?: "")
        }
        val dueLabel = transaction.dueDate?.let { context.getString(R.string.label_due, it) } ?: ""
        holder.debtMeta.text = "$personLabel  •  $dueLabel"
        holder.debtMeta.setTextColor(
            if (overdue) context.getColor(R.color.expense) else context.getColor(R.color.on_surface_variant)
        )

        if (onSettleToggle != null) {
            holder.settledCheckBox.visibility = View.VISIBLE
            holder.settledCheckBox.setOnCheckedChangeListener(null)
            holder.settledCheckBox.isChecked = transaction.isSettled
            holder.settledCheckBox.text = context.getString(R.string.label_settled)
            holder.settledCheckBox.setOnCheckedChangeListener { _, _ ->
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onSettleToggle.invoke(adapterPosition)
                }
            }
        } else {
            holder.settledCheckBox.visibility = View.GONE
        }
    }

    private fun isOverdue(transaction: Transaction): Boolean {
        if (transaction.isSettled) return false
        val due = try { transactionDateFormat.parse(transaction.dueDate ?: return false) } catch (e: Exception) { null } ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return due.before(today)
    }

    override fun getItemCount(): Int = items.size
}
