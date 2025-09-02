package com.heuge.busapp.ui.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.BusArrival

class BusArrivalAdapter(private var arrivals: List<BusArrival>) :
    RecyclerView.Adapter<BusArrivalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val routeNumber: TextView = view.findViewById(R.id.routeNumber)
        val destination: TextView = view.findViewById(R.id.destination)
        val arrivalTime: TextView = view.findViewById(R.id.arrivalTime)
        val delayStatus: TextView = view.findViewById(R.id.delayStatus)
        val separatorLine: View = view.findViewById(R.id.separatorLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_arrival_eink, parent, false)  // Use eink-optimized layout
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val arrival = arrivals[position]

        // Use high contrast text fields (you can customize text appearance in XML)
        holder.routeNumber.text = arrival.routeName
        holder.destination.text = buildString {
            append("To ")
            append(arrival.destination)
        }

        // Use formatted time from data class
        holder.arrivalTime.text = arrival.getFormattedTime()

        holder.delayStatus.text = arrival.delayStatus

        when {
            arrival.delayMinutes > 0 -> holder.delayStatus.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_red_dark)
            )
            arrival.delayMinutes < 0 -> holder.delayStatus.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_green_dark)
            )
            else -> {
                // Get theme attribute color for "on time" text
                val typedValue = android.util.TypedValue()
                holder.itemView.context.theme.resolveAttribute(
                    R.attr.appOnBackground,
                    typedValue,
                    true
                )
                holder.delayStatus.setTextColor(typedValue.data)
            }
        }

        // Visual separator between items, hide on last item
        holder.separatorLine.visibility = if (position == arrivals.size - 1) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = arrivals.size

    fun updateArrivals(newArrivals: List<BusArrival>) {
        arrivals = newArrivals
        notifyDataSetChanged()
    }
}
