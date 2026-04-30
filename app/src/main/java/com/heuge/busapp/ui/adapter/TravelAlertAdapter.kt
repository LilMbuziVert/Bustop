package com.heuge.busapp.ui.adapter

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
        val title: TextView = view.findViewById(R.id.alertTitle)
        val content: TextView = view.findViewById(R.id.alertContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_travel_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        holder.title.text = alert.title
        holder.content.text = alert.content
    }

    override fun getItemCount() = alerts.size

    fun updateAlerts(newAlerts: List<TravelAlert>) {
        alerts = newAlerts
        notifyDataSetChanged()
    }
}
