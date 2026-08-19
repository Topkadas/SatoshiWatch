package com.satoshiwatch.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.satoshiwatch.BuildConfig
import com.satoshiwatch.data.remote.NetworkClientProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request

/** Manifest aktualizace publikovaný v repozitáři (dist/version.json). */
@Serializable
data class UpdateManifestDto(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    /** Hex SHA-256 otisk APK – ověřuje se po stažení. */
    @SerialName("sha256") val sha256: String,
    @SerialName("notes") val notes: String = ""
)

/** Stav ruční aktualizace – jediný zdroj pravdy je [UpdateManager]. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifestDto) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(
        val file: File,
        val manifest: UpdateManifestDto,
        /** true = uživatel musí nejprve povolit instalaci z tohoto zdroje. */
        val needsInstallPermission: Boolean = false
    ) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Aktualizace aplikace přímo z GitHub repozitáře – bez obchodu, bez účtů.
 *
 * Zásady:
 *  - kontrola se spouští VÝHRADNĚ ručně z Nastavení (žádné automatické pingování),
 *  - stahuje se přes aplikačního OkHttp klienta → respektuje SOCKS5/Tor proxy,
 *  - stažené APK se ověřuje proti SHA-256 otisku z manifestu,
 *  - instalaci provádí systém (vyžaduje shodný podpisový klíč s nainstalovanou verzí).
 *
 * Stavový automat žije v singletonu s vlastním scope – rozběhnuté stahování
 * přežije opuštění obrazovky Nastavení i celé aktivity.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: NetworkClientProvider
) {

    companion object {
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/Topkadas/SatoshiWatch/main/dist/version.json"
        private const val UPDATES_DIR = "updates"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    fun checkForUpdate() {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val manifest = fetchManifest()
                if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateState.Available(manifest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun downloadUpdate() {
        val available = _state.value as? UpdateState.Available ?: return
        _state.value = UpdateState.Downloading(0)
        downloadJob = scope.launch {
            try {
                val file = downloadAndVerify(available.manifest) { percent ->
                    _state.value = UpdateState.Downloading(percent)
                }
                _state.value = UpdateState.ReadyToInstall(file, available.manifest)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(available.manifest)
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Stažení selhalo")
            }
        }
    }

    /** Zruší běžící stahování; stav se vrátí na nabídku dostupné verze. */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Návrat do výchozího stavu (např. pro opakovanou kontrolu). */
    fun resetState() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }

    /**
     * Spustí systémovou instalaci. Bez oprávnění „instalace z tohoto zdroje“
     * otevře příslušné systémové nastavení a označí stav, ať UI vysvětlí postup.
     */
    fun installUpdate() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = ready.copy(needsInstallPermission = true)
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        runCatching { context.startActivity(buildInstallIntent(ready.file)) }
            .onFailure {
                _state.value = UpdateState.Error(it.message ?: "Instalaci se nepodařilo spustit")
            }
    }

    // ------------------------------------------------------------- interní

    private suspend fun fetchManifest(): UpdateManifestDto {
        val request = Request.Builder().url(MANIFEST_URL).build()
        network.okHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Prázdná odpověď")
            return network.json.decodeFromString(UpdateManifestDto.serializer(), body)
        }
    }

    /**
     * Stáhne APK do cache, průběžně hlásí procenta a ověří SHA-256.
     * Při jakémkoli selhání (i zrušení) smaže rozestahovaný soubor.
     */
    private suspend fun downloadAndVerify(
        manifest: UpdateManifestDto,
        onProgress: (Int) -> Unit
    ): File {
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val outFile = File(dir, "SatoshiWatch-${manifest.versionName}.apk")

        try {
            val request = Request.Builder().url(manifest.apkUrl).build()
            network.okHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Prázdná odpověď")
                val totalBytes = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            // zrušení musí přerušit přenos hned, ne po dostažení
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress(
                                    ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                )
                            }
                        }
                    }
                }

                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
                    throw SecurityException("SHA-256 otisk staženého APK nesouhlasí s manifestem")
                }
            }
            return outFile
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        }
    }

    /** Předá stažené APK systémovému instalátoru (přes FileProvider). */
    private fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
