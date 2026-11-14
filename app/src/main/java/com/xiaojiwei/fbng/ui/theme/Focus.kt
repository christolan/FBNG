package com.xiaojiwei.fbng.ui.theme

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Gamepad focus for a shell control. [focusable] is the first modifier so a
 * caller's [androidx.compose.ui.focus.focusRequester] sits directly above it —
 * anything between the two makes requestFocus() miss the target.
 * An outline marks focus. Disabled targets keep solid colors so their labels
 * remain readable.
 */
@Composable
internal fun Modifier.fbngFocusTarget(
    onClick: () -> Unit,
    container: Color,
    shape: Shape = MaterialTheme.shapes.medium,
    enabled: Boolean = true,
    role: Role = Role.Button,
    restingStroke: Color = Color.Transparent,
    focusedStroke: Color? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val stroke = when {
        !enabled -> Color.Transparent
        focused -> focusedStroke ?: scheme.primary
        else -> restingStroke
    }
    return this
        .focusable(enabled = enabled, interactionSource = interaction)
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
        .border(FbngDimens.focusStroke, stroke, shape)
        .clip(shape)
        .indication(interaction, ripple(color = scheme.primary))
        .background(container)
}
