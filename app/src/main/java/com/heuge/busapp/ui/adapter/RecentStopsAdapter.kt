package com.heuge.busapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.BusStop

class RecentStopsAdapter(
    private val onStopClick: (BusStop) -> Unit
) : RecyclerView.Adapter<RecentStopsAdapter.ViewHolder>() {

    private var stops = listOf<BusStop>()

    fun updateStops(newStops: List<BusStop>) {
        stops = newStops
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_stop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stops[position])
    }

    override fun getItemCount() = stops.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stopIdText: TextView = itemView.findViewById(R.id.stopIdText)
        private val stopNameText: TextView = itemView.findViewById(R.id.stopNameText)

        fun bind(busStop: BusStop) {
            stopIdText.text = busStop.id
            stopNameText.text = busStop.name ?: "Stop ${busStop.id}"

            itemView.setOnClickListener {
                onStopClick(busStop)
            }
        }
    }
}