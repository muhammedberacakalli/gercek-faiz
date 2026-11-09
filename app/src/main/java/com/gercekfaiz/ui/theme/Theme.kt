package com.gercekfaiz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Gerçek Faiz marka renkleri: ikondaki gece mavisi, deniz mavisi ve altın.
 * Telefonun duvar kâğıdından renk alan "dinamik renk" bilerek kapalı:
 * uygulama her telefonda ikonuyla ve mağaza görselleriyle aynı görünsün.
 */

private val Gece = Color(0xFF0A1630)
private val GeceAcik = Color(0xFF13234A)
private val Deniz = Color(0xFF16689A)
private val Altin = Color(0xFFF2C14E)
private val AltinKoyu = Color(0xFF7A5A00)

private val AydinlikRenkler = lightColorScheme(
    primary = Color(0xFF0E3A66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Color(0xFF0A1E3A),
    secondary = AltinKoyu,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBEBC0),      // sonuç kartları
    onSecondaryContainer = Color(0xFF2E2200),
    tertiary = Deniz,
    onTertiary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE2E7EF),
    onSurfaceVariant = Color(0xFF475467),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFEAEEF5),        // alt sekme çubuğu
    surfaceContainerHigh = Color(0xFFE4E9F1),
    surfaceContainerHighest = Color(0xFFDEE4ED), // giriş kartları
    outline = Color(0xFF7A8699),
    outlineVariant = Color(0xFFC7CED9)
)

private val KaranlikRenkler = darkColorScheme(
    primary = Color(0xFFA9C8F0),
    onPrimary = Color(0xFF0A1E3A),
    primaryContainer = Color(0xFF1C3A60),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = Altin,
    onSecondary = Color(0xFF2E2200),
    secondaryContainer = Color(0xFF3B3216),      // sonuç kartları: koyu altın
    onSecondaryContainer = Color(0xFFFCE9B0),
    tertiary = Color(0xFF7CC4EE),
    onTertiary = Color(0xFF00324D),
    background = Gece,
    onBackground = Color(0xFFE6EBF4),
    surface = Gece,
    onSurface = Color(0xFFE6EBF4),
    surfaceVariant = Color(0xFF22304D),
    onSurfaceVariant = Color(0xFFB8C2D4),
    surfaceContainerLowest = Color(0xFF060E20),
    surfaceContainerLow = Color(0xFF0E1B38),
    surfaceContainer = Color(0xFF101E3D),        // alt sekme çubuğu
    surfaceContainerHigh = GeceAcik,
    surfaceContainerHighest = Color(0xFF182A52), // giriş kartları
    outline = Color(0xFF7F8CA3),
    outlineVariant = Color(0xFF34425E)
)

@Composable
fun GercekFaizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KaranlikRenkler else AydinlikRenkler,
        typography = Typography,
        content = content
    )
}
