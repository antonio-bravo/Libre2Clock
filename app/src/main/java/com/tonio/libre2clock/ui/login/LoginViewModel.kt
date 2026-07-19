package com.tonio.libre2clock.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.repository.GlucoseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: GlucoseRepository
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginResult = MutableStateFlow<Result<Unit>?>(null)
    val loginResult: StateFlow<Result<Unit>?> = _loginResult.asStateFlow()

    fun onEmailChanged(email: String) {
        _email.value = email
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
    }

    fun login() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.login(_email.value, _password.value)
            _loginResult.value = result
            _isLoading.value = false
        }
    }

    fun startDemoMode() {
        viewModelScope.launch {
            repository.enableDemoMode()
            _loginResult.value = Result.success(Unit)
        }
    }
}
