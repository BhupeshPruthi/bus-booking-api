package com.mybus.app.ui.stay

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.CreateStayBookingRequest
import com.mybus.app.data.remote.dto.StayBookingItemRequest
import com.mybus.app.data.remote.dto.StayCancellation
import com.mybus.app.data.remote.dto.StayCoupon
import com.mybus.app.data.remote.dto.StayDailyOccupancyBookings
import com.mybus.app.data.remote.dto.StayDailyOccupancyDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream

private enum class StayAdminPage(val title: String, val showInMenu: Boolean = true) {
    DASHBOARD("Stay Admin"),
    PENDING("Pending Requests"),
    CONFIRMED("Confirmed Bookings"),
    OCCUPANCY("Daily Occupancy"),
    OCCUPANCY_DETAILS("Occupancy Details", false),
    CANCELLATIONS("Cancellation Requests"),
    COUPONS("Coupons"),
    ARCHIVE("Stay Records (Last 30 Days)"),
    CREATE_BOOKING("Make Booking", false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayAdminScreen(viewModel: StayAdminViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var page by remember { mutableStateOf(StayAdminPage.DASHBOARD) }
    var occupancyTab by remember { mutableStateOf(OccupancyTab.UPCOMING) }
    var menuOpen by remember { mutableStateOf(false) }
    var rejectTarget by remember { mutableStateOf<StayBooking?>(null) }
    var cancellationTarget by remember { mutableStateOf<StayCancellation?>(null) }
    var showCreateCoupon by remember { mutableStateOf(false) }
    var deactivateCoupon by remember { mutableStateOf<StayCoupon?>(null) }
    var adminCancellationTarget by remember { mutableStateOf<StayBooking?>(null) }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(state.exportBookings) {
        state.exportBookings?.let { bookings ->
            exportStayRecords(context, bookings)
            viewModel.consumeExport()
        }
    }

    state.error?.let {
        NoticeDialog("Unable to continue", it, viewModel::clearNotice)
    }
    state.message?.let {
        NoticeDialog("Done", it, viewModel::clearNotice)
    }
    rejectTarget?.let { booking ->
        TextInputDialog(
            title = "Reject ${booking.reference}",
            label = "Mandatory reason",
            value = reason,
            onValueChange = { reason = it.take(1000) },
            onDismiss = { rejectTarget = null },
            onConfirm = {
                viewModel.reject(booking.id, reason.trim())
                rejectTarget = null
                reason = ""
            }
        )
    }
    cancellationTarget?.let { request ->
        CancellationDecisionDialog(
            request,
            onDismiss = { cancellationTarget = null },
            onDecision = { action, refund, amount, decisionReason ->
                viewModel.decideCancellation(
                    request.id,
                    action,
                    refund,
                    amount,
                    decisionReason
                )
                cancellationTarget = null
            }
        )
    }
    if (showCreateCoupon) {
        CreateCouponDialog(
            onDismiss = { showCreateCoupon = false },
            onCreate = { code, amount, startDate, endDate ->
                viewModel.createCoupon(code, amount, startDate, endDate)
                showCreateCoupon = false
            }
        )
    }
    adminCancellationTarget?.let { booking ->
        AdminStayCancellationDialog(
            booking = booking,
            onDismiss = { adminCancellationTarget = null },
            onConfirm = { refundDecision, refundAmount, cancellationReason ->
                viewModel.cancelAdminBooking(
                    booking.id,
                    refundDecision,
                    refundAmount,
                    cancellationReason
                )
                adminCancellationTarget = null
            }
        )
    }
    deactivateCoupon?.let { coupon ->
        AlertDialog(
            onDismissRequest = { deactivateCoupon = null },
            title = { Text("Deactivate ${coupon.code}?") },
            text = { Text("Users will no longer be able to apply this coupon.") },
            dismissButton = {
                TextButton(onClick = { deactivateCoupon = null }) { Text("Back") }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deactivateCoupon(coupon.id)
                    deactivateCoupon = null
                }) { Text("Deactivate") }
            }
        )
    }

    fun open(target: StayAdminPage) {
        page = target
        when (target) {
            StayAdminPage.PENDING -> viewModel.loadBookings("pending")
            StayAdminPage.CONFIRMED -> viewModel.loadBookings("confirmed")
            StayAdminPage.OCCUPANCY -> viewModel.loadDailyOccupancy()
            StayAdminPage.CANCELLATIONS -> viewModel.loadCancellations()
            StayAdminPage.COUPONS -> viewModel.loadCoupons()
            StayAdminPage.ARCHIVE -> {
                val (fromDate, toDate) = stayArchiveRange()
                viewModel.loadBookings(null, fromDate = fromDate, toDate = toDate)
            }
            else -> Unit
        }
    }

    fun navigateBack() {
        page = if (page == StayAdminPage.OCCUPANCY_DETAILS) {
            StayAdminPage.OCCUPANCY
        } else {
            StayAdminPage.DASHBOARD
        }
    }

