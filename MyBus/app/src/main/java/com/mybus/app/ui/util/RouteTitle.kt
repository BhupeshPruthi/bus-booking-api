package com.mybus.app.ui.util

/**
 * Route line for titles: round trip uses a bidirectional arrow (↔); one way uses →.
 */
fun formatRouteTitle(source: String, destination: String, tripType: String?): String {
    val sep = if (tripType == "round_trip") "  \u2194  " else "  \u2192  "
    return "$source$sep$destination"
}
