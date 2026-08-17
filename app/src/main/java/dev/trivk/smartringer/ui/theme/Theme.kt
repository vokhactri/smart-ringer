package dev.trivk.smartringer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F5E92),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF07164B),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF76546E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF202F60),
    primaryContainer = Color(0xFF374778),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFFE5BAD7),
)

@Composable
fun SmartRingerTheme(customSeedArgb: Int? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colors = when {
        customSeedArgb != null && darkTheme -> customDarkColorScheme(customSeedArgb)
        customSeedArgb != null -> customLightColorScheme(customSeedArgb)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Material 3 does not expose its wallpaper seed generator as public Compose API. These palettes
 * preserve the stock neutral surfaces while deriving the accent families from the user's seed.
 * Blending the seed towards each background also keeps arbitrary bright/dark input readable.
 */
private fun customLightColorScheme(seedArgb: Int) = seedFamilies(seedArgb, dark = false).let { colors ->
    lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.tertiary,
        onTertiary = colors.onTertiary,
        tertiaryContainer = colors.tertiaryContainer,
        onTertiaryContainer = colors.onTertiaryContainer,
    )
}

private fun customDarkColorScheme(seedArgb: Int) = seedFamilies(seedArgb, dark = true).let { colors ->
    darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.tertiary,
        onTertiary = colors.onTertiary,
        tertiaryContainer = colors.tertiaryContainer,
        onTertiaryContainer = colors.onTertiaryContainer,
    )
}

private data class SeedFamilies(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

private fun seedFamilies(seedArgb: Int, dark: Boolean): SeedFamilies {
    val seed = Color(seedArgb)
    val secondarySeed = blend(seed, Color(0xFF6A6572), 0.38f)
    val tertiarySeed = rotateHue(seed, 55f)

    fun family(color: Color): Array<Color> = if (dark) {
        val accent = blend(color, Color.White, 0.42f)
        val container = blend(color, Color.Black, 0.48f)
        arrayOf(accent, readableOn(accent), container, readableOn(container))
    } else {
        val accent = blend(color, Color.Black, 0.25f)
        val container = blend(color, Color.White, 0.72f)
        arrayOf(accent, readableOn(accent), container, readableOn(container))
    }

    val primary = family(seed)
    val secondary = family(secondarySeed)
    val tertiary = family(tertiarySeed)
    return SeedFamilies(
        primary[0], primary[1], primary[2], primary[3],
        secondary[0], secondary[1], secondary[2], secondary[3],
        tertiary[0], tertiary[1], tertiary[2], tertiary[3],
    )
}

private fun blend(from: Color, to: Color, amount: Float) = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

private fun readableOn(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.54f) Color(0xFF111318) else Color.White
}

private fun rotateHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
        hsv,
    )
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
