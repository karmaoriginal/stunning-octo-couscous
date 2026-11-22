package app.kromiv.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kromiv.BuildConfig
import app.kromiv.core.model.ThemeMode
import app.kromiv.ui.KromivUiState

@Composable
fun SettingsScreen(
    uiState: KromivUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onFpsLimitChange: (Int) -> Unit,
    onMuteDefaultChange: (Boolean) -> Unit,
    onPauseBatterySaverChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onMaxCacheChange: (Int) -> Unit,
    onAiUpscalerChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = uiState.settings
    val stats = uiState.storageStats

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "settings_title") {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item(key = "settings_appearance") {
            SettingsCard(title = "Apariencia") {
                Text(
                    text = "Tema",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to "Sistema",
                        ThemeMode.LIGHT to "Claro",
                        ThemeMode.DARK to "Oscuro"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = s.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }
                SettingsSwitchRow(
                    title = "Negro puro AMOLED",
                    subtitle = "Fondo #000000 en modo oscuro para ahorrar batería",
                    checked = s.amoledDark,
                    onCheckedChange = onAmoledChange
                )
                SettingsSwitchRow(
                    title = "Color dinámico (Material You)",
                    subtitle = "Adapta la paleta a tu fondo en Android 12+",
                    checked = s.dynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
            }
        }

        item(key = "settings_live") {
            SettingsCard(title = "Live Wallpaper y Batería") {
                Text(
                    text = "Límite de FPS en fondo animado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = s.liveFpsLimit == fps,
                            onClick = { onFpsLimitChange(fps) },
                            label = { Text("$fps fps") }
                        )
                    }
                }
                SettingsSwitchRow(
                    title = "Silenciar videos por defecto",
                    subtitle = "Recomendado para evitar ruido inesperado",
                    checked = s.muteLiveByDefault,
                    onCheckedChange = onMuteDefaultChange
                )
                SettingsSwitchRow(
                    title = "Pausar con ahorro de batería",
                    subtitle = "También se pausa automáticamente con pantalla apagada o launcher oculto",
                    checked = s.pauseLiveOnBatterySaver,
                    onCheckedChange = onPauseBatterySaverChange
                )
            }
        }

        item(key = "settings_storage") {
            SettingsCard(title = "Red, Caché y Almacenamiento") {
                SettingsSwitchRow(
                    title = "Descargar solo por Wi-Fi",
                    subtitle = "Evita consumo de datos móviles en la tienda",
                    checked = s.wifiOnlyDownloads,
                    onCheckedChange = onWifiOnlyChange
                )
                Text(
                    text = "Límite máximo de caché de imágenes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(128, 256, 512, 1024).forEach { mb ->
                        FilterChip(
                            selected = s.maxCacheMb == mb,
                            onClick = { onMaxCacheChange(mb) },
                            label = { Text(if (mb == 1024) "1 GB" else "$mb MB") }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Importados: ${Formatter.formatShortFileSize(context, stats.importsBytes)} · " +
                        "Tienda: ${Formatter.formatShortFileSize(context, stats.downloadsBytes)} · " +
                        "Mejorados: ${Formatter.formatShortFileSize(context, stats.enhancedBytes)} · " +
                        "Caché: ${Formatter.formatShortFileSize(context, stats.imageCacheBytes + stats.partialDownloadsBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Limpiar caché y descargas temporales")
                }
            }
        }

        item(key = "settings_experimental") {
            SettingsCard(title = "Funciones experimentales") {
                SettingsSwitchRow(
                    title = "Escalador IA ligero (Opcional)",
                    subtitle = "Desactivado por defecto. Interfaz lista para conectar modelo TFLite/NCNN sin aumentar tamaño del APK",
                    checked = s.aiUpscalerEnabled,
                    onCheckedChange = onAiUpscalerChange
                )
            }
        }

        item(key = "settings_about") {
            SettingsCard(title = "Acerca de Kromiv") {
                Text(
                    text = "Kromiv v${BuildConfig.VERSION_NAME} · Licencia MIT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Catálogo CC0 publicado en GitHub Pages y assets alojados en GitHub Releases. Sin rastreadores ni permisos de almacenamiento amplios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