    BackHandler(
        enabled = page == StayAdminPage.CREATE_BOOKING ||
            page == StayAdminPage.OCCUPANCY_DETAILS
    ) {
        navigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (page == StayAdminPage.OCCUPANCY_DETAILS) {
                            state.selectedOccupancyDate?.let(::formatOccupancyDate)
                                ?: page.title
                        } else {
                            page.title
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (page != StayAdminPage.DASHBOARD) {
                        IconButton(onClick = ::navigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (page == StayAdminPage.OCCUPANCY_DETAILS) {
                                    "Back to Daily Occupancy"
                                } else {
                                    "Back to Stay Admin"
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        when (page) {
                            StayAdminPage.DASHBOARD -> viewModel.refreshDashboard()
                            StayAdminPage.OCCUPANCY_DETAILS -> {
                                state.selectedOccupancyDate
                                    ?.let(viewModel::loadDailyOccupancyBookings)
                            }
                            else -> open(page)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Sections")
                        }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            StayAdminPage.entries.filter { it.showInMenu }.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.title) },
                                        onClick = {
                                            menuOpen = false
                                            open(target)
                                        }
                                    )
                                }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                StayAdminPage.DASHBOARD -> Dashboard(state, ::open) {
                    page = StayAdminPage.CREATE_BOOKING
                }
                StayAdminPage.CREATE_BOOKING -> AdminStayBookingPage(
                    onConfirm = {
                        viewModel.createAdminBooking(it)
                        page = StayAdminPage.DASHBOARD
                    }
                )
                StayAdminPage.PENDING -> BookingPage(
                    state,
                    emptyMessage = "No pending requests",
                    onSearch = { viewModel.loadBookings("pending", it) },
                    onLoadMore = { viewModel.loadBookings("pending", state.bookingSearch, false) }
                ) { booking ->
                    Button(onClick = { viewModel.confirm(booking.id) }) { Text("Confirm") }
                    OutlinedButton(onClick = {
                        reason = ""
                        rejectTarget = booking
                    }) { Text("Reject") }
                }
                StayAdminPage.CONFIRMED -> BookingPage(
                    state,
                    emptyMessage = "No confirmed bookings",
                    onSearch = { viewModel.loadBookings("confirmed", it) },
                    onLoadMore = { viewModel.loadBookings("confirmed", state.bookingSearch, false) }
                ) { booking ->
                    if (booking.bookingSource == "admin") {
                        OutlinedButton(onClick = { adminCancellationTarget = booking }) {
                            Text("Cancel booking")
                        }
                    }
                }
                StayAdminPage.OCCUPANCY -> DailyOccupancyPage(
                    state,
                    selectedTab = occupancyTab,
                    onTabSelected = { occupancyTab = it }
                ) { day ->
                    page = StayAdminPage.OCCUPANCY_DETAILS
                    viewModel.loadDailyOccupancyBookings(day.date)
                }
                StayAdminPage.OCCUPANCY_DETAILS -> DailyOccupancyBookingsPage(state)
                StayAdminPage.CANCELLATIONS -> CancellationPage(
                    state,
                    onReview = { cancellationTarget = it },
                    onLoadMore = { viewModel.loadCancellations(false) }
                )
                StayAdminPage.COUPONS -> CouponsPage(
                    state = state,
                    onCreate = { showCreateCoupon = true },
                    onDeactivate = { deactivateCoupon = it }
                )
                StayAdminPage.ARCHIVE -> BookingPage(
                    state,
                    emptyMessage = "No Stay records in the last 30 days",
                    onSearch = {
                        val (fromDate, toDate) = stayArchiveRange()
                        viewModel.loadBookings(null, it, fromDate = fromDate, toDate = toDate)
                    },
                    onLoadMore = {
                        val (fromDate, toDate) = stayArchiveRange()
                        viewModel.loadBookings(
                            null,
                            state.bookingSearch,
                            false,
                            fromDate,
                            toDate
                        )
                    },
                    onExport = viewModel::exportLastMonthRecords,
                    exporting = state.isExporting
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun NoticeDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                onValueChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(onClick = onConfirm, enabled = value.isNotBlank()) { Text("Confirm") }
        }
    )
}

@Composable
private fun Dashboard(
    state: StayAdminUiState,
    onOpen: (StayAdminPage) -> Unit,
    onCreateBooking: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = onCreateBooking, modifier = Modifier.fillMaxWidth()) {
                Text("Add confirmed booking")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard("Pending", state.pendingTotal, Modifier.weight(1f)) {
                    onOpen(StayAdminPage.PENDING)
                }
                DashboardCard("Cancellations", state.cancellationTotal, Modifier.weight(1f)) {
                    onOpen(StayAdminPage.CANCELLATIONS)
                }
            }
        }
        item {
            DashboardCard("Confirmed", state.confirmedTotal, Modifier.fillMaxWidth()) {
                onOpen(StayAdminPage.CONFIRMED)
            }
        }
        item {
            DashboardAction(
                "Daily Occupancy",
                "See booked and available rooms from 3 months ago through next month"
            ) { onOpen(StayAdminPage.OCCUPANCY) }
        }
        item { DashboardAction("Coupons", "Create and manage Stay discount codes") { onOpen(StayAdminPage.COUPONS) } }
        item { DashboardAction("Stay records", "View or export the last 30 days") { onOpen(StayAdminPage.ARCHIVE) } }
    }
}

