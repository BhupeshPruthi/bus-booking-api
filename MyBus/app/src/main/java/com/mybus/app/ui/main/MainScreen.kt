package com.mybus.app.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.local.TokenManager
import com.mybus.app.ui.addbus.AddBusScreen
import com.mybus.app.ui.addbus.AddBusViewModel
import com.mybus.app.ui.addbus.SelectStopsScreen
import com.mybus.app.ui.bus.ActiveBusesScreen
import com.mybus.app.ui.bus.BusTab
import com.mybus.app.ui.events.AddEventScreen
import com.mybus.app.ui.home.AdminBusDetailScreen
import com.mybus.app.ui.home.BusDetailScreen
import com.mybus.app.ui.home.HomeTab
import com.mybus.app.ui.navigation.BottomTabs
import com.mybus.app.ui.theme.BrandOrange
import com.mybus.app.ui.theme.TabBarIndicatorGradientFill
import com.mybus.app.ui.pooja.PoojaDetailScreen
import com.mybus.app.ui.pooja.PoojaTab
import com.mybus.app.ui.pooja.SchedulePoojaScreen
import com.mybus.app.ui.pooja.UpcomingPoojaListScreen
import com.mybus.app.ui.profile.ProfileScreen
import com.mybus.app.ui.stay.StayTab
import com.mybus.app.ui.trips.BookingDetailScreen
import com.mybus.app.ui.trips.MyTripsScreen
import com.mybus.app.ui.auth.AuthUiState
import com.mybus.app.ui.auth.AuthViewModel
import com.mybus.app.ui.auth.LoginScreen
import com.mybus.app.ui.web.InAppWebViewScreen
import com.mybus.app.ui.web.LiveEventsWeb

