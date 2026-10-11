package com.tonio.libre2clock.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.DexcomServerRegion
import com.tonio.libre2clock.data.model.GlucoseSourceProvider
import com.tonio.libre2clock.data.repository.GlucoseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: GlucoseRepository
) : ViewModel() {

    private val _selectedProvider = MutableStateFlow(GlucoseSourceProvider.LIBRE_LINK_UP)
    val selectedProvider: StateFlow<GlucoseSourceProvider> = _selectedProvider.asStateFlow()

    private val _dexcomRegion = MutableStateFlow(DexcomServerRegion.OUS)
    val dexcomRegion: StateFlow<DexcomServerRegion> = _dexcomRegion.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginResult = MutableStateFlow<Result<Unit>?>(null)
    val loginResult: StateFlow<Result<Unit>?> = _loginResult.asStateFlow()

    fun onProviderSelected(provider: GlucoseSourceProvider) {
        _selectedProvider.value = provider
    }

    fun onDexcomRegionSelected(region: DexcomServerRegion) {
        _dexcomRegion.value = region
    }

    fun onEmailChanged(email: String) {
        _email.value = email
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
    }

    fun login() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = if (_selectedProvider.value == GlucoseSourceProvider.DEXCOM_SHARE) {
                repository.loginDexcom(_email.value, _password.value, _dexcomRegion.value)
            } else {
                repository.login(_email.value, _password.value)
            }
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
