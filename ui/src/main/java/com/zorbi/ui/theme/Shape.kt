package com.zorbi.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ZorbiCardShape = RoundedCornerShape(16.dp)
val ZorbiButtonShape = RoundedCornerShape(12.dp)
val ZorbiSmallShape = RoundedCornerShape(8.dp)
val ZorbiChipShape = RoundedCornerShape(20.dp)
val ZorbiPillShape = CircleShape

/**
 * Sci-Fi angled parallelogram shape for navigation rail tabs.
 * Slanted at ~25 degrees with proportional height slant.
 */
val SciFiAngledTabShape = GenericShape { size, _ ->
    val slant = size.height * 0.32f
    moveTo(slant, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - slant, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * Sci-Fi chamfered octagonal shape (cut corners) for main gaming HUD panels.
 * 45-degree chamfered corners matching futuristic gaming console HUD.
 */
val SciFiChamferedPanelShape = GenericShape { size, _ ->
    val corner = 32f
    moveTo(corner, 0f)
    lineTo(size.width - corner, 0f)
    lineTo(size.width, corner)
    lineTo(size.width, size.height - corner)
    lineTo(size.width - corner, size.height)
    lineTo(corner, size.height)
    lineTo(0f, size.height - corner)
    lineTo(0f, corner)
    close()
}

val ZorbiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = ZorbiSmallShape,
    medium = ZorbiButtonShape,
    large = ZorbiCardShape,
    extraLarge = RoundedCornerShape(24.dp)
)
