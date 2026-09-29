package com.yourname.expensetrackerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val items: List<NotificationEntry>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val timestampFormat = SimpleDateFormat("dd/MM/yyyy 'at' hh:mm a", Locale.getDefault())

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.notifTitle)
        val message: TextView = itemView.findViewById(R.id.notifMessage)
        val timestamp: TextView = itemView.findViewById(R.id.notifTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val entry = items[position]
        holder.title.text = entry.title
        holder.message.text = entry.message
        holder.timestamp.text = timestampFormat.format(Date(entry.timestampMillis))
    }

    override fun getItemCount(): Int = items.size
}
