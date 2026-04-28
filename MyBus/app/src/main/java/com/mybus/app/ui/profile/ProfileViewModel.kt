package com.mybus.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {
    val userMobile: Flow<String?> = tokenManager.userMobile
    val userEmail: Flow<String?> = tokenManager.userEmail
    val userName: Flow<String?> = tokenManager.userName
    val debugRoleOverride: Flow<String?> = tokenManager.debugRoleOverride

    fun setDebugRoleOverride(mode: String?) {
        viewModelScope.launch {
            tokenManager.setDebugRoleOverride(mode)
        }
    }
}
