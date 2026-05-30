package com.mybus.app.ui.pooja

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.PoojaBookingData
import com.mybus.app.data.remote.dto.PoojaDetailData
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoojaDetailScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onBookTokenClick: (poojaId: String) -> Unit,
    viewModel: PoojaDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var bookingToCancel by remember { mutableStateOf<PoojaBookingData?>(null) }

    LaunchedEffect(isAdmin) {
        viewModel.load(isAdmin)
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    bookingToCancel?.let { booking ->
        val isCancelling = state.cancelInProgressId == booking.id
        AlertDialog(
            onDismissRequest = { if (!isCancelling) bookingToCancel = null },
            title = { Text("Cancel Token") },
            text = {
                Text(
                    "Cancel token ${booking.tokenNumber?.let { "#$it" } ?: booking.name}? " +
                        "This will release this pooja token for another booking request."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelBookingAsAdmin(booking.id)
                        bookingToCancel = null
                    },
                    enabled = !isCancelling,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Token")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingToCancel = null }, enabled = !isCancelling) {
                    Text("Keep")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pooja Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val pooja = state.pooja
                    val bookings = pooja?.bookings.orEmpty()
                    if (isAdmin && pooja != null && bookings.isNotEmpty()) {
                        TextButton(onClick = { exportPoojaBookings(context, pooja, bookings) }) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading && state.pooja == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.pooja == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load pooja",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }

                else -> {
                    val pooja = state.pooja!!
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PoojaSummaryCard(pooja)
                        }

                        if (!isAdmin) {
                            item {
                                Button(
                                    onClick = { onBookTokenClick(pooja.id) },
                                    enabled = pooja.availableTokens > 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (pooja.availableTokens > 0) "Book Token" else "No Tokens Available",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "Enrolled (${pooja.bookings?.size ?: 0})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val bookings = pooja.bookings ?: emptyList()
                            if (bookings.isEmpty()) {
                                item {
                                    Text(
                                        text = "No one has enrolled yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val canCancelTokens = isFutureDateTime(pooja.scheduledAt)
                                items(bookings, key = { it.id }) { booking ->
                                    BookingRow(
                                        booking = booking,
                                        canCancel = canCancelTokens && booking.status == "confirmed",
                                        isCancelling = state.cancelInProgressId == booking.id,
                                        onCancel = { bookingToCancel = booking }
                                    )
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoojaSummaryCard(pooja: PoojaDetailData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = pooja.place,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDateTime(pooja.scheduledAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tokens: ${pooja.availableTokens} available / ${pooja.totalTokens} total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pooja.availableTokens > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Status: ${pooja.status.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookingRow(
    booking: PoojaBookingData,
    canCancel: Boolean,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    booking.tokenNumber?.let {
                        Text(
                            text = "Token #$it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = booking.status.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = booking.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Members: ${booking.memberCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "City: ${booking.city}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            booking.createdAt?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Booked: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            booking.cancelledAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cancelled: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (canCancel) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Token")
                    }
                }
            }
        }
    }
}

private fun isFutureDateTime(isoString: String): Boolean {
    return try {
        Instant.parse(isoString).isAfter(Instant.now())
    } catch (_: Exception) {
        false
    }
}

private fun formatDateTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        zoned.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}

private fun exportPoojaBookings(
    context: Context,
    pooja: PoojaDetailData,
    bookings: List<PoojaBookingData>
) {
    runCatching {
        val pdfFile = createPoojaBookingsPdf(context, pooja, bookings)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val title = "Pooja Token Bookings - ${pooja.place}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Pooja Tokens"))
    }.onFailure {
        Toast.makeText(context, "Unable to export pooja tokens PDF", Toast.LENGTH_LONG).show()
    }
}

private fun createPoojaBookingsPdf(
    context: Context,
    pooja: PoojaDetailData,
    bookings: List<PoojaBookingData>
): File {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val pdfFile = File(exportDir, "pooja_tokens_${safeFilePart(pooja.place)}.pdf")

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

    val sortedBookings = bookings.sortedWith(
        compareBy<PoojaBookingData> { it.tokenNumber ?: Int.MAX_VALUE }
            .thenBy { it.createdAt ?: "" }
            .thenBy { it.name }
    )
    val confirmedCount = bookings.count { it.status == "confirmed" }
    val cancelledCount = bookings.count { it.status == "cancelled" }

    startPage()

    drawWrapped("Pooja Token Bookings", titlePaint, extraBottom = 8f)
    drawWrapped(pooja.place, sectionPaint)
    drawWrapped("Scheduled: ${formatDateTime(pooja.scheduledAt)}", bodyPaint)
    drawWrapped("Status: ${pooja.status.toTitleLabel()}", bodyPaint)
    drawWrapped(
        "Tokens: ${pooja.bookedTokens} booked / ${pooja.totalTokens} total (${pooja.availableTokens} available)",
        bodyPaint
    )
    drawWrapped(
        "Requests: ${bookings.size} total, $confirmedCount confirmed, $cancelledCount cancelled",
        bodyPaint,
        extraBottom = 8f
    )

    drawSeparator()
    drawWrapped("Token List (${bookings.size} requests)", sectionPaint, extraBottom = 8f)

    sortedBookings.forEachIndexed { index, booking ->
        ensureSpace(112f)
        drawWrapped(
            "${index + 1}. ${booking.tokenNumber?.let { "Token #$it" } ?: "Token N/A"} - ${booking.name}",
            sectionPaint
        )
        drawWrapped("Phone: ${booking.phone}", bodyPaint)
        drawWrapped("Members: ${booking.memberCount}", bodyPaint)
        drawWrapped("City: ${booking.city}", bodyPaint)
        booking.user?.name?.takeIf { it.isNotBlank() && it != booking.name }?.let {
            drawWrapped("User: $it", bodyPaint)
        }
        booking.user?.mobile?.takeIf { it.isNotBlank() && it != booking.phone }?.let {
            drawWrapped("User mobile: $it", bodyPaint)
        }
        booking.createdAt?.let { drawWrapped("Booked: ${formatDateTime(it)}", mutedPaint) }
        booking.cancelledAt?.let { drawWrapped("Cancelled: ${formatDateTime(it)}", mutedPaint) }
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
        .ifBlank { "pooja" }
        .take(40)
}

private fun String.toTitleLabel(): String {
    return split('_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char -> char.uppercase() }
        }
}
