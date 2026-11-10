package com.mangapill.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mangapill.app.R

/** Outfit (bundled variable font): the typeface AniStream and CineStream use, so the apps read alike. */
@OptIn(ExperimentalTextApi::class)
val OutfitFamily = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black
    ).map { weight ->
        Font(
            resId = R.font.outfit_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

fun outfitTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = OutfitFamily),
        displayMedium = base.displayMedium.copy(fontFamily = OutfitFamily),
        displaySmall = base.displaySmall.copy(fontFamily = OutfitFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = OutfitFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = OutfitFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = OutfitFamily),
        titleLarge = base.titleLarge.copy(fontFamily = OutfitFamily),
        titleMedium = base.titleMedium.copy(fontFamily = OutfitFamily),
        titleSmall = base.titleSmall.copy(fontFamily = OutfitFamily),
        // bodyLarge is the default text style: no extra tracking or fixed line height.
        bodyLarge = base.bodyLarge.copy(fontFamily = OutfitFamily, letterSpacing = 0.sp, lineHeight = TextUnit.Unspecified),
        bodyMedium = base.bodyMedium.copy(fontFamily = OutfitFamily),
        bodySmall = base.bodySmall.copy(fontFamily = OutfitFamily),
        labelLarge = base.labelLarge.copy(fontFamily = OutfitFamily),
        labelMedium = base.labelMedium.copy(fontFamily = OutfitFamily),
        labelSmall = base.labelSmall.copy(fontFamily = OutfitFamily)
    )
}
