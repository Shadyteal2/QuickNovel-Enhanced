package com.lagradost.quicknovel.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ExpressiveShapes {

    /**
     * Returns an asymmetric shape with diagonal corners rounded more than the others.
     */
    fun asymmetricShape(baseCorner: Dp): Shape {
        return RoundedCornerShape(
            topStart = baseCorner * 1.6f,
            topEnd = baseCorner * 0.5f,
            bottomStart = baseCorner * 0.5f,
            bottomEnd = baseCorner * 1.6f
        )
    }

    /**
     * Dynamically remembers and morphs shape between standard symmetrical and expressive asymmetric
     * based on active preferences and pressed/selected state.
     */
    @Composable
    fun rememberMorphingShape(
        baseCorner: Dp = 16.dp,
        isPressed: Boolean = false,
        active: Boolean = rememberAsymmetricShapesEnabled()
    ): Shape {
        if (!active) {
            // When setting is disabled, return symmetrical corners with a slight spring on press
            val animatedRadius by animateDpAsState(
                targetValue = if (isPressed) baseCorner * 0.85f else baseCorner,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "sym_shape_morph"
            )
            return remember(animatedRadius) { RoundedCornerShape(animatedRadius) }
        }

        // When asymmetric setting is enabled, animate individual corners separately using spring physics
        val topStartRadius by animateDpAsState(
            targetValue = if (isPressed) baseCorner * 1.1f else baseCorner * 1.6f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "ts_shape_morph"
        )
        val topEndRadius by animateDpAsState(
            targetValue = if (isPressed) baseCorner * 1.0f else baseCorner * 0.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "te_shape_morph"
        )
        val bottomStartRadius by animateDpAsState(
            targetValue = if (isPressed) baseCorner * 1.0f else baseCorner * 0.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "bs_shape_morph"
        )
        val bottomEndRadius by animateDpAsState(
            targetValue = if (isPressed) baseCorner * 1.1f else baseCorner * 1.6f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "be_shape_morph"
        )

        return remember(topStartRadius, topEndRadius, bottomStartRadius, bottomEndRadius) {
            RoundedCornerShape(
                topStart = topStartRadius,
                topEnd = topEndRadius,
                bottomStart = bottomStartRadius,
                bottomEnd = bottomEndRadius
            )
        }
    }
}
