package com.xiaojiwei.fbng.ui.theme

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/** Flat tonal islands; the surrounding layout supplies the dark gutter. */
@Composable
internal fun Modifier.fbngIsland(panel: Boolean = true): Modifier {
    val shape = RoundedCornerShape(if (panel) FbngDimens.panelRadius else FbngDimens.cardRadius)
    return clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
}

@Composable
internal fun FbngPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().fbngIsland().padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        content = content,
    )
}

/** A bounded island that centers short empty states and scrolls long text. */
@Composable
internal fun FbngEmptyState(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().fbngIsland()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(FbngDimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space12, Alignment.CenterVertically),
            content = content,
        )
    }
}
