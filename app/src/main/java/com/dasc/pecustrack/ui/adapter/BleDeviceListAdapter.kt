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
    private val onItemClicked: (BleDevice) -> Unit
) : ListAdapter<BleDevice, BleDeviceListAdapter.DeviceViewHolder>(BleDeviceDiffCallback()) {

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

        fun bind(deviceItem: BleDevice) {
            nameTextView.text = deviceItem.resolvedName
            addressTextView.text = deviceItem.address
        }
    }

    class BleDeviceDiffCallback : DiffUtil.ItemCallback<BleDevice>() {
        override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem.resolvedName == newItem.resolvedName && oldItem.address == newItem.address
        }
    }
}