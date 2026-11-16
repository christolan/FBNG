package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

enum class FbngButtonKind {
    /** Electric-blue fill. The focus ring is onPrimary, because primary-on-primary disappears. */
    Filled,

    /** Neutral raised action with a quiet resting outline. */
    Outline,

    Neutral,

    Toolbar,

    Island,
}

@Composable
fun FbngButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: FbngButtonKind = FbngButtonKind.Filled,
    contentPadding: PaddingValues = PaddingValues(horizontal = FbngDimens.space16, vertical = FbngDimens.space8),
    selected: Boolean = false,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        kind == FbngButtonKind.Island -> scheme.surfaceContainerHigh
        !enabled -> scheme.surfaceContainerHighest
        selected -> scheme.secondaryContainer
        kind == FbngButtonKind.Filled -> scheme.primary
        kind == FbngButtonKind.Toolbar -> scheme.background
        else -> scheme.surfaceVariant
    }
    val resting = when {
        kind == FbngButtonKind.Toolbar -> if (selected) scheme.secondary else scheme.outlineVariant
        kind == FbngButtonKind.Outline -> scheme.outlineVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val focused = if (kind == FbngButtonKind.Filled && !selected) scheme.onPrimary else scheme.primary
    val contentColor = when {
        !enabled && kind == FbngButtonKind.Island -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
        !enabled -> scheme.onSurfaceVariant
        selected -> scheme.secondary
        kind == FbngButtonKind.Filled -> scheme.onPrimary
        else -> scheme.onSurface
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .fbngFocusTarget(
                    onClick = onClick,
                    container = container,
                    enabled = enabled,
                    role = role,
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
