package com.mybus.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mybus.app.R

/**
 * Promotional artwork for Ranibagh–Mehendipur Balaji bus trips (Active Buses + trip detail).
 */
@Composable
fun BusBookingPromoBanner(
    modifier: Modifier = Modifier,
    contentDescription: String = "Bus service to Shri Mehendipur Balaji Dham",
) {
    Image(
        painter = painterResource(R.drawable.bus_booking_promo),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}
