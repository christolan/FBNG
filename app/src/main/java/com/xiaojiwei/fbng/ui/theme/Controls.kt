package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class FbngButtonKind {
    /** Electric-blue fill. The focus ring is onPrimary, because primary-on-primary disappears. */
    Filled,

    /** Neutral raised action with a quiet resting outline. */
    Outline,

    Neutral,
}

@Composable
fun FbngButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: FbngButtonKind = FbngButtonKind.Filled,
    contentPadding: PaddingValues = PaddingValues(horizontal = FbngDimens.space16, vertical = FbngDimens.space8),
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(FbngDimens.controlRadius)
    val container = when {
        !enabled -> scheme.surfaceContainerHighest
        kind != FbngButtonKind.Filled -> scheme.surfaceVariant
        else -> scheme.primary
    }
    val resting = when (kind) {
        FbngButtonKind.Outline -> scheme.outlineVariant.copy(alpha = 0.35f)
        FbngButtonKind.Neutral -> Color.Transparent
        FbngButtonKind.Filled -> Color.Transparent
    }
    val focused = if (kind == FbngButtonKind.Filled) scheme.onPrimary else scheme.primary
    val contentColor = when {
        !enabled -> scheme.onSurfaceVariant
        kind == FbngButtonKind.Filled -> scheme.onPrimary
        else -> scheme.onSurface
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
    Row(
        modifier = modifier
            .fbngFocusTarget(
                onClick = onClick,
                container = container,
                shape = shape,
                enabled = enabled,
                restingStroke = resting,
                focusedStroke = focused,
            )
            .heightIn(min = FbngDimens.buttonHeight)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    }
}
