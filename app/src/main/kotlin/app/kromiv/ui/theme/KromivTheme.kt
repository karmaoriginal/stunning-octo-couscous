package app.kromiv.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kromiv.core.model.ThemeMode

private val KromivDarkScheme = darkColorScheme(
    primary = Color(0xFF9F92FF),
    onPrimary = Color(0xFF150B42),
    primaryContainer = Color(0xFF332674),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFF47E5FF),
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF0C4C5B),
    onSecondaryContainer = Color(0xFFC3F5FF),
    tertiary = Color(0xFFF48FB1),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFECEEF6),
    surface = Color(0xFF111420),
    onSurface = Color(0xFFECEEF6),
    surfaceVariant = Color(0xFF1D2234),
    onSurfaceVariant = Color(0xFFA7B0C5),
    surfaceContainerLowest = Color(0xFF080A10),
    surfaceContainerLow = Color(0xFF111420),
    surfaceContainer = Color(0xFF171B2A),
    surfaceContainerHigh = Color(0xFF202538),
    outline = Color(0xFF363E57)
)

private val KromivLightScheme = lightColorScheme(
    primary = Color(0xFF5A45E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E3FF),
    onPrimaryContainer = Color(0xFF190A59),
    secondary = Color(0xFF006B82),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDF0FF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFFC2376A),
    background = Color(0xFFF8F9FD),
    onBackground = Color(0xFF121520),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121520),
    surfaceVariant = Color(0xFFEAEDF5),
    onSurfaceVariant = Color(0xFF535B70),
    outline = Color(0xFFC5CCDD)
)

private val KromivShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val KromivTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun KromivTheme(
    themeMode: ThemeMode,
    amoledDark: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> KromivDarkScheme
        else -> KromivLightScheme
    }

    if (isDark && amoledDark) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0D),
            surfaceContainer = Color(0xFF121218),
            surfaceContainerHigh = Color(0xFF191922)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = KromivShapes,
        typography = KromivTypography,
        content = content
    )
}
