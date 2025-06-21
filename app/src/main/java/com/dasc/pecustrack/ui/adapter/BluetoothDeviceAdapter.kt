package com.dasc.pecustrack.ui.adapter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dasc.pecustrack.R

class BluetoothDeviceAdapter(
    private val onItemClicked: (BluetoothDevice) -> Unit
) : ListAdapter<BluetoothDevice, BluetoothDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
        holder.itemView.setOnClickListener {
            onItemClicked(device)
        }
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewDeviceName)
        private val addressTextView: TextView = itemView.findViewById(R.id.textViewDeviceAddress)

        fun bind(device: BluetoothDevice) {
            // Es crucial verificar el permiso BLUETOOTH_CONNECT antes de acceder a device.name
            if (ActivityCompat.checkSelfPermission(
                    itemView.context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                nameTextView.text = device.name ?: "Dispositivo Desconocido"
            } else {
                // Si no hay permiso, solo puedes mostrar la dirección o un placeholder.
                // Considera solicitar el permiso o informar al usuario.
                nameTextView.text = "Nombre no disponible (sin permiso)"
            }
            addressTextView.text = device.address
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
        override fun areItemsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            // La dirección MAC es un identificador único para el dispositivo físico.
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            // Dado que el nombre puede aparecer o cambiar después del descubrimiento inicial,
            // y obtenerlo de forma segura dentro de DiffUtil es complicado sin contexto
            // para la verificación de permisos, podemos hacer una suposición.
            // Si el nombre es crucial para ti aquí, considera la Opción 2.

            // Una comparación simple podría ser:
            // return oldItem.address == newItem.address && oldItem.name == newItem.name
            // PERO, oldItem.name y newItem.name podrían lanzar SecurityException si no tienes
            // BLUETOOTH_CONNECT.

            // La forma más segura y simple aquí, si no quieres introducir un wrapper,
            // es asumir que si la dirección es la misma, los contenidos "esenciales"
            // para DiffUtil en este punto son los mismos, y dejar que el ViewHolder
            // se preocupe por mostrar el nombre más actualizado cuando se llame a onBindViewHolder.
            // O, si sabes que *siempre* tendrás permiso en el punto de actualización de la lista,
            // podrías arriesgarte, pero no es lo ideal.

            // Solución pragmática y segura para DiffUtil:
            // Compara los campos que puedes acceder de forma segura y consistente.
            // Si el nombre se obtiene de forma asíncrona, el ViewHolder lo actualizará.
            val oldName = try { oldItem.name } catch (_: SecurityException) { null }
            val newName = try { newItem.name } catch (_: SecurityException) { null }

            return oldItem.address == newItem.address && oldName == newName
        }
    }
}