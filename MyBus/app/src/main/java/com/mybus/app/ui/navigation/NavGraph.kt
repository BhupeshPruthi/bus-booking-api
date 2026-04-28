package com.mybus.app.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mybus.app.data.local.TokenManager
import com.mybus.app.ui.auth.AuthViewModel
import com.mybus.app.ui.auth.LoginScreen
import com.mybus.app.ui.main.MainScreen

object Routes {
    const val LOGIN = "auth/login"
    const val MAIN = "main"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    tokenManager: TokenManager
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                uiState = uiState,
                onGoogleSignIn = { idToken -> authViewModel.signInWithGoogle(idToken) },
                onSignInError = { authViewModel.setSignInError(it) },
                onClearError = { authViewModel.clearError() },
                onDismiss = { navController.popBackStack() },
            )

            LaunchedEffect(uiState.loginSuccess) {
                if (uiState.loginSuccess) {
                    authViewModel.resetState()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.MAIN) {
            MainScreen(
                tokenManager = tokenManager,
                authViewModel = authViewModel,
                authUiState = uiState,
                onLogout = { authViewModel.logout() },
            )

            LaunchedEffect(uiState.logoutSuccess) {
                if (uiState.logoutSuccess) {
                    authViewModel.resetState()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
}
