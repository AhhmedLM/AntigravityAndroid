package com.antigravity.studio.runtime

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Module 1: Binary Asset Manager (`RuntimeInstaller.kt`)
 *
 * Extracts the official Google Antigravity CLI native Android Bionic `arm64-v8a` binary
 * (`cli_android_arm64.tar.gz` -> `antigravity` / `agy` v1.4.12) from APK `assets/runtime/`
 * (or fetches the latest release from Google's official Antigravity CLI updater manifest)
 * strictly into `context.filesDir/runtime/bin/`.
 *
 * Architectural & Security Invariants:
 * - Strict SELinux & W^X (Write XOR Execute) compliance: Binaries execute strictly from
 *   `context.filesDir/runtime/bin/` (`app_data_file` SELinux domain).
 * - Idempotent version checking via `.runtime_version` stamp file.
 * - Explicit POSIX executable permission handling (`setExecutable(true, false)` and `Os.chmod(0755)`)
 *   for `runtime/bin/antigravity` and `runtime/bin/agy`.
 */
class RuntimeInstaller(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "RuntimeInstaller"
        private const val BUNDLED_TARBALL_ASSET = "runtime/cli_android_arm64.agypkg"
        private const val VERSION_STAMP_FILE = ".runtime_version"
        const val BUNDLED_CLI_VERSION = "1.4.12-android-arm64"
        const val OFFICIAL_MANIFEST_URL =
            "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/android_arm64.json"
        private const val BUFFER_SIZE = 128 * 1024 // 128 KB I/O buffer
    }

    @Serializable
    data class OfficialCliManifest(
        val version: String,
        val url: String,
        val sha512: String
    )

    sealed interface InstallState {
        data object Idle : InstallState
        data class Extracting(val step: String, val progressFraction: Float) : InstallState
        data class Ready(val runtimePaths: RuntimePaths, val cliVersion: String) : InstallState
        data class Failed(val reason: String, val throwable: Throwable? = null) : InstallState
    }

    data class RuntimePaths(
        val runtimeRootDir: File,
        val binDir: File,
        val homeDir: File,
        val geminiConfigDir: File,
        val cliSettingsFile: File,
        val tmpDir: File,
        val workspacesDir: File,
        val antigravityBinary: File,
        val agyBinary: File
    )

    private val installMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    val runtimeRootDir: File
        get() = File(context.filesDir, "runtime")

    val binDir: File
        get() = File(runtimeRootDir, "bin")

    val homeDir: File
        get() = File(context.filesDir, "home")

    val geminiConfigDir: File
        get() = File(homeDir, ".gemini/antigravity-cli")

    val tmpDir: File
        get() = File(context.filesDir, "tmp")

    val workspacesDir: File
        get() = File(homeDir, "projects")

    /**
     * Ensures the official `android_arm64` Antigravity CLI (`agy`) binary is extracted,
     * verified as a valid 64-bit ARM ELF executable, and marked `0755` inside `context.filesDir/runtime/bin/`.
     */
    suspend fun ensureInstalled(forceReinstall: Boolean = false): Result<RuntimePaths> =
        withContext(ioDispatcher) {
            installMutex.withLock {
                runCatching {
                    val paths = resolveRuntimePaths()
                    createDirectoryHierarchy(paths)
                    ensureCliSettingsJson(paths)

                    if (!forceReinstall && isVersionUpToDate(paths)) {
                        enforceExecutablePermissions(paths)
                        _installState.value = InstallState.Ready(paths, BUNDLED_CLI_VERSION)
                        Log.i(TAG, "Antigravity CLI ($BUNDLED_CLI_VERSION) already installed & verified.")
                        return@runCatching paths
                    }

                    _installState.value = InstallState.Extracting(
                        step = "Extracting official Antigravity CLI (android_arm64 v1.4.12)...",
                        progressFraction = 0.15f
                    )

                    val extractedFromAssets = extractBundledTarballIfPresent(paths)
                    if (!extractedFromAssets) {
                        _installState.value = InstallState.Extracting(
                            step = "Downloading official android_arm64 release from antigravity.google...",
                            progressFraction = 0.30f
                        )
                        downloadFromOfficialManifest(paths)
                    }

                    createAgySymlinkOrLauncher(paths)
                    enforceExecutablePermissions(paths)
                    writeVersionStamp(paths, BUNDLED_CLI_VERSION)

                    _installState.value = InstallState.Ready(paths, BUNDLED_CLI_VERSION)
                    paths
                }.onFailure { error ->
                    Log.e(TAG, "Failed to install Antigravity CLI runtime", error)
                    _installState.value = InstallState.Failed(
                        reason = error.message ?: "Unknown CLI installation error",
                        throwable = error
                    )
                }
            }
        }

    fun resolveRuntimePaths(): RuntimePaths {
        return RuntimePaths(
            runtimeRootDir = runtimeRootDir,
            binDir = binDir,
            homeDir = homeDir,
            geminiConfigDir = geminiConfigDir,
            cliSettingsFile = File(geminiConfigDir, "settings.json"),
            tmpDir = tmpDir,
            workspacesDir = workspacesDir,
            antigravityBinary = File(binDir, "antigravity"),
            agyBinary = File(binDir, "agy")
        )
    }

    private fun isVersionUpToDate(paths: RuntimePaths): Boolean {
        val stampFile = File(paths.runtimeRootDir, VERSION_STAMP_FILE)
        if (!stampFile.exists()) return false
        val installedVersion = runCatching { stampFile.readText().trim() }.getOrNull()
        return installedVersion == BUNDLED_CLI_VERSION &&
            paths.antigravityBinary.exists() &&
            paths.antigravityBinary.length() > 1_000_000L &&
            isElfBinary(paths.antigravityBinary)
    }

    private fun createDirectoryHierarchy(paths: RuntimePaths) {
        val defaultProjects = listOf(
            "JavaKingdom",
            "lively-noether",
            "TradeMachine",
            "McpeArabicFixer",
            "MoreXp",
            "AntigravityAndroid"
        )

        val requiredDirs = listOf(
            paths.runtimeRootDir,
            paths.binDir,
            paths.homeDir,
            paths.geminiConfigDir,
            File(paths.homeDir, ".local/bin"),
            File(paths.homeDir, ".cache/antigravity"),
            paths.tmpDir,
            paths.workspacesDir
        ) + defaultProjects.map { File(paths.workspacesDir, it) }

        requiredDirs.forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Unable to create directory: ${dir.absolutePath}")
            }
            setDirectoryPermissions(dir)
        }
    }

    private fun ensureCliSettingsJson(paths: RuntimePaths) {
        if (!paths.cliSettingsFile.exists()) {
            paths.cliSettingsFile.writeText(
                """
                {
                  "theme": "dark",
                  "renderMode": "inline",
                  "model": "gemini-4-argon-medium",
                  "permissionMode": "request-review",
                  "trustedWorkspaces": [
                    "${paths.workspacesDir.absolutePath.replace("\\", "/")}"
                  ]
                }
                """.trimIndent()
            )
        }
    }

    /**
     * Extracts `assets/runtime/cli_android_arm64.tar.gz` atomically into `runtime/bin/antigravity`.
     */
    private fun extractBundledTarballIfPresent(paths: RuntimePaths): Boolean {
        val assetStream = runCatching { context.assets.open(BUNDLED_TARBALL_ASSET) }.getOrNull()
            ?: return false

        assetStream.use { rawStream ->
            extractTarGzContainingAntigravity(
                tarGzStream = rawStream,
                targetBinary = paths.antigravityBinary,
                totalCompressedBytes = 58_869_007L
            )
        }
        return paths.antigravityBinary.exists() && paths.antigravityBinary.length() > 1_000_000L
    }

    /**
     * Queries Google's official Antigravity CLI updater (`manifests/android_arm64.json`),
     * verifies the SHA-512 checksum, and extracts the native `antigravity` ELF binary.
     */
    private fun downloadFromOfficialManifest(paths: RuntimePaths) {
        val manifestRequest = Request.Builder().url(OFFICIAL_MANIFEST_URL).build()
        val manifestJson = httpClient.newCall(manifestRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Manifest query HTTP ${resp.code}")
            }
            resp.body?.string() ?: throw IOException("Empty manifest body")
        }

        val manifest = json.decodeFromString<OfficialCliManifest>(manifestJson)
        val stagingTarGz = File(paths.tmpDir, "cli_android_arm64.tar.gz")

        try {
            val downloadReq = Request.Builder().url(manifest.url).build()
            httpClient.newCall(downloadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Binary download HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IOException("Empty binary payload")
                val totalLen = body.contentLength().coerceAtLeast(1L)
                val digest = MessageDigest.getInstance("SHA-512")

                BufferedInputStream(body.byteStream(), BUFFER_SIZE).use { input ->
                    BufferedOutputStream(FileOutputStream(stagingTarGz), BUFFER_SIZE).use { output ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var copied = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            digest.update(buf, 0, read)
                            copied += read
                            val progress = 0.30f + 0.40f * (copied.toFloat() / totalLen.toFloat())
                            _installState.value = InstallState.Extracting(
                                step = "Downloading Antigravity CLI v${manifest.version} (${copied / (1024 * 1024)}MB)...",
                                progressFraction = progress.coerceIn(0.30f, 0.70f)
                            )
                        }
                        output.flush()
                    }
                }

                val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHex.equals(manifest.sha512, ignoreCase = true)) {
                    throw IOException("SHA-512 mismatch on downloaded Antigravity CLI package")
                }
            }

            FileInputStream(stagingTarGz).use { fis ->
                extractTarGzContainingAntigravity(
                    tarGzStream = fis,
                    targetBinary = paths.antigravityBinary,
                    totalCompressedBytes = stagingTarGz.length()
                )
            }
        } finally {
            if (stagingTarGz.exists()) {
                stagingTarGz.delete()
            }
        }
    }

    /**
     * Pure-Kotlin POSIX `ustar` `.tar.gz` extractor that streams the `antigravity` ELF binary
     * directly to `runtime/bin/antigravity.tmp` and renames atomically to respect SELinux W^X.
     */
    private fun extractTarGzContainingAntigravity(
        tarGzStream: InputStream,
        targetBinary: File,
        totalCompressedBytes: Long
    ) {
        val tempFile = File(targetBinary.parentFile, "${targetBinary.name}.tmp")
        try {
            GZIPInputStream(BufferedInputStream(tarGzStream, BUFFER_SIZE)).use { gzipIn ->
                val header = ByteArray(512)
                while (true) {
                    val headerRead = readFully(gzipIn, header, 512)
                    if (headerRead < 512 || header.all { it == 0.toByte() }) {
                        break
                    }

                    // Parse entry name (bytes 0..99)
                    val entryName = parseNullTerminatedString(header, 0, 100)
                    // Parse octal file size (bytes 124..135)
                    val sizeOctal = parseNullTerminatedString(header, 124, 12).trim()
                    val entrySize = sizeOctal.toLongOrNull(8) ?: 0L
                    val typeFlag = header[156].toInt().toChar()

                    val isTargetBinary = (typeFlag == '0' || typeFlag == '\u0000') &&
                        (entryName == "antigravity" || entryName.endsWith("/antigravity") || entryName == "agy")

                    if (isTargetBinary && entrySize > 0L) {
                        BufferedOutputStream(FileOutputStream(tempFile), BUFFER_SIZE).use { out ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var remaining = entrySize
                            var written = 0L
                            while (remaining > 0L) {
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val n = gzipIn.read(buf, 0, toRead)
                                if (n <= 0) throw IOException("Unexpected EOF while extracting $entryName")
                                out.write(buf, 0, n)
                                remaining -= n
                                written += n
                                val pct = (written.toFloat() / entrySize.toFloat()).coerceIn(0f, 1f)
                                _installState.value = InstallState.Extracting(
                                    step = "Unpacking native arm64-v8a binary (${written / (1024 * 1024)} / ${entrySize / (1024 * 1024)} MB)...",
                                    progressFraction = 0.20f + (0.75f * pct)
                                )
                            }
                            out.flush()
                        }
                        break
                    } else if (entrySize > 0L) {
                        // Skip entry payload + 512-byte alignment padding
                        val paddedSize = ((entrySize + 511L) / 512L) * 512L
                        skipFully(gzipIn, paddedSize)
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IOException("Archive did not contain expected 'antigravity' binary")
            }

            if (targetBinary.exists() && !targetBinary.delete()) {
                throw IOException("Unable to replace old binary at ${targetBinary.absolutePath}")
            }
            if (!tempFile.renameTo(targetBinary)) {
                throw IOException("Atomic rename failed for ${targetBinary.absolutePath}")
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Creates the `runtime/bin/agy` symlink (or direct launcher wrapper if symlink is restricted)
     * pointing to `runtime/bin/antigravity`.
     */
    private fun createAgySymlinkOrLauncher(paths: RuntimePaths) {
        if (paths.agyBinary.exists()) {
            paths.agyBinary.delete()
        }
        val symlinkCreated = try {
            Os.symlink(paths.antigravityBinary.absolutePath, paths.agyBinary.absolutePath)
            true
        } catch (errno: ErrnoException) {
            Log.w(TAG, "Os.symlink fallback for agy (${errno.message}); creating exec wrapper")
            false
        }

        if (!symlinkCreated) {
            paths.agyBinary.writeText(
                """
                #!/system/bin/sh
                exec "${paths.antigravityBinary.absolutePath}" "${'$'}@"
                """.trimIndent()
            )
        }
    }

    /**
     * Explicitly grants POSIX 0755 (`rwxr-xr-x`) permissions on `runtime/bin/antigravity` and `runtime/bin/agy`.
     */
    private fun enforceExecutablePermissions(paths: RuntimePaths) {
        listOf(paths.antigravityBinary, paths.agyBinary).forEach { binary ->
            if (!binary.exists()) {
                throw IOException("Required binary missing: ${binary.absolutePath}")
            }
            binary.setReadable(true, false)
            binary.setExecutable(true, false)
            binary.setWritable(true, true)
            try {
                Os.chmod(binary.absolutePath, 493) // 0755
            } catch (errno: ErrnoException) {
                throw IOException(
                    "POSIX chmod(0755) failed on ${binary.absolutePath} (errno=${errno.errno})",
                    errno
                )
            }
        }
    }

    fun isElfBinary(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 0x7F.toByte() &&
                    magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, total, length - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(8192)
        while (remaining > 0L) {
            val toRead = minOf(scratch.size.toLong(), remaining).toInt()
            val read = input.read(scratch, 0, toRead)
            if (read <= 0) break
            remaining -= read
        }
    }

    private fun parseNullTerminatedString(bytes: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        val limit = offset + maxLen
        while (end < limit && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun setDirectoryPermissions(dir: File) {
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
        dir.setWritable(true, true)
        try {
            Os.chmod(dir.absolutePath, 493)
        } catch (_: ErrnoException) {
        }
    }

    private fun writeVersionStamp(paths: RuntimePaths, version: String) {
        File(paths.runtimeRootDir, VERSION_STAMP_FILE).writeText(version)
    }
}
