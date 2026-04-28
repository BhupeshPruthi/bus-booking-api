package com.mybus.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Same corner radius as the home “Login” [OutlinedButton] (guest upcoming trips card). */
val OutlinedControlCornerRadius = 20.dp

fun outlinedControlShape() = RoundedCornerShape(OutlinedControlCornerRadius)

@Composable
fun outlinedControlLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge

/**
 * Outlined button matching home Login: [outlinedControlShape] + [outlinedControlLabelStyle] for typical single-line labels.
 */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = outlinedControlShape(),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun AppOutlinedButtonLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = text, style = outlinedControlLabelStyle())
    }
}

/** Read-only pill with the same outline radius and label style as [AppOutlinedButtonLabel] (e.g. “Round Way” on bus cards). */
@Composable
fun AppOutlinedPillReadOnly(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    borderColor: Color? = null,
    containerColor: Color? = null,
) {
    Surface(
        modifier = modifier,
        shape = outlinedControlShape(),
        border = BorderStroke(1.dp, borderColor ?: MaterialTheme.colorScheme.outline),
        color = containerColor ?: MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            style = outlinedControlLabelStyle(),
            color = textColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(ButtonDefaults.ContentPadding),
        )
    }
}
