package com.mybus.app.ui.stay

import androidx.compose.runtime.Composable

@Composable
fun StayTab(
    isStayAdmin: Boolean,
    canManageAdmins: Boolean,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMyBookings: () -> Unit
) {
    if (isStayAdmin) {
        StayAdminScreen(canManageAdmins = canManageAdmins)
    } else {
        StayCustomerScreen(
            isLoggedIn = isLoggedIn,
            onRequireLogin = onRequireLogin,
            onOpenMyBookings = onOpenMyBookings
        )
    }
}
