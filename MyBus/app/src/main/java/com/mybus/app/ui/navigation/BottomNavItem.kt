package com.mybus.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

object BottomTabs {
    const val HOME = "tab_home"
    const val BUS = "tab_bus"
    const val POOJA = "tab_pooja"
    const val STAY = "tab_stay"
    const val PROFILE = "tab_profile"

    val items: List<BottomNavItem> = listOf(
        BottomNavItem(
            route = HOME,
            label = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        BottomNavItem(
            route = BUS,
            label = "Bus",
            selectedIcon = Icons.Filled.DirectionsBus,
            unselectedIcon = Icons.Outlined.DirectionsBus
        ),
        BottomNavItem(
            route = POOJA,
            label = "Pooja",
            selectedIcon = Icons.Filled.SelfImprovement,
            unselectedIcon = Icons.Outlined.SelfImprovement
        ),
        BottomNavItem(
            route = STAY,
            label = "Stay",
            selectedIcon = Icons.Filled.Hotel,
            unselectedIcon = Icons.Outlined.Hotel
        )
    )
}
