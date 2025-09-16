package com.rafaelcosio.gpslivestock.ui.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rafaelcosio.gpslivestock.databinding.ActivityRegisterBinding
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.ui.viewmodel.RegisterUiState
import com.rafaelcosio.gpslivestock.ui.viewmodel.RegisterViewModel
import com.rafaelcosio.gpslivestock.utils.toSpanish // <-- AÑADIDA IMPORTACIÓN
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// ELIMINADA LA FUNCIÓN DUPLICADA UserType.toSpanish() DE AQUÍ

@AndroidEntryPoint
class RegisterView : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val registerViewModel: RegisterViewModel by viewModels()

    // Guardar la lista filtrada de UserType para accederla fácilmente
    private val selectableUserTypes: List<UserType> = UserType.values().filter { it != UserType.ADMINISTRATOR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupListeners()
        observeUiState()
    }

    private fun setupSpinner() {
        // Usar la lista pre-filtrada 'selectableUserTypes' y mapear a sus nombres en español
        val userTypeDisplayNames = selectableUserTypes.map { it.toSpanish() } // Ahora usa la función importada

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userTypeDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUserType.adapter = adapter

        binding.spinnerUserType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Obtener el UserType original de nuestra lista filtrada 'selectableUserTypes'
                if (position < selectableUserTypes.size) { // Check bounds
                    val selectedUserType = selectableUserTypes[position]
                    binding.tilRegisterRanchName.visibility = if (selectedUserType == UserType.RANCHER) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { /* No action needed */ }
        }

        // Establecer visibilidad inicial del campo ranchName basada en la primera opción del spinner (si la lista no está vacía)
        if (selectableUserTypes.isNotEmpty()) {
            binding.tilRegisterRanchName.visibility = if (selectableUserTypes[0] == UserType.RANCHER) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else {
            binding.tilRegisterRanchName.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val email = binding.etRegisterEmail.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString()
            val confirmPassword = binding.etRegisterConfirmPassword.text.toString()
            val displayName = binding.etRegisterDisplayName.text.toString().trim()

            // Obtener el UserType original de 'selectableUserTypes' usando la posición del item seleccionado en el spinner
            val selectedItemPosition = binding.spinnerUserType.selectedItemPosition
            if (selectedItemPosition >= 0 && selectedItemPosition < selectableUserTypes.size) { // Check bounds
                val selectedUserType = selectableUserTypes[selectedItemPosition]
                val ranchName = binding.etRegisterRanchName.text.toString().trim()

                registerViewModel.registerUser(
                    email,
                    password,
                    confirmPassword,
                    displayName.ifEmpty { null },
                    selectedUserType, // Enviar el enum UserType original
                    ranchName.ifEmpty { null }
                )
            } else {
                Toast.makeText(this, "Por favor, selecciona un tipo de usuario válido.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            registerViewModel.registerUiState.collect { state ->
                when (state) {
                    is RegisterUiState.Idle -> {
                        binding.progressBarRegister.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                        binding.tvRegisterError.visibility = View.GONE
                    }
                    is RegisterUiState.Loading -> {
                        binding.progressBarRegister.visibility = View.VISIBLE
                        binding.btnRegister.isEnabled = false
                        binding.tvRegisterError.visibility = View.GONE
                    }
                    is RegisterUiState.Success -> {
                        binding.progressBarRegister.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                        Toast.makeText(this@RegisterView, "Registro exitoso. Por favor, inicia sesión.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@RegisterView, LoginView::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    is RegisterUiState.Error -> {
                        binding.progressBarRegister.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                        binding.tvRegisterError.text = state.message
                        binding.tvRegisterError.visibility = View.VISIBLE
                        Toast.makeText(this@RegisterView, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}