private enum class OccupancyTab(val title: String) {
    UPCOMING("Upcoming"),
    PAST("Past 3 months")
}

@Composable
private fun DailyOccupancyPage(
    state: StayAdminUiState,
    selectedTab: OccupancyTab,
    onTabSelected: (OccupancyTab) -> Unit,
    onOpenDay: (StayDailyOccupancyDay) -> Unit
) {
    val report = state.dailyOccupancy
    if (report == null && !state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No occupancy information available")
        }
        return
    }

    // The server sends days oldest first and counts "today" in India time.
    // Read on each recomposition (not remembered), so the split moves on after midnight.
    val today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()
    val days = report?.days.orEmpty()
    val upcomingDays = remember(days, today) { days.filter { it.date >= today } }
    val pastDays = remember(days, today) { days.filter { it.date < today }.reversed() }
    val shownDays = if (selectedTab == OccupancyTab.UPCOMING) upcomingDays else pastDays
    val bookedDays = remember(shownDays) { shownDays.filter { it.bookingCount > 0 } }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            OccupancyTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.title) }
                )
            }
        }
        if (report != null && bookedDays.isNotEmpty()) {
            OutlinedButton(
                onClick = { exportDailyOccupancy(context, selectedTab, bookedDays, shownDays) },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export ${selectedTab.title.lowercase()} as PDF")
            }
        }

        key(selectedTab) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (report != null) {
                    item {
                        Text(
                            if (shownDays.isEmpty()) {
                                "No days in this range"
                            } else {
                                "${formatOccupancyDate(shownDays.first().date)} to " +
                                    formatOccupancyDate(shownDays.last().date)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(shownDays, key = { it.date }) { day ->
                        DailyOccupancyCard(day) { onOpenDay(day) }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun DailyOccupancyCard(day: StayDailyOccupancyDay, onClick: () -> Unit) {
    val dateLabel = remember(day.date) { formatOccupancyDate(day.date) }
    Card(
        onClick = onClick,
        enabled = day.bookingCount > 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${day.bookingCount} ${if (day.bookingCount == 1) "booking" else "bookings"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Total earnings: ₹${day.totalEarnings.asRupees()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider()
            day.unitTypes.forEach { unit ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(unit.displayName, modifier = Modifier.weight(1f))
                    Text(
                        "${unit.bookedUnits} booked · ${unit.availableUnits} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (day.bookingCount > 0) {
                Text(
                    "View all ${day.bookingCount} ${if (day.bookingCount == 1) "booking" else "bookings"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/** Shares a PDF of the tab's days that have bookings (days with no bookings are left out). */
private fun exportDailyOccupancy(
    context: Context,
    tab: OccupancyTab,
    bookedDays: List<StayDailyOccupancyDay>,
    shownDays: List<StayDailyOccupancyDay>
) {
    runCatching {
        val pdfFile = createDailyOccupancyPdf(context, tab, bookedDays, shownDays)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val title = "Daily Occupancy - ${tab.title}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Daily Occupancy"))
    }.onFailure {
        Toast.makeText(context, "Unable to export daily occupancy PDF", Toast.LENGTH_LONG).show()
    }
}

private fun createDailyOccupancyPdf(
    context: Context,
    tab: OccupancyTab,
    bookedDays: List<StayDailyOccupancyDay>,
    shownDays: List<StayDailyOccupancyDay>
): File {
    val india = ZoneId.of("Asia/Kolkata")
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val slug = if (tab == OccupancyTab.UPCOMING) "upcoming" else "past_3_months"
    val pdfFile = File(exportDir, "daily_occupancy_${slug}_${LocalDate.now(india)}.pdf")
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val contentWidth = pageWidth - (margin * 2)
    val right = pageWidth - margin
    var pageNumber = 1
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = margin

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val amountPaint = Paint(dayPaint).apply { textAlign = Paint.Align.RIGHT }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(40, 40, 40)
        textSize = 11f
    }
    val bodyRightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(95, 95, 95)
        textSize = 10f
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(210, 210, 210)
        strokeWidth = 1f
    }

    fun startPage() {
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
        pageNumber += 1
    }
    fun ensureSpace(required: Float) {
        if (y + required > pageHeight - margin) {
            document.finishPage(page)
            startPage()
        }
    }
    fun drawWrapped(text: String, paint: Paint, extraBottom: Float = 4f) {
        wrapStayPdfText(text, paint, contentWidth).forEach { line ->
            ensureSpace(paint.textSize + 8f)
            canvas.drawText(line, margin, y, paint)
            y += paint.textSize + 5f
        }
        y += extraBottom
    }
    fun drawSeparator() {
        ensureSpace(12f)
        canvas.drawLine(margin, y, right, y, linePaint)
        y += 12f
    }

    val range = if (shownDays.isEmpty()) "" else {
        val dates = shownDays.map { it.date }.sorted()
        "${formatOccupancyDate(dates.first())} to ${formatOccupancyDate(dates.last())}"
    }
    val totalEarnings = bookedDays.sumOf { it.totalEarnings }
    val generatedAt = java.time.ZonedDateTime.now(india)
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", java.util.Locale.ENGLISH))

    startPage()
    drawWrapped("Daily Occupancy - ${tab.title}", titlePaint, extraBottom = 6f)
    if (range.isNotEmpty()) drawWrapped(range, bodyPaint)
    drawWrapped(
        "${bookedDays.size} ${if (bookedDays.size == 1) "day" else "days"} with bookings · " +
            "Total earnings ₹${totalEarnings.asRupees()}",
        bodyPaint
    )
    drawWrapped(
        "Only days and room types with bookings are listed. Generated $generatedAt (India time).",
        mutedPaint,
        extraBottom = 8f
    )
    drawSeparator()

    bookedDays.forEach { day ->
        val units = day.unitTypes.filter { it.bookedUnits > 0 }
        // Keeps a day's heading and its rooms on the same page.
        ensureSpace(40f + units.size * 16f)
        canvas.drawText(formatOccupancyDate(day.date), margin, y, dayPaint)
        canvas.drawText("₹${day.totalEarnings.asRupees()}", right, y, amountPaint)
        y += dayPaint.textSize + 6f
        drawWrapped("${day.bookingCount} ${if (day.bookingCount == 1) "booking" else "bookings"}", mutedPaint, extraBottom = 2f)
        units.forEach { unit ->
            ensureSpace(bodyPaint.textSize + 8f)
            val counts = "${unit.bookedUnits} of ${unit.totalUnits} booked · ${unit.availableUnits} available"
            // A long room name is shortened so it never runs into the numbers on the right.
            val nameWidth = contentWidth - 12f - bodyRightPaint.measureText(counts) - 16f
            val name = TextUtils.ellipsize(unit.displayName, TextPaint(bodyPaint), nameWidth, TextUtils.TruncateAt.END)
            canvas.drawText(name.toString(), margin + 12f, y, bodyPaint)
            canvas.drawText(counts, right, y, bodyRightPaint)
            y += bodyPaint.textSize + 5f
        }
        y += 4f
        drawSeparator()
    }

    try {
        document.finishPage(page)
        FileOutputStream(pdfFile).use { document.writeTo(it) }
    } finally {
        document.close()
    }
    return pdfFile
}

private fun formatOccupancyDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
}.getOrDefault(value)

/** Shares a PDF guest list of every booking occupying accommodation on one day. */
private fun exportOccupancyDayGuests(context: Context, details: StayDailyOccupancyBookings) {
    runCatching {
        val pdfFile = createOccupancyDayGuestsPdf(context, details)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val title = "Guest list - ${formatOccupancyDate(details.date)}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Guest List"))
    }.onFailure {
        Toast.makeText(context, "Unable to export guest list PDF", Toast.LENGTH_LONG).show()
    }
}

private fun createOccupancyDayGuestsPdf(context: Context, details: StayDailyOccupancyBookings): File {
    val india = ZoneId.of("Asia/Kolkata")
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val pdfFile = File(exportDir, "guest_list_${details.date}.pdf")
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val contentWidth = pageWidth - (margin * 2)
    val right = pageWidth - margin
    var pageNumber = 1
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = margin

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val headingRightPaint = Paint(headingPaint).apply { textAlign = Paint.Align.RIGHT }
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
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
        pageNumber += 1
    }
    fun ensureSpace(required: Float) {
        if (y + required > pageHeight - margin) {
            document.finishPage(page)
            startPage()
        }
    }
    fun drawWrapped(text: String, paint: Paint, indent: Float = 0f, extraBottom: Float = 2f) {
        wrapStayPdfText(text, paint, contentWidth - indent).forEach { line ->
            ensureSpace(paint.textSize + 8f)
            canvas.drawText(line, margin + indent, y, paint)
            y += paint.textSize + 5f
        }
        y += extraBottom
    }
    fun drawSeparator() {
        ensureSpace(12f)
        canvas.drawLine(margin, y, right, y, linePaint)
        y += 12f
    }

    val bookings = details.bookings
    val totalGuests = bookings.sumOf { it.guestCount }
    val checkIns = bookings.count { it.checkInDate == details.date }
    val generatedAt = java.time.ZonedDateTime.now(india)
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", java.util.Locale.ENGLISH))

    startPage()
    drawWrapped("Guest list - ${formatOccupancyDate(details.date)}", titlePaint, extraBottom = 6f)
    drawWrapped(
        "${bookings.size} ${if (bookings.size == 1) "booking" else "bookings"} · " +
            "$totalGuests ${if (totalGuests == 1) "guest" else "guests"} · " +
            "$checkIns checking in today · Estimated earnings ₹${details.totalEarnings.asRupees()}",
        bodyPaint
    )
    details.unitTypes.filter { it.bookedUnits > 0 }.forEach { unit ->
        drawWrapped(
            "${unit.displayName}: ${unit.bookedUnits} of ${unit.totalUnits} booked · ${unit.availableUnits} available",
            mutedPaint
        )
    }
    drawWrapped("Generated $generatedAt (India time).", mutedPaint, extraBottom = 8f)
    drawSeparator()

    bookings.forEachIndexed { index, booking ->
        // Keeps a booking's name, contact and rooms together on one page.
        ensureSpace(110f + booking.items.size * 16f)
        val guestLabel = "${booking.guestCount} ${if (booking.guestCount == 1) "guest" else "guests"}"
        val nameWidth = contentWidth - headingRightPaint.measureText(guestLabel) - 16f
        val name = TextUtils.ellipsize("${index + 1}. ${booking.contactName}", TextPaint(headingPaint), nameWidth, TextUtils.TruncateAt.END)
        canvas.drawText(name.toString(), margin, y, headingPaint)
        canvas.drawText(guestLabel, right, y, headingRightPaint)
        y += headingPaint.textSize + 6f

        val indent = 14f
        drawWrapped("Phone: ${booking.contactPhone}", bodyPaint, indent)
        if (booking.contactEmail.isNotBlank()) drawWrapped("Email: ${booking.contactEmail}", bodyPaint, indent)
        drawWrapped(
            "${booking.reference} · ${booking.status.stayStatusLabel()} · " +
                if (booking.checkInDate == details.date) "Checks in today" else "Continuing stay",
            bodyPaint,
            indent
        )
        drawWrapped(
            "Stay: ${formatOccupancyDate(booking.checkInDate)} → ${formatOccupancyDate(booking.checkOutDate)} · " +
                "${booking.nightCount} ${if (booking.nightCount == 1) "night" else "nights"}",
            bodyPaint,
            indent
        )
        booking.items.forEach { item ->
            drawWrapped("${item.quantity} × ${item.unitTypeName}", bodyPaint, indent)
        }
        val dayAmount = booking.dailyEarningsContribution?.let { " · This day: ₹${it.asRupees()}" }.orEmpty()
        drawWrapped("Booking total: ₹${booking.totalAmount.asRupees()}$dayAmount", bodyPaint, indent)
        if (booking.bookingSource == "admin") drawWrapped("Admin booking", mutedPaint, indent)
        booking.customerNote?.takeIf(String::isNotBlank)?.let {
            drawWrapped("Note: $it", mutedPaint, indent)
        }
        y += 4f
        drawSeparator()
    }

    try {
        document.finishPage(page)
        FileOutputStream(pdfFile).use { document.writeTo(it) }
    } finally {
        document.close()
    }
    return pdfFile
}

@Composable
private fun DailyOccupancyBookingsPage(state: StayAdminUiState) {
    val selectedDate = state.selectedOccupancyDate
    val details = state.occupancyDayDetails?.takeIf { it.date == selectedDate }
    if (details == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (state.isLoading) "Loading bookings…" else "No booking details available")
        }
        return
    }
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (details.bookings.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { exportOccupancyDayGuests(context, details) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export guest list as PDF")
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        formatOccupancyDate(details.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${details.bookingCount} ${if (details.bookingCount == 1) "booking" else "bookings"} occupying accommodation"
                    )
                    Text(
                        "Estimated earnings for this day: ₹${details.totalEarnings.asRupees()}",
                        fontWeight = FontWeight.Medium
                    )
                    HorizontalDivider()
                    details.unitTypes.forEach { unit ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(unit.displayName, modifier = Modifier.weight(1f))
                            Text(
                                "${unit.bookedUnits} booked · ${unit.availableUnits} available",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        if (details.bookings.isEmpty()) {
            item { Text("No confirmed bookings occupy accommodation on this date.") }
        } else {
            items(details.bookings, key = { it.id }) { booking ->
                DailyOccupancyBookingCard(booking, details.date)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun DailyOccupancyBookingCard(booking: StayBooking, occupancyDate: String) {
    val accommodationUnits = booking.totalAccommodationUnits
        ?: booking.items.sumOf { it.quantity }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(booking.reference, fontWeight = FontWeight.Bold)
                    Text(
                        if (booking.checkInDate == occupancyDate) {
                            "Checks in today"
                        } else {
                            "Continuing stay"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        booking.status.stayStatusLabel(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            HorizontalDivider()
            Text("Booker: ${booking.contactName}", fontWeight = FontWeight.Medium)
            Text("Phone: ${booking.contactPhone}")
            if (booking.contactEmail.isNotBlank()) Text("Email: ${booking.contactEmail}")
            Text(
                if (booking.bookingSource == "admin") "Source: Admin booking" else "Source: Customer booking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
            Text("Stay: ${booking.checkInDate} → ${booking.checkOutDate}")
            Text("${booking.nightCount} night(s) · ${booking.guestCount} guest(s)")
            Text(
                "$accommodationUnits total accommodation ${if (accommodationUnits == 1) "unit" else "units"}",
                fontWeight = FontWeight.Medium
            )
            booking.items.forEach { item ->
                Text(
                    "${item.quantity} × ${item.unitTypeName} · " +
                        "₹${item.nightlyRate.asRupees()}/night · ₹${item.lineTotal.asRupees()}"
                )
            }

            HorizontalDivider()
            if (booking.discountAmount > 0) {
                Text("Subtotal: ₹${(booking.subtotalAmount ?: booking.totalAmount).asRupees()}")
                Text(
                    "Coupon ${booking.couponCode.orEmpty()}: −₹${booking.discountAmount.asRupees()}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text("Booking total: ₹${booking.totalAmount.asRupees()}", fontWeight = FontWeight.Bold)
            booking.dailyEarningsContribution?.let { amount ->
                Text("Amount allocated to this day: ₹${amount.asRupees()}")
            } ?: Text(
                "Amount for this day is excluded while cancellation is under review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            booking.customerNote?.takeIf(String::isNotBlank)?.let {
                HorizontalDivider()
                Text("Customer note", fontWeight = FontWeight.Medium)
                Text(it)
            }
            booking.cancellation?.let { cancellation ->
                HorizontalDivider()
                Text("Cancellation request", fontWeight = FontWeight.Medium)
                Text("Requested: ${formatStayBookingTimestamp(cancellation.requestedAt)}")
                cancellation.reason?.takeIf(String::isNotBlank)?.let { Text("Reason: $it") }
                Text(
                    if (cancellation.standardFullRefundEligible) {
                        "Eligible for a standard full refund"
                    } else {
                        "Refund is at admin discretion"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()
            booking.createdAt?.let {
                Text(
                    "Booked: ${formatStayBookingTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            booking.confirmedAt?.let {
                Text(
                    "Confirmed: ${formatStayBookingTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title)
        }
    }
}

@Composable
private fun DashboardAction(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdminStayBookingPage(
    modifier: Modifier = Modifier,
    onConfirm: (CreateStayBookingRequest) -> Unit
) {
    var checkInDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var checkOutDate by remember { mutableStateOf(LocalDate.now().plusDays(2)) }
    var threeBed by remember { mutableStateOf("") }
    var fourBed by remember { mutableStateOf("") }
    var fiveBed by remember { mutableStateOf("") }
    var halls by remember { mutableStateOf("") }
    var guestCount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var coupon by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val quantities = listOf(
        "three_bed_room" to threeBed.toIntOrNull(),
        "four_bed_room" to fourBed.toIntOrNull(),
        "five_bed_room" to fiveBed.toIntOrNull(),
        "hall" to halls.toIntOrNull()
    )
    val selectedItems = quantities.filter { (it.second ?: 0) > 0 }
        .map { StayBookingItemRequest(it.first, it.second!!) }
    val canConfirm = !checkOutDate.isBefore(checkInDate.plusDays(1)) &&
        selectedItems.isNotEmpty() &&
        (guestCount.toIntOrNull() ?: 0) > 0 &&
        name.isNotBlank() && phone.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "This records payment as received and confirms the booking immediately.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Stay dates and accommodation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CouponDateButton("Check-in", checkInDate) { selected ->
                    checkInDate = selected
                    if (!checkOutDate.isAfter(selected)) checkOutDate = selected.plusDays(1)
                }
                CouponDateButton("Check-out", checkOutDate) { checkOutDate = it }
                QuantityField("3 Bed rooms", threeBed) { threeBed = it }
                QuantityField("4 Bed rooms", fourBed) { fourBed = it }
                QuantityField("5 Bed rooms", fiveBed) { fiveBed = it }
                QuantityField("Halls", halls) { halls = it }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Guest details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = guestCount,
                    onValueChange = { if (it.all(Char::isDigit)) guestCount = it },
                    label = { Text("Total guests") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(name, { name = it.take(200) }, label = { Text("Guest name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(coupon, { coupon = it.uppercase().take(50) }, label = { Text("Coupon code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = {
                    onConfirm(
                        CreateStayBookingRequest(
                            checkInDate = checkInDate.toString(),
                            checkOutDate = checkOutDate.toString(),
                            items = selectedItems,
                            guestCount = guestCount.toInt(),
                            contactName = name.trim(),
                            // The ViewModel supplies the signed-in admin's email, as the
                            // normal Stay flow does; no email needs to be entered here.
                            contactEmail = "",
                            contactPhone = phone.trim(),
                            couponCode = coupon.trim().ifBlank { null },
                            customerNote = note.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) { Text("Confirm booking") }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun QuantityField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onChange(it.take(2)) },
        label = { Text(label) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminStayCancellationDialog(
    booking: StayBooking,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, String?) -> Unit
) {
    var decision by remember(booking.id) { mutableStateOf("full") }
    var amount by remember(booking.id) { mutableStateOf("") }
    var reason by remember(booking.id) { mutableStateOf("") }
    val partialAmount = amount.toDoubleOrNull()
    val canConfirm = when (decision) {
        "partial" -> (partialAmount ?: 0.0) > 0 && reason.isNotBlank()
        "none" -> reason.isNotBlank()
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel ${booking.reference}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the refund to record. The server enforces the 48-hour refund policy.")
                Row {
                    listOf("full", "partial", "none").forEach { option ->
                        FilterChip(
                            selected = decision == option,
                            onClick = { decision = option },
                            label = { Text(option.replaceFirstChar(Char::uppercase)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                if (decision == "partial") {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                        label = { Text("Refund amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000) },
                    label = { Text(if (decision == "full") "Note (optional)" else "Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(decision, partialAmount, reason.trim().ifBlank { null }) },
                enabled = canConfirm
            ) { Text("Cancel booking") }
        }
    )
}

@Composable
private fun CouponsPage(
    state: StayAdminUiState,
    onCreate: () -> Unit,
    onDeactivate: (StayCoupon) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Create coupon")
            }
        }
        if (state.coupons.isEmpty() && !state.isLoading) {
            item { Text("No coupons created yet.") }
        } else if (state.coupons.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.coupons.forEachIndexed { index, coupon ->
                            CouponRow(coupon, onDeactivate)
                            if (index != state.coupons.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CouponRow(coupon: StayCoupon, onDeactivate: (StayCoupon) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                coupon.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (coupon.status == "active") {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    coupon.status.replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Text("₹${coupon.discountAmount.asRupees()} discount")
        Text(
            "${coupon.startDate} to ${coupon.endDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (coupon.isActive && coupon.status != "expired") {
            OutlinedButton(onClick = { onDeactivate(coupon) }) {
                Text("Deactivate")
            }
        }
    }
}

@Composable
private fun CreateCouponDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Double, String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    val parsedAmount = amount.toDoubleOrNull()
    val canCreate = code.length >= 3 && (parsedAmount ?: 0.0) > 0 && !endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Stay coupon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.uppercase()
                            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                            .take(50)
                    },
                    label = { Text("Coupon code") },
                    supportingText = { Text("Users will type this code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it
                    },
                    label = { Text("Discount amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CouponDateButton("Starts", startDate) { selected ->
                    startDate = selected
                    if (endDate.isBefore(selected)) endDate = selected
                }
                CouponDateButton("Ends", endDate) { endDate = it }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(code, parsedAmount!!, startDate.toString(), endDate.toString())
                },
                enabled = canCreate
            ) { Text("Create") }
        }
    )
}

@Composable
private fun CouponDateButton(label: String, date: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$label: ${date.format(DateTimeFormatter.ofPattern("dd MMM yy"))}")
    }
}

@Composable
private fun BookingPage(
    state: StayAdminUiState,
    emptyMessage: String,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onExport: (() -> Unit)? = null,
    exporting: Boolean = false,
    actions: @Composable RowScope.(StayBooking) -> Unit = {}
) {
    var search by remember(state.bookingStatus) { mutableStateOf(state.bookingSearch) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                search,
                { search = it.take(200) },
                label = { Text("Reference, guest, phone, or email") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSearch(search) }) { Text("Search") }
        }
        onExport?.let { export ->
            OutlinedButton(
                onClick = export,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (exporting) "Preparing PDF…" else "Export last 30 days as PDF")
            }
        }
        if (state.bookings.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyMessage) }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.bookings, key = { it.id }) { booking ->
                    StayBookingCard(booking, showContactDetails = true) { actions(booking) }
                }
                if (state.bookings.size < state.bookingTotal) {
                    item {
                        OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Load more (${state.bookings.size}/${state.bookingTotal})")
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

private fun stayArchiveRange(): Pair<String, String> {
    val today = LocalDate.now()
    return today.minusMonths(1).toString() to today.plusDays(1).toString()
}

private fun exportStayRecords(context: Context, bookings: List<StayBooking>) {
    runCatching {
        val pdfFile = createStayRecordsPdf(context, bookings)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val title = "Stay Records - Last 30 Days"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Stay Records"))
    }.onFailure {
        Toast.makeText(context, "Unable to export Stay records PDF", Toast.LENGTH_LONG).show()
    }
}

private fun createStayRecordsPdf(context: Context, bookings: List<StayBooking>): File {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val pdfFile = File(exportDir, "stay_records_last_30_days.pdf")
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
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
        pageNumber += 1
    }
    fun finishPage() = document.finishPage(page)
    fun ensureSpace(required: Float) {
        if (y + required > pageHeight - margin) {
            finishPage()
            startPage()
        }
    }
    fun drawWrapped(text: String, paint: Paint, extraBottom: Float = 4f) {
        wrapStayPdfText(text, paint, contentWidth).forEach { line ->
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
        compareByDescending<StayBooking> { it.createdAt ?: "" }.thenBy { it.reference }
    )
    val confirmedCount = bookings.count { it.status == "confirmed" }
    val cancelledCount = bookings.count { it.status == "cancelled" }

    startPage()
    drawWrapped("Stay Records", titlePaint, extraBottom = 8f)
    drawWrapped("Rolling 30-day report", sectionPaint)
    drawWrapped(
        "${bookings.size} records · $confirmedCount confirmed · $cancelledCount cancelled",
        bodyPaint,
        extraBottom = 8f
    )
    drawSeparator()

    sortedBookings.forEachIndexed { index, booking ->
        ensureSpace(120f)
        drawWrapped("${index + 1}. ${booking.reference} — ${booking.contactName}", sectionPaint)
        drawWrapped("Phone: ${booking.contactPhone}  |  Email: ${booking.contactEmail}", bodyPaint)
        drawWrapped(
            "Stay: ${booking.checkInDate} to ${booking.checkOutDate} (${booking.nightCount} nights) · ${booking.guestCount} guests",
            bodyPaint
        )
        val rooms = booking.items.joinToString { "${it.quantity} × ${it.unitTypeName}" }
        drawWrapped("Accommodation: ${rooms.ifBlank { "Not available" }}", bodyPaint)
        drawWrapped("Amount: ₹${booking.totalAmount.asRupees()}", bodyPaint)
        drawWrapped("Status: ${booking.status.stayStatusLabel()}", mutedPaint)
        booking.createdAt?.let {
            drawWrapped("Booked: ${formatStayBookingTimestamp(it)}", mutedPaint, extraBottom = 6f)
        }
        drawSeparator()
    }

    finishPage()
    FileOutputStream(pdfFile).use { document.writeTo(it) }
    document.close()
    return pdfFile
}

private fun wrapStayPdfText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val lines = mutableListOf<String>()
    var current = ""
    text.split(" ").forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) current = candidate
        else {
            if (current.isNotBlank()) lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    return lines.ifEmpty { listOf(text) }
}

private fun String.stayStatusLabel(): String = split('_', ' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable
private fun CancellationPage(
    state: StayAdminUiState,
    onReview: (StayCancellation) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.cancellations, key = { it.id }) { request ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(request.reference.orEmpty(), fontWeight = FontWeight.Bold)
                    Text("${request.contactName.orEmpty()} · ${request.contactPhone.orEmpty()}")
                    Text("Original state: ${request.previousBookingStatus.replace('_', ' ')}")
                    Text(
                        if (request.standardFullRefundEligible) {
                            "Full refund required"
                        } else {
                            "Refund at admin discretion"
                        }
                    )
                    request.reason?.let { Text("Reason: $it") }
                    Button(onClick = { onReview(request) }) { Text("Review") }
                }
            }
        }
        if (state.cancellations.size < state.cancellationTotal) {
            item {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun CancellationDecisionDialog(
    request: StayCancellation,
    onDismiss: () -> Unit,
    onDecision: (String, String?, Double?, String?) -> Unit
) {
    var decision by remember(request.id) {
        mutableStateOf(if (request.standardFullRefundEligible) "full" else "none")
    }
    var amount by remember(request.id) { mutableStateOf("") }
    var reason by remember(request.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancellation decision") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.standardFullRefundEligible) {
                    Text("This request is entitled to a full refund.")
                } else {
                    Row {
                        listOf("none", "partial", "full").forEach { option ->
                            FilterChip(
                                selected = decision == option,
                                onClick = { decision = option },
                                label = { Text(option.replaceFirstChar(Char::uppercase)) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
                if (decision == "partial") {
                    OutlinedTextField(
                        amount,
                        { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                        label = { Text("Refund amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                OutlinedTextField(
                    reason,
                    { reason = it.take(1000) },
                    label = { Text("Decision note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (reason.isNotBlank()) onDecision("reject", null, null, reason)
                },
                enabled = reason.isNotBlank()
            ) { Text("Reject request") }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDecision(
                        "approve",
                        decision,
                        amount.toDoubleOrNull(),
                        reason.ifBlank { null }
                    )
                },
                enabled = decision != "partial" || (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Approve cancellation") }
        }
    )
}
