package com.mybus.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.mybus.app.R

/** Same shuffled order repeated this many times so the pager can scroll on and on. */
private const val GalleryRepeatCount = 5

/**
 * Horizontal image carousel with pager-offset parallax and a dot page indicator.
 * Images are repeated [GalleryRepeatCount] times in one long list; dots follow the unique image (modulo).
 */
@Composable
fun GalleryHomeSection() {
    val baseIds = remember {
        GalleryImages.drawableIds.shuffled()
    }
    if (baseIds.isEmpty()) return

    val expandedIds = remember(baseIds) {
        List(GalleryRepeatCount) { baseIds }.flatten()
    }

    val initialPage = remember(baseIds.size, expandedIds.size) {
        // Start near the middle so there is room to swipe both ways through repeats
        (baseIds.size * 2).coerceIn(0, (expandedIds.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { expandedIds.size }
    )

    val dotIndex = pagerState.currentPage % baseIds.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Gallery",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            pageSpacing = 14.dp
        ) { page ->
            val drawableId = expandedIds[page]
            GalleryParallaxImage(
                drawableId = drawableId,
                page = page,
                pagerState = pagerState,
            )
        }

        GalleryPageDots(
            pageCount = baseIds.size,
            currentPage = dotIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

/** Base width:height for gallery image area (portrait 3:4); extra height added below. */
private const val GalleryWidthOverHeight = 3f / 4f

/** Extra height (dp) added to the gallery card beyond the 3:4 frame — applied to the image box. */
private val GalleryCardExtraHeight = 40.dp

/** How far the bitmap shifts horizontally vs the pager (fraction of card width per 1.0 page offset). */
private const val ParallaxHorizontalFraction = 0.34f

/** Extra zoom so crop edges stay covered while translating during parallax. */
private const val ParallaxImageScale = 1.26f

@Composable
private fun GalleryParallaxImage(
    drawableId: Int,
    page: Int,
    pagerState: PagerState,
) {
    val pageOffset =
        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val cardHeight = maxWidth / GalleryWidthOverHeight + GalleryCardExtraHeight
        val isGallery01 = drawableId == R.drawable.gallery_01
        // gallery_01: show full bitmap (no crop) when aspect ratio differs from the 3:4 slot; parallax zoom off so Fit isn’t re-cropped.
        val contentScale = if (isGallery01) ContentScale.Fit else ContentScale.Crop
        val layerScale = if (isGallery01) 1f else ParallaxImageScale
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        scaleX = layerScale
                        scaleY = layerScale
                        translationX = pageOffset * widthPx * ParallaxHorizontalFraction
                    },
            )
        }
    }
}

@Composable
private fun GalleryPageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
