package com.mybus.app.ui.aartipath

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybus.app.data.repository.AartiPathRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

const val AARTI_PATH_READER_ROUTE = "aarti_path_reader/{id}?title={title}"

fun aartiPathReaderRoute(id: String, title: String): String =
    "aarti_path_reader/${Uri.encode(id)}?title=${Uri.encode(title)}"

/** Text size steps, as zoom over fit-width (100% to 300%). */
private val ZOOM_STEPS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f
private const val DOUBLE_TAP_ZOOM = 2f
private const val STEP_EPSILON = 0.01f
private const val LEVEL_LABEL_MILLIS = 1_500L

private fun nextZoomStep(zoom: Float): Float? = ZOOM_STEPS.firstOrNull { it > zoom + STEP_EPSILON }
private fun previousZoomStep(zoom: Float): Float? = ZOOM_STEPS.lastOrNull { it < zoom - STEP_EPSILON }

/** The step at or just above [zoom], so pages are drawn at least as wide as they show. */
private fun renderStep(zoom: Float): Float = ZOOM_STEPS.firstOrNull { it >= zoom - STEP_EPSILON } ?: MAX_ZOOM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AartiPathReaderScreen(
    onBack: () -> Unit,
    viewModel: AartiPathReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state is ReaderState.Ready && !sharing,
                        onClick = {
                            sharing = true
                            scope.launch {
                                val file = viewModel.fileForSharing()
                                if (file != null) sharePdf(context, file, viewModel.title)
                                else Toast.makeText(context, "Couldn't share this PDF", Toast.LENGTH_SHORT).show()
                                sharing = false
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(readerBackground())
        ) {
            when (val current = state) {
                is ReaderState.Downloading -> DownloadingView(current.progress)
                is ReaderState.Failed -> FailedView(current.message, onRetry = viewModel::open)
                is ReaderState.Ready -> PdfPages(
                    pageAspects = current.pageAspects,
                    initialZoom = viewModel.initialZoom,
                    onZoomSettled = viewModel::saveZoom,
                    renderPage = viewModel::renderPage
                )
            }
        }
    }
}

