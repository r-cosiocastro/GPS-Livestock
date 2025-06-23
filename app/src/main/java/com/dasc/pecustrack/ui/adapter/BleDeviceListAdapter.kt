package com.dasc.pecustrack.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dasc.pecustrack.R

class BleDeviceListAdapter(
    private val onItemClicked: (DiscoveredDeviceInfo) -> Unit
) : ListAdapter<DiscoveredDeviceInfo, BleDeviceListAdapter.DeviceViewHolder>(BleDeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false) // Asegúrate de tener este layout
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val deviceItem = getItem(position)
        holder.bind(deviceItem)
        holder.itemView.setOnClickListener {
            onItemClicked(deviceItem)
        }
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewDeviceName)
        private val addressTextView: TextView = itemView.findViewById(R.id.textViewDeviceAddress)

        fun bind(deviceItem: DiscoveredDeviceInfo) {
            nameTextView.text = deviceItem.displayName
            addressTextView.text = deviceItem.address
        }
    }

    class BleDeviceDiffCallback : DiffUtil.ItemCallback<DiscoveredDeviceInfo>() {
        override fun areItemsTheSame(oldItem: DiscoveredDeviceInfo, newItem: DiscoveredDeviceInfo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiscoveredDeviceInfo, newItem: DiscoveredDeviceInfo): Boolean {
            return oldItem.displayName == newItem.displayName && oldItem.address == newItem.address
        }
    }
}