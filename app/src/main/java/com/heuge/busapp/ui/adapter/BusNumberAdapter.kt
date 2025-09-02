package com.heuge.busapp.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R

class BusNumberAdapter(
    private var busNumbers: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<BusNumberAdapter.BusNumberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusNumberViewHolder {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_number, parent, false) as TextView
        return BusNumberViewHolder(tv)
    }


    private var selectedPosition = 0

    inner class BusNumberViewHolder(val textView: TextView) :
        RecyclerView.ViewHolder(textView) {
        fun bind(busNumber: String, isSelected: Boolean) {
            textView.text = busNumber
            textView.isSelected = isSelected
            textView.setBackgroundResource(
                if (isSelected) R.drawable.chip_selected else R.drawable.chip_unselected
            )
            textView.setOnClickListener {
                val oldPos = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onClick(busNumber)
            }
        }
    }


    override fun onBindViewHolder(holder: BusNumberViewHolder, position: Int) {
        holder.bind(busNumbers[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = busNumbers.size

    fun updateData(newBusNumbers: List<String>) {
        busNumbers = newBusNumbers
        selectedPosition = 0
        notifyDataSetChanged()
    }
}
