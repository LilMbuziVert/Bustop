package com.heuge.busapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.BusStop
import com.heuge.busapp.data.model.BusStopGroup
import com.heuge.busapp.utils.enableEInkFeedback


class RecentStopsAdapter(
    private val onStopClick: (BusStop) -> Unit
) : RecyclerView.Adapter<RecentStopsAdapter.ViewHolder>() {

    private var stopGroups = listOf<BusStopGroup>()

    fun updateStops(stops: List<BusStop>) {
        stopGroups = stops.chunked(3).map { BusStopGroup(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_stops_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //holder.bind(stops[position])
        holder.bind(stopGroups[position])
    }

    //override fun getItemCount() = stops.size
    override fun getItemCount() = stopGroups.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val firstStopContainer: LinearLayout =
            itemView.findViewById(R.id.firstStopContainer)
        private val firstStopIdText: TextView = itemView.findViewById(R.id.firstStopIdText)
        private val firstStopNameText: TextView = itemView.findViewById(R.id.firstStopNameText)

        private val secondStopContainer: LinearLayout =
            itemView.findViewById(R.id.secondStopContainer)
        private val secondStopIdText: TextView = itemView.findViewById(R.id.secondStopIdText)
        private val secondStopNameText: TextView = itemView.findViewById(R.id.secondStopNameText)
        private val secondSeparator: View = itemView.findViewById(R.id.secondSeparator)

        private val thirdStopContainer: LinearLayout =
            itemView.findViewById(R.id.thirdStopContainer)
        private val thirdStopIdText: TextView = itemView.findViewById(R.id.thirdStopIdText)
        private val thirdStopNameText: TextView = itemView.findViewById(R.id.thirdStopNameText)

        init {
            // ✅ Apply touch feedback ONCE per container
            firstStopContainer.enableEInkFeedback()
            secondStopContainer.enableEInkFeedback()
            thirdStopContainer.enableEInkFeedback()
        }

        fun bind(stopGroup: BusStopGroup) {
            // First stop (always present)
            val firstStop = stopGroup.stops[0]
            firstStopIdText.text = firstStop.id
            firstStopNameText.text = firstStop.name ?: "Stop ${firstStop.id}"
            firstStopContainer.apply {
                setOnClickListener { onStopClick(firstStop) }
                //enableEInkFeedback()
            }

            // Second stop
            if (stopGroup.hasSecondStop) {
                val secondStop = stopGroup.stops[1]
                secondStopContainer.visibility = View.VISIBLE
                secondSeparator.visibility = View.VISIBLE
                secondStopIdText.text = secondStop.id
                secondStopNameText.text = secondStop.name ?: "Stop ${secondStop.id}"
                secondStopContainer.apply {
                    setOnClickListener { onStopClick(secondStop) }
                    //enableEInkFeedback()
                }
            } else {
                secondStopContainer.visibility = View.GONE
            }

            // Third stop
            if (stopGroup.hasThirdStop) {
                val thirdStop = stopGroup.stops[2]
                thirdStopContainer.visibility = View.VISIBLE
                thirdStopIdText.text = thirdStop.id
                thirdStopNameText.text = thirdStop.name ?: "Stop ${thirdStop.id}"
                thirdStopContainer.apply {
                    setOnClickListener { onStopClick(thirdStop) }
                    //enableEInkFeedback()
                }
            } else {
                thirdStopContainer.visibility = View.GONE
            }
        }
    }
}