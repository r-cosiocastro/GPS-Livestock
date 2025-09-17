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
import com.rafaelcosio.gpslivestock.R
import com.rafaelcosio.gpslivestock.data.model.FirebaseUserProfile
import com.rafaelcosio.gpslivestock.databinding.ActivityLoginBinding
import com.rafaelcosio.gpslivestock.data.model.UserType
import com.rafaelcosio.gpslivestock.ui.viewmodel.AuthViewModel
import com.rafaelcosio.gpslivestock.ui.viewmodel.AuthUiState
import com.rafaelcosio.gpslivestock.utils.AppPreferences
import com.rafaelcosio.gpslivestock.utils.LoadingDialog
import com.rafaelcosio.gpslivestock.utils.toSpanish
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginView : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var loadingDialog: LoadingDialog

    private var isRegisterMode = false
    private val selectableUserTypes: List<UserType> = UserType.entries.filter { it != UserType.ADMINISTRATOR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupListeners()
        setupDialogs()
        observeUiState()
        observeCurrentUser()
        updateUI()
    }

    private fun setupDialogs(){
        loadingDialog = LoadingDialog(this@LoginView)
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
        // Usar btnPrimary que es el ID actual en el XML
        binding.btnPrimary.setOnClickListener {
            if (isRegisterMode) {
                handleRegister()
            } else {
                handleLogin()
            }
        }

        binding.btnToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUI()
            authViewModel.clearMessages()
            clearFields()
        }
    }

    private fun handleLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (validateLoginFields(email, password)) {
            authViewModel.signIn(email, password)
        }
    }

    private fun handleRegister() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val displayName = binding.etDisplayName.text.toString().trim()
        val userType = selectableUserTypes.getOrNull(binding.spinnerUserType.selectedItemPosition) ?: UserType.REGULAR_USER
        val ranchName = binding.etRanchName.text.toString().trim().ifBlank { null }

        if (validateRegisterFields(email, password, confirmPassword, displayName)) {
            authViewModel.signUp(email, password, confirmPassword, displayName, userType, ranchName)
        }
    }

    private fun validateLoginFields(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            showError("El email es requerido")
            isValid = false
        }
        if (password.isEmpty()) {
            showError("La contraseña es requerida")
            isValid = false
        }

        return isValid
    }

    private fun validateRegisterFields(email: String, password: String, confirmPassword: String, displayName: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            showError("El email es requerido")
            isValid = false
        } else if (password.isEmpty()) {
            showError("La contraseña es requerida")
            isValid = false
        } else if (password.length < 8) {
            showError("La contraseña debe tener al menos 8 caracteres")
            isValid = false
        } else if (confirmPassword.isEmpty()) {
            showError("Confirma tu contraseña")
            isValid = false
        } else if (password != confirmPassword) {
            showError("Las contraseñas no coinciden")
            isValid = false
        } else if (displayName.isEmpty()) {
            showError("El nombre es requerido")
            isValid = false
        }

        return isValid
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            authViewModel.uiState.collect { state ->
                handleUiState(state)
            }
        }
    }

    private fun observeCurrentUser() {
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                if (user != null) {
                    saveUserSession(user)
                }
            }
        }
    }

    private fun handleUiState(state: AuthUiState) {
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.btnPrimary.isEnabled = !state.isLoading
        binding.btnToggleMode.isEnabled = !state.isLoading

        if (state.isLoading) {
            loadingDialog.showLoadingDialog("Iniciando sesión", R.raw.cow)
        } else {
            loadingDialog.dismiss()
        }

        if (state.errorMessage != null) {
            showError(state.errorMessage)
        }

        if (state.successMessage != null) {
            Toast.makeText(this, state.successMessage, Toast.LENGTH_LONG).show()
        }

        if (state.isSignInSuccess) {
            navigateToMainScreen()
        }

        if (state.isSignUpSuccess) {
            isRegisterMode = false
            updateUI()
            clearFields()
            authViewModel.resetSuccessStates()
            Toast.makeText(this, "Cuenta creada exitosamente. Ahora puedes iniciar sesión.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUI() {
        if (isRegisterMode) {
            // Modo registro
            binding.tvTitle.text = "Crear Cuenta"
            binding.btnPrimary.text = "Registrarse"
            binding.btnToggleMode.text = "¿Ya tienes cuenta? Inicia sesión"

            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.tilDisplayName.visibility = View.VISIBLE
            binding.userTypeSection.visibility = View.VISIBLE

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
            binding.userTypeSection.visibility = View.GONE
            binding.tilRanchName.visibility = View.GONE
        }

        hideError()
    }

    private fun clearFields() {
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        binding.etDisplayName.text?.clear()
        binding.etRanchName.text?.clear()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.errorCard.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.errorCard.visibility = View.GONE
    }

    private fun saveUserSession(user: FirebaseUserProfile) {
        AppPreferences.saveUserSessionFirebase(this, user)
    }

    private fun navigateToMainScreen() {
        // Navegar a la pantalla principal después del login exitoso
        val intent = Intent(this, MapsView::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}