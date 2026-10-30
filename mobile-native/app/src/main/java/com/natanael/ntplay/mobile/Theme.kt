package com.natanael.ntplay.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta do NTPlay, a mesma da TV desde a 1.6.0: azul-marinho escuro com ciano e roxo do logo.
val Bg = Color(0xFF070B1A)
val Bg2 = Color(0xFF0E1430)
val PanelC = Color(0xFF111838)
val CardC = Color(0xFF182046)
val Purple = Color(0xFF8B5CF6)
val Cyan = Color(0xFF3CE0E6)
val Lilac = Color(0xFFB27CFF)
val Muted = Color(0xFF9FB0D6)
val Btn = Color(0xFF222B57)
val Danger = Color(0xFFFF9A9A)
val Star = Color(0xFFFFC107)

/**
 * Tema do app de celular.
 *
 * Diferente do da TV de propósito: lá o tema encolhe tudo com `LocalDensity` (UI_SCALE = 0.8) e
 * reserva margem para overscan, porque a TV corta as bordas e o usuário está a três metros.
 * No celular isso só desperdiçaria tela, então a densidade do sistema é respeitada — é ela que
 * carrega a escolha de tamanho de fonte do usuário nas configurações de acessibilidade.
 */
@Composable
fun NtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            secondary = Cyan,
            background = Bg,
            surface = PanelC,
            surfaceVariant = CardC,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
