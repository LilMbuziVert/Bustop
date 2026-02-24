package com.heuge.busapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.heuge.busapp.R
import com.heuge.busapp.data.model.BusStop
import com.heuge.busapp.data.model.BusStopGroup


class RecentStopsAdapter(
    private val onStopClick: (BusStop) -> Unit,
    private val onDeleteClick: (BusStop) -> Unit
) : RecyclerView.Adapter<RecentStopsAdapter.ViewHolder>() {

    private var stopGroups = listOf<BusStopGroup>()
    private var deleteVisibleStopId: String? = null

    fun updateStops(stops: List<BusStop>) {
        stopGroups = stops.chunked(3).map { BusStopGroup(it) }
        notifyDataSetChanged()
    }

    fun hideDeleteButtons() {
        if (deleteVisibleStopThrown()) {
            deleteVisibleStopId = null
            notifyDataSetChanged()
        }
    }
    
    private fun deleteVisibleStopIdNotNull(): Boolean = deleteVisibleStopId != null
    
    private fun deleteVisibleStopThrown(): Boolean = deleteVisibleStopId != null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_stops_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stopGroups[position])
    }

    override fun getItemCount() = stopGroups.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val firstStopContainer: LinearLayout = itemView.findViewById(R.id.firstStopContainer)
        private val firstStopIdText: TextView = itemView.findViewById(R.id.firstStopIdText)
        private val firstStopNameText: TextView = itemView.findViewById(R.id.firstStopNameText)
        private val firstDeleteButton: ImageButton = itemView.findViewById(R.id.firstDeleteButton)

        private val secondStopFrame: FrameLayout = itemView.findViewById(R.id.secondStopFrame)
        private val secondStopContainer: LinearLayout = itemView.findViewById(R.id.secondStopContainer)
        private val secondStopIdText: TextView = itemView.findViewById(R.id.secondStopIdText)
        private val secondStopNameText: TextView = itemView.findViewById(R.id.secondStopNameText)
        private val secondSeparator: View = itemView.findViewById(R.id.secondSeparator)
        private val secondDeleteButton: ImageButton = itemView.findViewById(R.id.secondDeleteButton)

        private val thirdStopFrame: FrameLayout = itemView.findViewById(R.id.thirdStopFrame)
        private val thirdStopContainer: LinearLayout = itemView.findViewById(R.id.thirdStopContainer)
        private val thirdStopIdText: TextView = itemView.findViewById(R.id.thirdStopIdText)
        private val thirdStopNameText: TextView = itemView.findViewById(R.id.thirdStopNameText)
        private val thirdDeleteButton: ImageButton = itemView.findViewById(R.id.thirdDeleteButton)

        fun bind(stopGroup: BusStopGroup) {
            // First stop
            val firstStop = stopGroup.stops[0]
            bindStop(firstStop, firstStopContainer, firstStopIdText, firstStopNameText, firstDeleteButton)

            // Second stop
            if (stopGroup.hasSecondStop) {
                val secondStop = stopGroup.stops[1]
                secondStopFrame.visibility = View.VISIBLE
                secondSeparator.visibility = View.VISIBLE
                bindStop(secondStop, secondStopContainer, secondStopIdText, secondStopNameText, secondDeleteButton)
            } else {
                secondStopFrame.visibility = View.GONE
                secondSeparator.visibility = View.GONE
            }

            // Third stop
            if (stopGroup.hasThirdStop) {
                val thirdStop = stopGroup.stops[2]
                thirdStopFrame.visibility = View.VISIBLE
                bindStop(thirdStop, thirdStopContainer, thirdStopIdText, thirdStopNameText, thirdDeleteButton)
            } else {
                thirdStopFrame.visibility = View.GONE
            }
        }

        private fun bindStop(
            stop: BusStop,
            container: LinearLayout,
            idText: TextView,
            nameText: TextView,
            deleteButton: ImageButton
        ) {
            idText.text = stop.signId ?: stop.id
            nameText.text = stop.name ?: "Stop ${stop.id}"
            
            deleteButton.visibility = if (deleteVisibleStopId == stop.id) View.VISIBLE else View.GONE

            container.setOnClickListener {
                if (deleteVisibleStopId != null) {
                    deleteVisibleStopId = null
                    notifyDataSetChanged()
                } else {
                    onStopClick(stop)
                }
            }

            container.setOnLongClickListener {
                deleteVisibleStopId = stop.id
                notifyDataSetChanged()
                true
            }

            deleteButton.setOnClickListener {
                deleteVisibleStopId = null
                onDeleteClick(stop)
            }
        }
    }
}