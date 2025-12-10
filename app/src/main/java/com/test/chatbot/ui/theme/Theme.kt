package com.test.chatbot.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Полностью чёрная тема
private val BlackColorScheme = darkColorScheme(
    primary = AccentYellow,              // Ядовито-жёлтый акцент
    onPrimary = PureBlack,               // Чёрный текст на жёлтом
    primaryContainer = Color(0xFF2D2D00),
    onPrimaryContainer = AccentYellow,
    
    secondary = AccentBlue,              // Голубой акцент
    onSecondary = PureBlack,
    secondaryContainer = Color(0xFF002D3D),
    onSecondaryContainer = AccentBlue,
    
    tertiary = AccentPurple,             // Фиолетовый акцент
    onTertiary = TextWhite,
    tertiaryContainer = Color(0xFF2D1F4D),
    onTertiaryContainer = AccentPurple,
    
    background = PureBlack,              // Чисто чёрный фон
    onBackground = TextWhite,            // Белый текст
    
    surface = DarkGray,                  // Тёмно-серые поверхности
    onSurface = TextWhite,
    surfaceVariant = MediumGray,
    onSurfaceVariant = TextGray,
    
    outline = LightGray,
    outlineVariant = Color(0xFF3D3D3D),
    
    error = Color(0xFFFF5555),
    onError = PureBlack,
    errorContainer = Color(0xFF4D1F1F),
    onErrorContainer = Color(0xFFFFAAAA),
    
    inverseSurface = TextWhite,
    inverseOnSurface = PureBlack,
    inversePrimary = Color(0xFF8B9900),
    
    scrim = PureBlack
)

@Composable
fun ChatBotTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = BlackColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Чёрный статус бар
            window.statusBarColor = PureBlack.toArgb()
            // Светлые иконки на тёмном фоне
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
