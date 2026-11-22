package com.xiaojiwei.fbng.ui.theme

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
internal fun Modifier.fbngFocusTarget(
    onClick: () -> Unit,
    container: Color,
    enabled: Boolean = true,
    role: Role = Role.Button,
    restingStroke: Color = Color.Transparent,
    focusedStroke: Color? = null,
    focusableWhenDisabled: Boolean = false,
    shape: Shape = FbngShapes.PanelItem,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hasFocus by interaction.collectIsFocusedAsState()
    val focused = hasFocus && LocalWindowInfo.current.isWindowFocused
    val scheme = MaterialTheme.colorScheme
    val stroke = when {
        focused -> focusedStroke ?: scheme.primary
        !enabled -> Color.Transparent
        else -> restingStroke
    }
    return this
        .focusable(enabled = enabled || focusableWhenDisabled, interactionSource = interaction)
        .onKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyUp) return@onKeyEvent false
            val activate = event.key == Key.ButtonA ||
                event.key == Key.Enter ||
                event.key == Key.DirectionCenter ||
                event.key == Key.NumPadEnter ||
                event.key == Key.Spacebar
            if (activate) {
                onClick()
                true
            } else {
                false
            }
        }
        .pointerInput(enabled, onClick) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interaction.tryEmit(press)
                    val released = tryAwaitRelease()
                    interaction.tryEmit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                },
                onTap = { onClick() },
            )
        }
        .semantics(mergeDescendants = true) {
            this.role = role
            if (enabled) onClick { onClick(); true } else disabled()
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val line = FbngDimens.focusStroke.toPx()
                val gap = FbngDimens.focusGap.toPx()
                val outer = gap + line / 2
                val outline = shape.createOutline(size, layoutDirection, this)
                val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
                drawRoundRect(stroke, Offset(-outer, -outer),
                    Size(size.width + 2 * outer, size.height + 2 * outer),
                    CornerRadius(radius + outer), style = Stroke(line))
            }
        }
        .border(Dp.Hairline, if (enabled) restingStroke else Color.Transparent, shape)
        .clip(shape)
        .indication(interaction, ripple(color = scheme.primary))
        .background(container)
}
