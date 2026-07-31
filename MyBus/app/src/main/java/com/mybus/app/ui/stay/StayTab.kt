package com.mybus.app.ui.stay

import androidx.compose.runtime.Composable

@Composable
fun StayTab(
    isStayAdmin: Boolean,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMyBookings: () -> Unit
) {
    if (isStayAdmin) {
        StayAdminScreen()
    } else {
        StayCustomerScreen(
            isLoggedIn = isLoggedIn,
            onRequireLogin = onRequireLogin,
            onOpenMyBookings = onOpenMyBookings
        )
    }
}
