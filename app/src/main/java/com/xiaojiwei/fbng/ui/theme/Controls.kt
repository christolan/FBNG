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
    Filled,

    Outline,

    Neutral,

    Destructive,

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
    focusableWhenDisabled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        kind == FbngButtonKind.Island -> scheme.surfaceContainerHigh
        !enabled -> scheme.surfaceContainerHighest
        selected -> scheme.primary.copy(alpha = .22f)
        kind == FbngButtonKind.Filled -> scheme.primary
        kind == FbngButtonKind.Destructive -> scheme.error
        kind == FbngButtonKind.Toolbar -> scheme.background
        else -> scheme.surfaceVariant
    }
    val resting = when {
        selected -> scheme.primary.copy(alpha = .65f)
        kind == FbngButtonKind.Toolbar -> scheme.outlineVariant
        kind == FbngButtonKind.Outline -> scheme.outlineVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled && kind == FbngButtonKind.Island -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
        !enabled -> scheme.onSurfaceVariant
        selected -> scheme.onSurface
        kind == FbngButtonKind.Filled -> scheme.onPrimary
        kind == FbngButtonKind.Destructive -> scheme.onError
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
                    focusableWhenDisabled = focusableWhenDisabled,
                )
                .heightIn(min = FbngDimens.buttonHeight)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
