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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_arrival, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val arrival = arrivals[position]
        holder.routeNumber.text = arrival.routeName
        holder.destination.text = "To ${arrival.destination}"
        holder.arrivalTime.text = arrival.getFormattedTime()
    }

    override fun getItemCount() = arrivals.size

    fun updateArrivals(newArrivals: List<BusArrival>) {
        arrivals = newArrivals
        notifyDataSetChanged()
    }
}