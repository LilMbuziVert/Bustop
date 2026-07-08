package com.heuge.busapp.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.TravelAlert

class TravelAlertAdapter(private var alerts: List<TravelAlert>) :
    RecyclerView.Adapter<TravelAlertAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.alertLabel)
        val date: TextView = view.findViewById(R.id.alertDate)
        val title: TextView = view.findViewById(R.id.alertTitle)
        val affectedLines: TextView = view.findViewById(R.id.affectedLines)
        val link: TextView = view.findViewById(R.id.alertLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_travel_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        
        // Label (IMPORTANT / TRACKWORK)
        holder.label.text = alert.priority ?: "ALERT"
        
        // Date
        if (!alert.dateRange.isNullOrEmpty()) {
            holder.date.visibility = View.VISIBLE
            holder.date.text = alert.dateRange
        } else {
            holder.date.visibility = View.GONE
        }

        // Title
        holder.title.text = alert.title

        // Sydney Metro, Sydney Trains, etc.
        holder.affectedLines.text = alert.affectedLines ?: "Multiple Lines"

        // Link handling
        holder.link.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.url))
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = alerts.size

    fun updateAlerts(newAlerts: List<TravelAlert>) {
        alerts = newAlerts
        notifyDataSetChanged()
    }
}
