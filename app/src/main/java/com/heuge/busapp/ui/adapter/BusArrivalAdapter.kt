package com.heuge.busapp.ui.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.BusArrival

class BusArrivalAdapter(
    private var arrivals: List<BusArrival>,
    private val onLoadEarlierClick: () -> Unit,
    private val onDismissEarlierClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LOAD_EARLIER = 0
        private const val VIEW_TYPE_ARRIVAL = 1
    }

    private var hasEarlierArrivals: Boolean = false

    class ArrivalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val routeNumber: TextView = view.findViewById(R.id.routeNumber)
        val destination: TextView = view.findViewById(R.id.destination)
        val arrivalTime: TextView = view.findViewById(R.id.arrivalTime)
        val delayStatus: TextView = view.findViewById(R.id.delayStatus)
        val separatorLine: View = view.findViewById(R.id.separatorLine)
    }

    class LoadEarlierViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val loadEarlierButton: TextView = view.findViewById(R.id.loadEarlierButton)
        val dismissEarlierButton: TextView = view.findViewById(R.id.dismissEarlierButton)
        val separator: View = view.findViewById(R.id.earlierArrivalsSeparator)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_LOAD_EARLIER else VIEW_TYPE_ARRIVAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_LOAD_EARLIER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_load_earlier, parent, false)
            LoadEarlierViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bus_arrival_eink, parent, false)
            ArrivalViewHolder(view)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is LoadEarlierViewHolder) {
            holder.loadEarlierButton.setOnClickListener { onLoadEarlierClick() }
            holder.dismissEarlierButton.setOnClickListener { onDismissEarlierClick() }
            
            // Only show dismiss button if we actually have earlier arrivals
            if (hasEarlierArrivals) {
                holder.dismissEarlierButton.visibility = View.VISIBLE
                holder.separator.visibility = View.VISIBLE
            } else {
                holder.dismissEarlierButton.visibility = View.GONE
                holder.separator.visibility = View.GONE
            }
        } else if (holder is ArrivalViewHolder) {
            val arrival = arrivals[position - 1]

            holder.routeNumber.text = arrival.routeName
            holder.destination.text = buildString {
                append("To ")
                append(arrival.destination)
            }

            holder.arrivalTime.text = arrival.getFormattedTime()
            holder.delayStatus.text = arrival.delayStatus

            if (arrival.isPast) {
                holder.itemView.alpha = 0.5f
                holder.delayStatus.visibility = View.GONE
            } else {
                holder.itemView.alpha = 1.0f
                holder.delayStatus.visibility = View.VISIBLE
                
                when {
                    arrival.delayMinutes > 0 -> {
                        val typedValue = android.util.TypedValue()
                        holder.itemView.context.theme.resolveAttribute(R.attr.delayLateColor, typedValue, true)
                        holder.delayStatus.setTextColor(typedValue.data)
                    }
                    arrival.delayMinutes < 0 -> {
                        val typedValue = android.util.TypedValue()
                        holder.itemView.context.theme.resolveAttribute(R.attr.delayEarlyColor, typedValue, true)
                        holder.delayStatus.setTextColor(typedValue.data)
                    }
                    else -> {
                        val typedValue = android.util.TypedValue()
                        holder.itemView.context.theme.resolveAttribute(R.attr.appOnBackground, typedValue, true)
                        holder.delayStatus.setTextColor(typedValue.data)
                    }
                }
            }

            holder.separatorLine.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
        }
    }

    override fun getItemCount(): Int {
        return if (arrivals.isEmpty()) 0 else arrivals.size + 1
    }

    fun updateArrivals(newArrivals: List<BusArrival>, hasEarlier: Boolean = false) {
        val wasEmpty = this.arrivals.isEmpty()
        val isNowEmpty = newArrivals.isEmpty()

        this.hasEarlierArrivals = hasEarlier

        if (wasEmpty && !isNowEmpty) {
            this.arrivals = newArrivals
            notifyDataSetChanged()
            return
        }
        if (!wasEmpty && isNowEmpty) {
            this.arrivals = newArrivals
            notifyDataSetChanged()
            return
        }

        this.arrivals = newArrivals
        notifyDataSetChanged()
    }

    private class BusArrivalDiffCallback(
        private val oldList: List<BusArrival>,
        private val newList: List<BusArrival>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.realTimeTime == newItem.realTimeTime && 
                   oldItem.routeName == newItem.routeName &&
                   oldItem.destination == newItem.destination
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
