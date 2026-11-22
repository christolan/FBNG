package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
internal fun Modifier.fbngIsland(shape: Shape = FbngShapes.Island): Modifier {
    val scheme = MaterialTheme.colorScheme
    return background(
        Brush.linearGradient(listOf(scheme.surfaceContainerHighest.copy(alpha = .65f), scheme.surface.copy(alpha = .72f))),
        shape,
    )
        .border(Dp.Hairline, scheme.outlineVariant.copy(alpha = .65f), shape)
}

@Composable
internal fun FbngBackdrop() {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        Box(Modifier.fillMaxSize().blur(FbngDimens.glassBlur)
            .background(Brush.radialGradient(
                listOf(Color(0xFF293B51), Color.Transparent), Offset(width * .30f, height * .16f), width * .53f,
            ))
            .background(Brush.radialGradient(
                listOf(Color(0xFF21504B), Color.Transparent), Offset(width * .96f, height * .96f), width * .46f,
            ))
            .background(Brush.radialGradient(
                listOf(Color(0xFF2A263C), Color.Transparent), Offset(width * .06f, height), width * .38f,
            )))
    }
}

@Composable
internal fun FbngPanel(
    modifier: Modifier = Modifier,
    shape: Shape = FbngShapes.Island,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().fbngIsland(shape).padding(FbngDimens.panelInset),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        content = content,
    )
}

@Composable
internal fun FbngEmptyState(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().fbngIsland()) {
        Column(
            Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .heightIn(min = maxHeight).padding(FbngDimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space12, Alignment.CenterVertically),
            content = content,
        )
    }
}
