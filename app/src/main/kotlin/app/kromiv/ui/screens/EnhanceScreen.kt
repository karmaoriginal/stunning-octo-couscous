package app.kromiv.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kromiv.core.model.EnhanceOptions
import app.kromiv.core.model.EnhancePreset
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.WallpaperItem
import app.kromiv.ui.KromivUiState
import app.kromiv.ui.components.BadgePill
import app.kromiv.ui.components.KromivThumbnailImage
import kotlin.math.roundToInt

@Composable
fun EnhanceScreen(
    uiState: KromivUiState,
    onEnqueueEnhance: (String, EnhanceOptions) -> Unit,
    onCancelEnhance: (String) -> Unit,
    onPreviewItem: (WallpaperItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPreset by remember { mutableStateOf(EnhancePreset.FHD_1080P) }
    var targetFps by remember { mutableIntStateOf(uiState.settings.liveFpsLimit) }
    var denoise by remember { mutableStateOf(true) }
    var sharpen by remember { mutableStateOf(true) }
    var stripAudio by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "enhance_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Mejora FFmpeg H.265",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Escalado Lanczos, reducción de ruido hqdn3d, nitidez unsharp y compresión eficiente HEVC en segundo plano (una sola vez).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "enhance_options") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Perfil de recodificación",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnhancePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = { Text(if (preset == EnhancePreset.FHD_1080P) "1080p" else "1440p") }
                            )
                        }
                        FilterChip(
                            selected = targetFps == 30,
                            onClick = { targetFps = 30 },
                            label = { Text("30 fps") }
                        )
                        FilterChip(
                            selected = targetFps == 60,
                            onClick = { targetFps = 60 },
                            label = { Text("60 fps") }
                        )
                    }

                    EnhanceToggleRow("Reducción de ruido (hqdn3d)", denoise) { denoise = it }
                    EnhanceToggleRow("Nitidez (unsharp)", sharpen) { sharpen = it }
                    EnhanceToggleRow("Silenciar pista de audio (-an)", stripAudio) { stripAudio = it }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Escalador IA ligero: ${if (uiState.settings.aiUpscalerEnabled) "Activado (interfaz lista, modelo externo requerido)" else "Desactivado por defecto"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (uiState.enhanceCandidates.isEmpty()) {
            item(key = "enhance_empty") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Importa o descarga un video en tu Biblioteca para escalarlo a 1080p/1440p H.265.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(items = uiState.enhanceCandidates, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KromivThumbnailImage(
                            uri = item.thumbnailUri,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(76.dp, 112.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                when (item.enhanceStatus) {
                                    EnhanceStatus.RECOMMENDED -> BadgePill(
                                        "Recomendado",
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    EnhanceStatus.COMPLETED -> BadgePill(
                                        "HEVC Listo",
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    else -> Unit
                                }
                            }

                            val origSize = Formatter.formatShortFileSize(context, item.fileSizeBytes)
                            val enhSize = if (item.enhancedSizeBytes > 0L) {
                                " → ${Formatter.formatShortFileSize(context, item.enhancedSizeBytes)}"
                            } else ""
                            Text(
                                text = "${item.width}×${item.height} · ${item.codec ?: item.format.extension} · $origSize$enhSize",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (item.enhanceStatus == EnhanceStatus.RUNNING || item.enhanceStatus == EnhanceStatus.QUEUED) {
                                val pct = (item.enhanceProgress * 100f).roundToInt()
                                LinearProgressIndicator(
                                    progress = { item.enhanceProgress.coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (item.enhanceStatus == EnhanceStatus.QUEUED) "En cola…" else "Procesando $pct%",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    OutlinedButton(onClick = { onCancelEnhance(item.id) }) {
                                        Text("Cancelar")
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            onEnqueueEnhance(
                                                item.id,
                                                EnhanceOptions(
                                                    preset = selectedPreset,
                                                    targetFps = targetFps,
                                                    denoiseEnabled = denoise,
                                                    sharpenEnabled = sharpen,
                                                    stripAudio = stripAudio,
                                                    useAiUpscaler = uiState.settings.aiUpscalerEnabled
                                                )
                                            )
                                        }
                                    ) {
                                        Text(
                                            if (item.enhanceStatus == EnhanceStatus.COMPLETED) "Reprocesar" else "Mejorar"
                                        )
                                    }
                                    OutlinedButton(onClick = { onPreviewItem(item) }) {
                                        Text("Probar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhanceToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
