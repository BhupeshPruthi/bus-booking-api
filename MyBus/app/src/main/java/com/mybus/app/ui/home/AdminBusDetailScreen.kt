package com.mybus.app.ui.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.remote.dto.BusDetailData
import com.mybus.app.ui.util.formatBusScheduleDateTimeFull
import com.mybus.app.ui.util.formatRouteTitle
import com.mybus.app.ui.util.seatAvailabilityFromBookings
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBusDetailScreen(
    onBack: () -> Unit,
    viewModel: AdminBusDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh bookings")
                    }
                    if (state.bookings.isNotEmpty()) {
                        TextButton(onClick = { exportBookings(context, state.bus, state.bookings) }) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading && state.bus == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.error != null && state.bus == null -> {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadAll() }) { Text("Retry") }
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    if (state.bookingsLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.bus?.let { bus ->
                            item { BusSummaryCard(bus, state.bookings) }
                        }

                        item {
                            Text(
                                text = "Bookings (${state.bookings.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        state.bookingsError?.let { message ->
                            item {
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Could not load all booking requests",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Text(
                                            message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(onClick = { viewModel.loadBookings() }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }

                        if (state.bookings.isEmpty() && !state.bookingsLoading && state.bookingsError == null) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.Inbox,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "No booking requests yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        items(state.bookings, key = { it.id }) { booking ->
                            AdminBookingCard(
                                booking = booking,
                                isActionInProgress = state.actionInProgress == booking.id,
                                onApprove = { viewModel.approveBooking(booking.id) },
                                onReject = { viewModel.rejectBooking(booking.id) },
                                onCancel = { viewModel.cancelBooking(booking.id) },
                                onApproveCancellation = { viewModel.approveCancellation(booking.id) },
                                onRejectCancellation = { viewModel.rejectCancellation(booking.id) }
                            )
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusSummaryCard(bus: BusDetailData, bookings: List<BookingData>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = formatRouteTitle(bus.route.source, bus.route.destination, bus.tripType),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            val finalArrival = if (bus.tripType == "round_trip") {
                bus.returnArrivalTime ?: bus.arrivalTime
            } else {
                bus.arrivalTime
            }

            SummaryRow(Icons.Filled.FlightTakeoff, "Departure", formatDateTimeFull(bus.departureTime))
            finalArrival?.let {
                SummaryRow(Icons.Filled.FlightLand, "Arrival", formatDateTimeFull(it))
            }
            val availability = bus.seatAvailabilityFromBookings(bookings)
            SummaryRow(
                Icons.Filled.EventSeat, "Seats",
                "${availability.availableSeats} available / ${availability.bookableSeats} seats"
            )
            SummaryRow(
                Icons.Filled.ConfirmationNumber, "Requested",
                "${availability.reservedSeats} requested / ${availability.totalSeats} total"
            )
            SummaryRow(
                Icons.Filled.CurrencyRupee, "Price",
                "\u20B9${bus.price.toLong()} per seat"
            )
            bus.contactName?.let {
                SummaryRow(Icons.Filled.Person, "Contact", "$it${bus.contactPhone?.let { p -> " ($p)" } ?: ""}")
            }
            SummaryRow(
                Icons.Filled.SyncAlt, "Type",
                if (bus.tripType == "round_trip") "Round Way" else "One Way"
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdminBookingCard(
    booking: BookingData,
    isActionInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onApproveCancellation: () -> Unit,
    onRejectCancellation: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.passengerName ?: booking.user?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = booking.passengerPhone ?: booking.user?.mobile ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    booking.route?.let { route ->
                        Text(
                            text = "${route.source} to ${route.destination}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusChip(status = booking.status)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EventSeat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    val seatLabel = if (booking.assignedSeats != null) {
                        "Seats: ${booking.assignedSeats}"
                    } else {
                        "${booking.seatCount} seat(s)"
                    }
                    Text(seatLabel, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "\u20B9${booking.totalAmount.toLong()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            booking.pickupPoint?.let { pp ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(pp.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            booking.createdAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Requested: ${formatDateTimeFull(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (booking.status in setOf("pending", "payment_uploaded", "confirmed", "cancellation_requested")) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (booking.status == "cancellation_requested") {
                        OutlinedButton(onClick = onRejectCancellation) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Keep Booking")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    } else if (booking.status == "confirmed") {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel Booking")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reject")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onApprove) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            icon = { Icon(Icons.Filled.Cancel, contentDescription = null) },
            title = { Text("Cancel booking") },
            text = { Text("This will release the assigned seat for new bookings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        if (booking.status == "cancellation_requested") {
                            onApproveCancellation()
                        } else {
                            onCancel()
                        }
                    }
                ) { Text("Cancel Booking") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "pending" -> "Pending" to MaterialTheme.colorScheme.tertiary
        "confirmed" -> "Confirmed" to MaterialTheme.colorScheme.primary
        "cancellation_requested" -> "Cancellation Requested" to MaterialTheme.colorScheme.secondary
        "payment_uploaded" -> "Payment Sent" to MaterialTheme.colorScheme.secondary
        "rejected" -> "Rejected" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.error
        "expired" -> "Expired" to MaterialTheme.colorScheme.outline
        else -> status.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDateTimeFull(isoString: String): String {
    return formatBusScheduleDateTimeFull(isoString)
}

private fun exportBookings(context: Context, bus: BusDetailData?, bookings: List<BookingData>) {
    runCatching {
        val pdfFile = createBookingsPdf(context, bus, bookings)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val title = "Booking Requests - ${bus?.route?.source ?: ""} to ${bus?.route?.destination ?: ""}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Bookings"))
    }.onFailure {
        Toast.makeText(context, "Unable to export bookings PDF", Toast.LENGTH_LONG).show()
    }
}

private fun createBookingsPdf(
    context: Context,
    bus: BusDetailData?,
    bookings: List<BookingData>
): File {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val source = safeFilePart(bus?.route?.source ?: "bookings")
    val destination = safeFilePart(bus?.route?.destination ?: "trip")
    val pdfFile = File(exportDir, "bookings_${source}_to_${destination}.pdf")

    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val contentWidth = pageWidth - (margin * 2)
    var pageNumber = 1
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = margin

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(40, 40, 40)
        textSize = 11f
    }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(95, 95, 95)
        textSize = 10f
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(210, 210, 210)
        strokeWidth = 1f
    }

    fun startPage() {
        page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        canvas = page.canvas
        y = margin
        pageNumber += 1
    }

    fun finishPage() {
        document.finishPage(page)
    }

    fun ensureSpace(required: Float) {
        if (y + required > pageHeight - margin) {
            finishPage()
            startPage()
        }
    }

    fun drawWrapped(text: String, paint: Paint, extraBottom: Float = 4f) {
        val lines = wrapText(text, paint, contentWidth)
        lines.forEach { line ->
            ensureSpace(paint.textSize + 8f)
            canvas.drawText(line, margin, y, paint)
            y += paint.textSize + 5f
        }
        y += extraBottom
    }

    fun drawSeparator() {
        ensureSpace(12f)
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 12f
    }

    startPage()

    drawWrapped("Booking Requests", titlePaint, extraBottom = 8f)

    bus?.let {
        val finalArrival = if (it.tripType == "round_trip") {
            it.returnArrivalTime ?: it.arrivalTime
        } else {
            it.arrivalTime
        }

        drawWrapped("${it.route.source} to ${it.route.destination}", sectionPaint)
        drawWrapped("Departure: ${formatDateTimeFull(it.departureTime)}", bodyPaint)
        finalArrival?.let { arr -> drawWrapped("Arrival: ${formatDateTimeFull(arr)}", bodyPaint) }
        val availability = it.seatAvailabilityFromBookings(bookings)
        drawWrapped("${availability.availableSeats} available / ${availability.bookableSeats} seats", bodyPaint)
        drawWrapped("${availability.reservedSeats} requested / ${availability.totalSeats} total", bodyPaint)
        drawWrapped("Fare: Rs ${it.price.toLong()} per seat", bodyPaint, extraBottom = 8f)
    }

    drawSeparator()
    drawWrapped("Passenger List (${bookings.size} requests)", sectionPaint, extraBottom = 8f)

    bookings.forEachIndexed { index, booking ->
        ensureSpace(86f)
        drawWrapped("${index + 1}. ${booking.passengerName ?: booking.user?.name ?: "N/A"}", sectionPaint)
        drawWrapped("Phone: ${booking.passengerPhone ?: booking.user?.mobile ?: "N/A"}", bodyPaint)
        drawWrapped("Seats: ${booking.assignedSeats ?: booking.seatCount.toString()}", bodyPaint)
        booking.pickupPoint?.let { drawWrapped("Pickup: ${it.name}", bodyPaint) }
        drawWrapped("Amount: Rs ${booking.totalAmount.toLong()}", bodyPaint)
        drawWrapped("Status: ${booking.status.toTitleLabel()}", mutedPaint, extraBottom = 6f)
        drawSeparator()
    }

    finishPage()
    FileOutputStream(pdfFile).use { output -> document.writeTo(output) }
    document.close()
    return pdfFile
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)

    val lines = mutableListOf<String>()
    var current = ""
    text.split(" ").forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) {
            current = candidate
        } else {
            if (current.isNotBlank()) lines.add(current)
            current = word
        }
    }
    if (current.isNotBlank()) lines.add(current)
    return lines.ifEmpty { listOf(text) }
}

private fun safeFilePart(value: String): String {
    return value
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "trip" }
        .take(40)
}

private fun String.toTitleLabel(): String {
    return split('_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char -> char.uppercase() }
        }
}