@Composable
fun MainScreen(
    tokenManager: TokenManager,
    authViewModel: AuthViewModel,
    authUiState: AuthUiState,
    onLogout: () -> Unit,
) {
    var showLoginSheet by remember { mutableStateOf(false) }
    val onRequireLogin: () -> Unit = {
        authViewModel.resetState()
        showLoginSheet = true
    }

    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val accessToken by tokenManager.accessToken.collectAsStateWithLifecycle(initialValue = null)
    val isLoggedIn = !accessToken.isNullOrBlank()

    val isAdmin by tokenManager.effectiveIsAdmin.collectAsStateWithLifecycle(initialValue = false)

    val detailRoutes = remember {
        setOf(
            "active_buses",
            "add_bus",
            "add_event",
            "select_stops",
            "schedule_pooja",
            "pooja_upcoming_list",
            "bus_detail/{busId}",
            "admin_bus_detail/{busId}",
            "pooja_detail/{poojaId}",
            "booking_detail/{bookingId}",
            "my_trips",
            "live_events_web",
            BottomTabs.PROFILE
        )
    }
    val showBottomBar = currentRoute != null && currentRoute !in detailRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Avoid stacking safeDrawing top inset here with each destination's TopAppBar (extra gap under status bar).
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val unselectedIcon = MaterialTheme.colorScheme.onSurfaceVariant
                    val unselectedText = MaterialTheme.colorScheme.onSurfaceVariant
                    BottomTabs.items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                tabNavController.navigate(item.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandOrange,
                                selectedTextColor = BrandOrange,
                                unselectedIconColor = unselectedIcon,
                                unselectedTextColor = unselectedText,
                                indicatorColor = TabBarIndicatorGradientFill
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = BottomTabs.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ---- Bottom tabs ----
            composable(BottomTabs.HOME) {
                HomeTab(
                    isAdmin = isAdmin,
                    isLoggedIn = isLoggedIn,
                    onOpenMyTrips = {
                        if (isLoggedIn) {
                            tabNavController.navigate("my_trips") {
                                launchSingleTop = true
                            }
                        } else {
                            onRequireLogin()
                        }
                    },
                    onOpenBookingDetail = { bookingId ->
                        if (isLoggedIn) {
                            tabNavController.navigate("booking_detail/$bookingId")
                        } else {
                            onRequireLogin()
                        }
                    },
                    onAddEventClick = {
                        if (isLoggedIn) {
                            tabNavController.navigate("add_event") {
                                launchSingleTop = true
                            }
                        } else {
                            onRequireLogin()
                        }
                    },
                    onOpenProfile = {
                        if (isLoggedIn) {
                            tabNavController.navigate(BottomTabs.PROFILE) {
                                launchSingleTop = true
                            }
                        } else {
                            onRequireLogin()
                        }
                    },
                    onOpenLiveEvents = {
                        tabNavController.navigate("live_events_web") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(BottomTabs.BUS) {
                BusTab(
                    onConsumerBusClick = { busId ->
                        tabNavController.navigate("bus_detail/$busId")
                    },
                    onAdminBusClick = { busId ->
                        tabNavController.navigate("admin_bus_detail/$busId")
                    },
                    onAddBusClick = { tabNavController.navigate("add_bus") }
                )
            }
            composable(BottomTabs.POOJA) {
                PoojaTab(
                    isAdmin = isAdmin,
                    isLoggedIn = isLoggedIn,
                    onRequireLogin = onRequireLogin,
                    onAddClick = { tabNavController.navigate("schedule_pooja") },
                    onPoojaClick = { poojaId ->
                        tabNavController.navigate("pooja_detail/$poojaId")
                    }
                )
            }
            composable(BottomTabs.STAY) {
                StayTab()
            }
            composable(BottomTabs.PROFILE) {
                ProfileScreen(
                    onLogout = onLogout,
                    onBack = { tabNavController.popBackStack() }
                )
            }

            // ---- Sub-screens ----
            composable("active_buses") {
                ActiveBusesScreen(
                    onBack = { tabNavController.popBackStack() },
                    onBusClick = { busId ->
                        if (isAdmin) {
                            tabNavController.navigate("admin_bus_detail/$busId")
                        } else {
                            tabNavController.navigate("bus_detail/$busId")
                        }
                    }
                )
            }
            composable("add_event") {
                AddEventScreen(onBack = { tabNavController.popBackStack() })
            }
            composable("live_events_web") {
                InAppWebViewScreen(
                    url = LiveEventsWeb.URL,
                    title = "Live events",
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable("add_bus") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    tabNavController.getBackStackEntry("add_bus")
                }
                val addBusViewModel: AddBusViewModel = hiltViewModel(parentEntry)
                AddBusScreen(
                    viewModel = addBusViewModel,
                    onNavigateToStops = { tabNavController.navigate("select_stops") },
                    onBack = { tabNavController.popBackStack() },
                    onSuccessDone = { tabNavController.popBackStack() }
                )
            }
            composable("select_stops") {
                val parentEntry = remember {
                    tabNavController.getBackStackEntry("add_bus")
                }
                val addBusViewModel: AddBusViewModel = hiltViewModel(parentEntry)
                val uiState by addBusViewModel.uiState.collectAsStateWithLifecycle()
                SelectStopsScreen(
                    stops = uiState.stops,
                    onToggle = { index -> addBusViewModel.toggleStop(index) },
                    onBack = { tabNavController.popBackStack() }
                )
            }
            composable("schedule_pooja") {
                SchedulePoojaScreen(
                    onBack = { tabNavController.popBackStack() }
                )
            }
            composable("pooja_upcoming_list") {
                UpcomingPoojaListScreen(
                    onBack = { tabNavController.popBackStack() },
                    onPoojaClick = { poojaId ->
                        tabNavController.navigate("pooja_detail/$poojaId")
                    }
                )
            }
            composable(
                route = "pooja_detail/{poojaId}",
                arguments = listOf(navArgument("poojaId") { type = NavType.StringType })
            ) {
                PoojaDetailScreen(
                    isAdmin = isAdmin,
                    onBack = { tabNavController.popBackStack() }
                )
            }
            composable(
                route = "bus_detail/{busId}",
                arguments = listOf(navArgument("busId") { type = NavType.StringType })
            ) {
                BusDetailScreen(
                    isLoggedIn = isLoggedIn,
                    onRequireLogin = onRequireLogin,
                    onBack = { tabNavController.popBackStack() },
                    onBookingSuccess = {
                        tabNavController.navigate("my_trips") {
                            popUpTo(tabNavController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = "admin_bus_detail/{busId}",
                arguments = listOf(navArgument("busId") { type = NavType.StringType })
            ) {
                AdminBusDetailScreen(
                    onBack = { tabNavController.popBackStack() }
                )
            }
            composable("my_trips") {
                MyTripsScreen(
                    onBookingClick = { bookingId ->
                        tabNavController.navigate("booking_detail/$bookingId")
                    },
                    onBack = { tabNavController.popBackStack() },
                    isLoggedIn = isLoggedIn,
                    onRequireLogin = onRequireLogin
                )
            }
            composable(
                route = "booking_detail/{bookingId}",
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
            ) {
                BookingDetailScreen(
                    onBack = { tabNavController.popBackStack() }
                )
            }
        }
    }

    // Login bottom sheet overlay — shown on top of current screen
    if (showLoginSheet) {
        LoginScreen(
            uiState = authUiState,
            onGoogleSignIn = { idToken -> authViewModel.signInWithGoogle(idToken) },
            onSignInError = { authViewModel.setSignInError(it) },
            onClearError = { authViewModel.clearError() },
            onDismiss = { showLoginSheet = false },
        )
    }

    // Auto-dismiss sheet on successful login
    LaunchedEffect(authUiState.loginSuccess) {
        if (authUiState.loginSuccess) {
            showLoginSheet = false
            authViewModel.resetState()
        }
    }
}
