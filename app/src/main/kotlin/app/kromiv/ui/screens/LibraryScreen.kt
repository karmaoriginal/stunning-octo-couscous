@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.kromiv.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kromiv.R
import app.kromiv.core.model.WallpaperItem
import app.kromiv.ui.KromivUiState
import app.kromiv.ui.LibraryFilter
import app.kromiv.ui.components.WallpaperCard

@Composable
fun LibraryScreen(
    uiState: KromivUiState,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectFilter: (LibraryFilter) -> Unit,
    onImportUris: (List<Uri>) -> Unit,
    onClearCache: () -> Unit,
    onItemClick: (WallpaperItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEnhanceClick: (WallpaperItem) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    var renameTarget by remember { mutableStateOf<WallpaperItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<WallpaperItem?>(null) }
    var actionTarget by remember { mutableStateOf<WallpaperItem?>(null) }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(164.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "lib_header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Biblioteca",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Storage card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${uiState.storageStats.itemCount} fondos guardados",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Total usado: ${Formatter.formatShortFileSize(context, uiState.storageStats.totalManagedBytes)} · Caché: ${Formatter.formatShortFileSize(context, uiState.storageStats.imageCacheBytes)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onClearCache) {
                                Text("Limpiar caché")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_photo_library),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Galería")
                            }
                            FilledTonalButton(
                                onClick = {
                                    safDocumentLauncher.launch(
                                        arrayOf("image/*", "video/*")
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder_open),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Archivos SAF")
                            }
                        }

                        if (uiState.isImporting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LibraryFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = uiState.filterState.libraryFilter == filter,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }

        if (uiState.libraryItems.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "lib_empty") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tu biblioteca está vacía",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Importa fotos (JPG, PNG, WEBP, HEIC, AVIF, GIF, BMP) o videos (MP4, MKV, WEBM, MOV, 3GP) sin permisos amplios, o descarga desde Explorar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(items = uiState.libraryItems, key = { it.id }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WallpaperCard(
                        item = item,
                        downloadProgress = null,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedKeyPrefix = "lib",
                        onClick = { onItemClick(item) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                        onEnhanceClick = { onEnhanceClick(item) }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Formatter.formatShortFileSize(
                                context,
                                if (item.enhancedSizeBytes > 0L) item.enhancedSizeBytes else item.fileSizeBytes
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            TextButton(onClick = {
                                renameTarget = item
                                renameText = item.title
                            }) {
                                Text("Renombrar")
                            }
                            TextButton(onClick = { actionTarget = item }) {
                                Text("Más")
                            }
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(item.title) },
            text = {
                Text("${item.width}×${item.height} · ${item.format.extension.uppercase()} · ${item.license}")
            },
            confirmButton = {
                if (item.isVideo) {
                    TextButton(onClick = {
                        actionTarget = null
                        onEnhanceClick(item)
                    }) {
                        Text("Mejorar video")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionTarget = null
                    deleteTarget = item
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar fondo") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nuevo nombre") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(item.id, renameText)
                    renameTarget = null
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar \"${item.title}\"?") },
            text = { Text("Se borrará el archivo local y su versión mejorada si existe.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.id)
                    deleteTarget = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
