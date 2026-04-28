@file:OptIn(ExperimentalMaterial3Api::class)

package com.mybus.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Call this from sheet content (e.g. close button) so the sheet animates down before [onDismissRequest] runs.
 * Falls back to no-op if not inside [AppModalBottomSheet].
 */
val LocalAnimatedBottomSheetDismiss = compositionLocalOf<() -> Unit> { { } }

/**
 * Material 3 [ModalBottomSheet] (slide-up + drag-to-dismiss).
 * Put your own close control in [content] (e.g. on the same row as the title).
 *
 * Use [LocalAnimatedBottomSheetDismiss] for the close action so the sheet slides down instead of vanishing.
 *
 * By default [scrimColor] is fully transparent so the content behind the dialog stays visible (no grey dim).
 * Only the sheet panel uses [sheetContainerColor].
 */
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetTopCornerRadius: Dp = 28.dp,
    /** Solid fill for the sheet panel only; area above stays transparent when [scrimColor] is transparent. */
    sheetContainerColor: Color = Color(0xFF12161F),
    /** Overlay on the region above the sheet; use [Color.Transparent] so the rest of the screen is not dimmed. */
    scrimColor: Color = Color.Transparent,
    contentColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val animatedDismiss = remember(sheetState, onDismissRequest) {
        {
            scope.launch {
                sheetState.hide()
                onDismissRequest()
            }
            Unit
        }
    }

    CompositionLocalProvider(LocalAnimatedBottomSheetDismiss provides animatedDismiss) {
        Box(modifier = modifier.fillMaxSize()) {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                shape = RoundedCornerShape(
                    topStart = sheetTopCornerRadius,
                    topEnd = sheetTopCornerRadius,
                ),
                containerColor = sheetContainerColor,
                contentColor = contentColor,
                tonalElevation = 0.dp,
                scrimColor = scrimColor,
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    content()
                }
            }
        }
    }
}
