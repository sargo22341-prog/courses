package org.opensources.courses.feature.voice.data

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opensources.courses.feature.language.domain.AppLanguage
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class VoiceModelAsset(val language: String, val name: String, val sha256: String, val url: String)

/** Extract only the chosen shipped model into private storage, outside Android backups. */
@Singleton
class VoiceModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val extraction = Mutex()

    suspend fun directory(language: AppLanguage): File = extraction.withLock {
        val manifest = context.assets.open("voice-models/manifest.json").bufferedReader().use {
            Json.decodeFromString<List<VoiceModelAsset>>(it.readText())
        }
        val asset = manifest.single { it.language == language.tag }
        require(asset.sha256.matches(Regex("[a-f0-9]{64}")))
        require(asset.name.matches(Regex("[a-z0-9.-]+")))
        val directory = File(context.noBackupFilesDir, "voice-models/${asset.sha256}")
        val ready = File(directory, "ready")
        if (ready.isFile && ready.readText() == asset.sha256) return@withLock directory
        check(directory.mkdirs() || directory.isDirectory)
        extract(asset, directory)
        ready.writeText(asset.sha256)
        directory
    }

    private suspend fun extract(asset: VoiceModelAsset, directory: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("voice-models/${asset.language}.zip").use { input ->
            DigestInputStream(input, digest).use { stream ->
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                val deadline = SystemClock.elapsedRealtime() + 30_000
                while (true) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline && bytes <= 80L * 1024 * 1024)
                    val count = stream.read(buffer)
                    if (count < 0) break
                    bytes += count
                }
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256)
        unpack(asset, directory)
    }

    private suspend fun unpack(asset: VoiceModelAsset, directory: File) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        context.assets.open("voice-models/${asset.language}.zip").use { input ->
            ZipInputStream(input).use { zip ->
                for (index in 0 until 128) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline)
                    val entry = zip.nextEntry ?: return
                    require(entry.name.startsWith("${asset.name}/"))
                    val relative = entry.name.removePrefix("${asset.name}/")
                    if (relative.isEmpty() || entry.isDirectory) continue
                    require(relative.matches(Regex("[a-zA-Z0-9._/-]+")))
                    require(relative.split('/').none { it == ".." || it == "." })
                    val output = File(directory, relative)
                    check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                    output.outputStream().use { file ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            check(SystemClock.elapsedRealtime() < deadline && total <= 150L * 1024 * 1024)
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            file.write(buffer, 0, count)
                        }
                    }
                }
                throw IOException("Too many files in speech model")
            }
        }
    }
}
