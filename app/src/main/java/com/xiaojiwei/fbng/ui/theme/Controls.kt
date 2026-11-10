package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FbngButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            // Material3 fades the disabled label to 38% and the fill to 10%.
            // On this ink that composite is about 2.2:1, so the blocked play
            // label and the not-ready save/load labels were dark on dark.
            disabledContainerColor = scheme.surfaceContainerHighest,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        content = content,
    )
}
