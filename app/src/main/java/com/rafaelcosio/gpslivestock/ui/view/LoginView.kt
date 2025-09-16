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
import com.rafaelcosio.gpslivestock.databinding.ActivityLoginBinding
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.ui.viewmodel.AuthUiState
import com.rafaelcosio.gpslivestock.ui.viewmodel.UnifiedAuthViewModel
import com.rafaelcosio.gpslivestock.utils.AppPreferences
import com.rafaelcosio.gpslivestock.data.model.User
import com.rafaelcosio.gpslivestock.utils.toSpanish
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginView : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: UnifiedAuthViewModel by viewModels()

    private var isRegisterMode = false
    private val selectableUserTypes: List<UserType> = UserType.entries.filter { it != UserType.ADMINISTRATOR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupListeners()
        observeUiState()
        updateUI()
    }

    private fun setupSpinner() {
        val userTypeDisplayNames = selectableUserTypes.map { it.toSpanish() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userTypeDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUserType.adapter = adapter

        binding.spinnerUserType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < selectableUserTypes.size) {
                    val selectedUserType = selectableUserTypes[position]
                    binding.tilRanchName.visibility = if (selectedUserType == UserType.RANCHER && isRegisterMode) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }

    private fun setupListeners() {
        binding.btnPrimary.setOnClickListener {
            if (isRegisterMode) {
                performRegister()
            } else {
                performLogin()
            }
        }

        binding.btnToggleMode.setOnClickListener {
            toggleMode()
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        authViewModel.login(email, password)
    }

    private fun performRegister() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val displayName = binding.etDisplayName.text.toString().trim()

        val selectedItemPosition = binding.spinnerUserType.selectedItemPosition
        if (selectedItemPosition >= 0 && selectedItemPosition < selectableUserTypes.size) {
            val selectedUserType = selectableUserTypes[selectedItemPosition]
            val ranchName = binding.etRanchName.text.toString().trim()

            authViewModel.register(
                email,
                password,
                confirmPassword,
                displayName.ifEmpty { null },
                selectedUserType,
                ranchName.ifEmpty { null }
            )
        } else {
            Toast.makeText(this, "Por favor, selecciona un tipo de usuario válido.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        updateUI()
        clearFields()
        authViewModel.resetState()
    }

    private fun updateUI() {
        if (isRegisterMode) {
            // Modo registro
            binding.tvTitle.text = "Crear Cuenta"
            binding.btnPrimary.text = "Registrarse"
            binding.btnToggleMode.text = "¿Ya tienes cuenta? Inicia sesión"

            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.tilDisplayName.visibility = View.VISIBLE
            binding.tvUserTypeLabel.visibility = View.VISIBLE
            binding.spinnerUserType.visibility = View.VISIBLE

            // Verificar si mostrar campo rancho
            val selectedPosition = binding.spinnerUserType.selectedItemPosition
            if (selectedPosition >= 0 && selectedPosition < selectableUserTypes.size) {
                val selectedUserType = selectableUserTypes[selectedPosition]
                binding.tilRanchName.visibility = if (selectedUserType == UserType.RANCHER) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        } else {
            // Modo login
            binding.tvTitle.text = "Bienvenido"
            binding.btnPrimary.text = "Iniciar Sesión"
            binding.btnToggleMode.text = "¿No tienes cuenta? Regístrate"

            binding.tilConfirmPassword.visibility = View.GONE
            binding.tilDisplayName.visibility = View.GONE
            binding.tvUserTypeLabel.visibility = View.GONE
            binding.spinnerUserType.visibility = View.GONE
            binding.tilRanchName.visibility = View.GONE
        }
    }

    private fun clearFields() {
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        binding.etDisplayName.text?.clear()
        binding.etRanchName.text?.clear()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            authViewModel.authUiState.collect { state ->
                when (state) {
                    is AuthUiState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.errorCard.visibility = View.GONE
                        binding.btnPrimary.isEnabled = true
                    }
                    is AuthUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.errorCard.visibility = View.GONE
                        binding.btnPrimary.isEnabled = false
                    }
                    is AuthUiState.LoginSuccess -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnPrimary.isEnabled = true
                        Toast.makeText(
                            this@LoginView,
                            "Login exitoso! Usuario: ${state.user.displayName ?: state.user.email}, Tipo: ${state.user.userType}",
                            Toast.LENGTH_LONG
                        ).show()

                        saveUserSession(state.user)
                        navigateToMainScreen()
                    }
                    is AuthUiState.RegisterSuccess -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnPrimary.isEnabled = true
                        Toast.makeText(this@LoginView, "Registro exitoso. Por favor, inicia sesión.", Toast.LENGTH_LONG).show()

                        // Cambiar automáticamente a modo login después del registro exitoso
                        isRegisterMode = false
                        updateUI()
                        clearFields()
                    }
                    is AuthUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = state.message
                        binding.errorCard.visibility = View.VISIBLE
                        binding.btnPrimary.isEnabled = true
                        Toast.makeText(this@LoginView, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveUserSession(user: User) {
        AppPreferences.saveUserSession(this, user)
    }

    private fun navigateToMainScreen() {
        val intent = Intent(this, MapsView::class.java) 
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}