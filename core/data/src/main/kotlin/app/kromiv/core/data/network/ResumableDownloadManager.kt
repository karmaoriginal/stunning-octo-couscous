package app.kromiv.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.kromiv.core.data.db.WallpaperDao
import app.kromiv.core.data.db.WallpaperEntity
import app.kromiv.core.data.media.AndroidMediaInspector
import app.kromiv.core.model.CatalogEntry
import app.kromiv.core.model.DownloadProgress
import app.kromiv.core.model.DownloadStatus
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.WallpaperSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class ResumableDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val wallpaperDao: WallpaperDao,
    private val mediaInspector: AndroidMediaInspector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val partDir = File(context.filesDir, "downloads_part").apply { mkdirs() }
    private val storeDir = File(context.filesDir, "wallpapers/store").apply { mkdirs() }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun startOrResume(entry: CatalogEntry, wifiOnly: Boolean) {
        if (activeJobs[entry.id]?.isActive == true) return
        if (wifiOnly && !isWifiConnected()) {
            updateProgress(
                DownloadProgress(
                    id = entry.id,
                    bytesRead = partFileFor(entry).length(),
                    totalBytes = entry.fileSizeBytes,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Solo Wi-Fi activado en ajustes"
                )
            )
            return
        }

        val job = scope.launch {
            val partFile = partFileFor(entry)
            val targetFile = File(storeDir, "${entry.id}.${entry.format.extension}")

            if (targetFile.exists() && targetFile.length() > 0L) {
                registerCompletedEntry(entry, targetFile)
                return@launch
            }

            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                updateProgress(
                    DownloadProgress(
                        id = entry.id,
                        bytesRead = existingBytes,
                        totalBytes = entry.fileSizeBytes,
                        status = DownloadStatus.DOWNLOADING
                    )
                )

                val reqBuilder = Request.Builder().url(entry.downloadUrl)
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                }

                okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 416 && existingBytes > 0L) {
                        // Server reports range exceeds length; treat existing part as candidate or restart cleanly
                        partFile.delete()
                        error("Rango inválido, reintentando")
                    }
                    if (!response.isSuccessful && response.code != 206) {
                        error("HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Respuesta vacía")
                    val isPartial = response.code == 206
                    val append = isPartial && existingBytes > 0L
                    val contentLen = body.contentLength().coerceAtLeast(0L)
                    val totalBytes = when {
                        isPartial && contentLen > 0L -> existingBytes + contentLen
                        !isPartial && contentLen > 0L -> contentLen
                        else -> entry.fileSizeBytes
                    }

                    var written = if (append) existingBytes else 0L
                    FileOutputStream(partFile, append).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            var lastEmitTime = 0L
                            while (isActive) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                written += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime >= 120L) {
                                    lastEmitTime = now
                                    updateProgress(
                                        DownloadProgress(
                                            id = entry.id,
                                            bytesRead = written,
                                            totalBytes = totalBytes,
                                            status = DownloadStatus.DOWNLOADING
                                        )
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    if (!isActive) {
                        updateProgress(
                            DownloadProgress(
                                id = entry.id,
                                bytesRead = partFile.length(),
                                totalBytes = totalBytes,
                                status = DownloadStatus.PAUSED
                            )
                        )
                        return@launch
                    }

                    val expectedSha = entry.sha256?.takeIf { it.isNotBlank() }
                    if (expectedSha != null) {
                        val actualSha = AndroidMediaInspector.computeSha256(partFile)
                        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                            partFile.delete()
                            error("Verificación SHA-256 fallida")
                        }
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                    registerCompletedEntry(entry, targetFile)
                }
            } catch (_: CancellationException) {
                updateProgress(
                    DownloadProgress(
                        id = entry.id,
                        bytesRead = partFile.length(),
                        totalBytes = entry.fileSizeBytes,
                        status = DownloadStatus.PAUSED
                    )
                )
            } catch (t: Throwable) {
                updateProgress(
                    DownloadProgress(
                        id = entry.id,
                        bytesRead = partFile.length(),
                        totalBytes = entry.fileSizeBytes,
                        status = DownloadStatus.FAILED,
                        errorMessage = t.message ?: "Error de descarga"
                    )
                )
            } finally {
                activeJobs.remove(entry.id)
            }
        }
        activeJobs[entry.id] = job
    }

    fun pause(id: String) {
        activeJobs[id]?.cancel()
    }

    fun cancelAndDeletePartial(entry: CatalogEntry) {
        activeJobs[entry.id]?.cancel()
        partFileFor(entry).delete()
        _progress.update { it - entry.id }
    }

    private suspend fun registerCompletedEntry(entry: CatalogEntry, targetFile: File) {
        val probed = mediaInspector.probeLocalFile(targetFile, entry.title, entry.format)
        wallpaperDao.upsert(
            WallpaperEntity(
                id = entry.id,
                title = entry.title,
                author = entry.author,
                license = entry.license,
                category = entry.category,
                mediaType = probed.mediaType.name,
                format = probed.format.name,
                source = WallpaperSource.STORE_DOWNLOAD.name,
                thumbnailUri = entry.thumbnailUrl,
                previewUri = entry.previewUrl,
                localPath = targetFile.absolutePath,
                enhancedPath = null,
                width = if (probed.width > 0) probed.width else entry.width,
                height = if (probed.height > 0) probed.height else entry.height,
                durationMs = if (probed.durationMs > 0) probed.durationMs else entry.durationMs,
                bitrateBps = if (probed.bitrateBps > 0) probed.bitrateBps else entry.bitrateBps,
                fps = if (probed.fps > 0f) probed.fps else entry.fps,
                codec = probed.codec,
                fileSizeBytes = targetFile.length(),
                enhancedSizeBytes = 0L,
                sha256 = entry.sha256,
                createdAtEpochMs = System.currentTimeMillis(),
                enhanceStatus = EnhanceStatus.NONE.name,
                enhanceProgress = 0f,
                tagsCsv = entry.tags.joinToString(",")
            )
        )
        updateProgress(
            DownloadProgress(
                id = entry.id,
                bytesRead = targetFile.length(),
                totalBytes = targetFile.length(),
                status = DownloadStatus.COMPLETED
            )
        )
    }

    private fun partFileFor(entry: CatalogEntry): File =
        File(partDir, "${entry.id}.${entry.format.extension}.part")

    private fun updateProgress(item: DownloadProgress) {
        _progress.update { current -> current + (item.id to item) }
    }
}
