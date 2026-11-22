package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density

internal object FbngShapes {
    private val panelItemCorner = object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float = with(density) {
            shapeSize.minDimension.coerceAtMost(FbngDimens.buttonHeight.toPx()) * (28f / 116f)
        }
    }
    private val tabCorner = CornerSize(34)

    val Button = RoundedCornerShape(percent = 27)
    val Tab = RoundedCornerShape(tabCorner)
    val ShoulderKey = RoundedCornerShape(percent = 23)
    val ActionKey = RoundedCornerShape(percent = 28)
    val PanelItem = RoundedCornerShape(panelItemCorner)
    val Navigation = RoundedCornerShape(outset(tabCorner))
    val Island = RoundedCornerShape(outset(panelItemCorner))

    private fun outset(inner: CornerSize): CornerSize = object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float = with(density) {
            val inset = FbngDimens.panelInset.toPx()
            val innerSize = Size(
                (shapeSize.width - 2 * inset).coerceAtLeast(0f),
                (shapeSize.height - 2 * inset).coerceAtLeast(0f),
            )
            inner.toPx(innerSize, density) + inset
        }
    }
}