@Composable
private fun DownloadingView(progress: Float?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (progress != null) "Downloading… ${(progress * 100).roundToInt()}%" else "Downloading…",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "It stays on your phone after this, so it opens without internet next time.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedTextColor(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FailedView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (message == AartiPathRepository.FIRST_OPEN_OFFLINE_MESSAGE) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = mutedTextColor()
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Pages top to bottom at screen width. A−/A+ step the text size between 100% and 300%; pinch
 * and double-tap zoom too. Pages are laid out wider rather than scaled as a picture, so the
 * list still scrolls normally and one finger pans sideways while zoomed. The chosen size is
 * remembered for every PDF.
 */
@Composable
private fun PdfPages(
    pageAspects: List<Float>,
    initialZoom: Float,
    onZoomSettled: (Float) -> Unit,
    renderPage: suspend (Int, Int) -> Bitmap?
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val padPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }
        val basePageW = (viewportW - 2 * padPx).coerceAtLeast(1f)

        val startZoom = remember { initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM) }
        var zoom by remember { mutableFloatStateOf(startZoom) }
        var settledZoom by remember { mutableFloatStateOf(startZoom) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        val listState = rememberLazyListState()

        // "150%" shows in the page chip for a moment after the size changes.
        var levelLabel by remember { mutableStateOf<String?>(null) }
        var levelChange by remember { mutableIntStateOf(0) }
        LaunchedEffect(levelChange) {
            if (levelChange > 0) {
                delay(LEVEL_LABEL_MILLIS)
                levelLabel = null
            }
        }

        val zoomer = remember(viewportW, pageAspects) {
            PageZoomer(viewportW, basePageW, padPx, gapPx, pageAspects, listState)
        }

        fun applyZoom(factor: Float, focus: Offset, pan: Offset) {
            val result = zoomer.zoom(zoom, offsetX, factor, focus, pan)
            zoom = result.zoom
            offsetX = result.offsetX
        }

        fun settle() {
            if (abs(zoom - settledZoom) > STEP_EPSILON / 2) {
                settledZoom = zoom
                onZoomSettled(zoom)
                levelLabel = "${(zoom * 100).roundToInt()}%"
                levelChange++
            }
        }

        /** A−/A+: zoom around the middle of the screen so the reading place stays put. */
        fun stepTo(target: Float) {
            applyZoom(target / zoom, Offset(viewportW / 2f, viewportH / 2f), Offset.Zero)
            settle()
        }

        // Visible pages are redrawn at the zoomed width (the renderer caps it at 2,500 px).
        val renderWidth = (basePageW * renderStep(settledZoom)).roundToInt()
        val currentPage by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                (info.visibleItemsInfo.firstOrNull { center in it.offset..(it.offset + it.size) }
                    ?: info.visibleItemsInfo.firstOrNull())?.index ?: 0
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(zoomer) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pinched = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                pinched = true
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (centroid != Offset.Unspecified) {
                                    applyZoom(event.calculateZoom(), centroid, event.calculatePan())
                                }
                                // Keep the list from scrolling while two fingers zoom.
                                event.changes.forEach { it.consume() }
                            } else if (pressed == 1 && !pinched && zoom > 1f) {
                                // One finger: the list scrolls up and down; this pans sideways.
                                val panX = event.calculatePan().x
                                if (panX != 0f) applyZoom(1f, Offset.Zero, Offset(panX, 0f))
                            }
                        } while (event.changes.any { it.pressed })
                        if (pinched) settle()
                    }
                }
                .pointerInput(zoomer) {
                    detectTapGestures(onDoubleTap = { position ->
                        val target = if (zoom > MIN_ZOOM + STEP_EPSILON) MIN_ZOOM else DOUBLE_TAP_ZOOM
                        applyZoom(target / zoom, position, Offset.Zero)
                        settle()
                    })
                }
        ) {
            val contentW = viewportW * zoom
            val pageW = basePageW * zoom
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(with(density) { contentW.toDp() })
                    .offset { IntOffset(offsetX.roundToInt(), 0) },
                contentPadding = PaddingValues(with(density) { (padPx * zoom).toDp() }),
                verticalArrangement = Arrangement.spacedBy(with(density) { (gapPx * zoom).toDp() })
            ) {
                items(pageAspects.size) { index ->
                    PdfPageItem(
                        index = index,
                        widthPx = pageW,
                        aspect = pageAspects[index],
                        renderWidth = renderWidth,
                        renderPage = renderPage
                    )
                }
            }

            // Page chip at the bottom start, text size buttons at the bottom end.
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp)
            ) {
                Text(
                    levelLabel ?: "Page ${currentPage + 1} of ${pageAspects.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            TextSizeButtons(
                canShrink = previousZoomStep(zoom) != null,
                canGrow = nextZoomStep(zoom) != null,
                onShrink = { previousZoomStep(zoom)?.let(::stepTo) },
                onGrow = { nextZoomStep(zoom)?.let(::stepTo) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun TextSizeButtons(
    canShrink: Boolean,
    canGrow: Boolean,
    onShrink: () -> Unit,
    onGrow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.7f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextSizeButton(label = "A−", description = "Smaller text", fontSizeSp = 16, enabled = canShrink, onClick = onShrink)
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            TextSizeButton(label = "A+", description = "Larger text", fontSizeSp = 21, enabled = canGrow, onClick = onGrow)
        }
    }
}

@Composable
private fun TextSizeButton(
    label: String,
    description: String,
    fontSizeSp: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(56.dp, 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.38f)
        )
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    widthPx: Float,
    aspect: Float,
    renderWidth: Int,
    renderPage: suspend (Int, Int) -> Bitmap?
) {
    val density = LocalDensity.current
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, renderWidth) {
        // Keep showing the previous bitmap until the sharper one is ready.
        renderPage(index, renderWidth)?.let { bitmap = it }
    }
    Box(
        Modifier
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { (widthPx * aspect).toDp() })
            .shadow(2.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            val image = remember(current) { current.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium
            )
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.Gray)
        }
    }
}

/**
 * Zoom maths for [PdfPages]. Everything in the list (pages, gaps, padding) scales by the same
 * factor, so the point under the fingers can be kept in place exactly.
 */
private class PageZoomer(
    private val viewportW: Float,
    private val basePageW: Float,
    private val padPx: Float,
    private val gapPx: Float,
    private val pageAspects: List<Float>,
    private val listState: LazyListState
) {
    data class Result(val zoom: Float, val offsetX: Float)

    private fun itemExtent(index: Int, zoom: Float) = basePageW * zoom * pageAspects[index] + gapPx * zoom

    fun zoom(zoom: Float, offsetX: Float, factor: Float, focus: Offset, pan: Offset): Result {
        val newZoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val z = newZoom / zoom

        // Horizontal: keep the focus point under the fingers, then pan.
        val maxShift = viewportW * newZoom - viewportW
        val newOffsetX = ((offsetX - focus.x) * z + focus.x + pan.x).coerceIn(-maxShift, 0f)

        if (z != 1f || pan.y != 0f) {
            // Vertical: distance from the top of the content to the top of the screen.
            var scrollY = 0f
            for (i in 0 until listState.firstVisibleItemIndex.coerceAtMost(pageAspects.size)) {
                scrollY += itemExtent(i, zoom)
            }
            scrollY += listState.firstVisibleItemScrollOffset
            val newScrollY = ((scrollY + focus.y) * z - focus.y - pan.y).coerceAtLeast(0f)

            var index = 0
            var start = 0f
            while (index < pageAspects.lastIndex && start + itemExtent(index, newZoom) <= newScrollY) {
                start += itemExtent(index, newZoom)
                index++
            }
            listState.requestScrollToItem(index, (newScrollY - start).roundToInt())
        }
        return Result(newZoom, newOffsetX)
    }
}

private fun sharePdf(context: Context, file: File, title: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }.onFailure {
        Toast.makeText(context, "Couldn't share this PDF", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun readerBackground(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF2A2A2A) else Color(0xFFE6E6E6)
