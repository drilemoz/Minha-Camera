package com.minhacamera

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest

/** GitHub HTTPS lives on an internet route, without touching the camera's Wi-Fi process binding. */
internal class GitHubUpdateClient(private val context: Context) {
    val directory = File(context.cacheDir, "github-updates")

    fun check(installed: String): GitHubUpdateRelease? {
        val connection = open(GitHubUpdateCore.API_URL, asset = false)
        try {
            val status = connection.responseCode
            if (status != 200) {
                if (status == 429 || (status == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0"))
                    throw UpdateProblem("O GitHub limitou as consultas. Tente novamente mais tarde.")
                throw UpdateProblem("Não foi possível consultar as Releases (HTTP $status).")
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chunk = CharArray(8192)
                val result = StringBuilder()
                while (true) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    if (result.length + count > 4_000_000) throw UpdateProblem("Resposta do GitHub muito grande.")
                    result.append(chunk, 0, count)
                }
                result.toString()
            }
            return GitHubUpdateCore.parseReleases(payload, installed)
        } finally { connection.disconnect() }
    }

    fun partial(release: GitHubUpdateRelease) =
        File(directory, "pending-${release.sha256}-${release.apkName}")
    private val readyDirectory = File(directory, "ready")
    private fun completed(release: GitHubUpdateRelease) = File(readyDirectory, release.apkName)

    fun download(
        release: GitHubUpdateRelease,
        resume: Boolean,
        checkpoint: () -> Unit,
        progress: (Long, Long) -> Unit,
        verifying: () -> Unit
    ): File {
        val hash = release.sha256 ?: throw UpdateProblem("O GitHub não informou o SHA-256 deste APK.")
        verifyInstalledSigner()

        if (!readyDirectory.isDirectory && !readyDirectory.mkdirs()) throw UpdateProblem("Sem acesso ao espaço do APK.")
        val partial = partial(release)
        val final = completed(release)
        // Keep only the selected release in private cache. Each partial is bound to its expected hash.
        directory.listFiles()?.filter { it.isFile && it != partial }?.forEach { it.delete() }
        readyDirectory.listFiles()?.filter { it.isFile && it != final }?.forEach { it.delete() }
        checkpoint()
        if (final.isFile && resume) {
            if (final.length() == release.size && digest(final, checkpoint).equals(hash, ignoreCase = true)) {
                verifying()
                verifyApk(final, release)
                checkpoint()
                return final
            }
            final.delete()
        }
        if (!resume) { partial.delete(); final.delete() }
        var offset = partial.length().takeIf { it in 1 until release.size } ?: 0L
        var connection = open(release.apkUrl, asset = true, offset = offset)
        if (offset > 0 && (connection.responseCode != 206 ||
                !GitHubUpdateCore.matchingContentRange(connection.getHeaderField("Content-Range"), offset, release.size))) {
            connection.disconnect()
            partial.delete()
            offset = 0
            checkpoint()
            connection = open(release.apkUrl, asset = true)
        }
        try {
            val status = connection.responseCode
            if (status != (if (offset > 0) 206 else 200))
                throw UpdateProblem("Download do APK indisponível (HTTP $status).")
            val length = connection.contentLengthLong
            if (length >= 0 && length != release.size - offset) throw UpdateProblem("Tamanho inesperado do APK.")
            var written = offset
            var lastAt = SystemClock.elapsedRealtime()
            var lastBytes = offset
            var rate = 0L
            checkpoint()
            connection.inputStream.use { input ->
                FileOutputStream(partial, offset > 0).buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        checkpoint()
                        val n = input.read(buffer)
                        if (n < 0) break
                        checkpoint()
                        written += n.toLong()
                        if (written > release.size) throw UpdateProblem("Tamanho inesperado do APK.")
                        output.write(buffer, 0, n)
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - lastAt
                        if (elapsed >= 500 || written == release.size) {
                            if (elapsed > 0) rate = (written - lastBytes) * 1000 / elapsed
                            progress(written, rate)
                            lastAt = now
                            lastBytes = written
                        }
                    }
                }
            }
            checkpoint()
            if (written != release.size) throw IOException("download incompleto")
        } finally { connection.disconnect() }
        verifying()
        if (!digest(partial, checkpoint).equals(hash, ignoreCase = true))
            throw UpdateProblem("A integridade do APK não corresponde ao SHA-256 do GitHub.")
        verifyApk(partial, release)
        checkpoint()
        if (!partial.renameTo(final)) throw UpdateProblem("Não foi possível preparar o APK.")
        return final
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, release: GitHubUpdateRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateProblem("Arquivo recebido não é um APK válido.")
        if (candidate.packageName != context.packageName || candidate.versionName != release.version ||
            code(candidate) <= code(pm.getPackageInfo(context.packageName, 0)))
            throw UpdateProblem("O pacote ou a versão não corresponde à atualização.")
        if (signers(candidate) != setOf(BuildConfig.UPDATE_SIGNER_SHA256))
            throw UpdateProblem("Assinatura do APK diferente da Release oficial.")
    }

    @Suppress("DEPRECATION")
    private fun verifyInstalledSigner() {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (signers(installed) != setOf(BuildConfig.UPDATE_SIGNER_SHA256))
            throw UpdateProblem("A instalação atual usa outra assinatura e não pode receber este APK.")
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo) =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map {
            hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
    }

    private fun digest(file: File, checkpoint: () -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                checkpoint()
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun internetNetwork(): Network {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: throw UpdateProblem("Rede de internet indisponível.")
        fun online(network: Network): Boolean {
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return cm.activeNetwork?.takeIf(::online) ?: cm.allNetworks.firstOrNull(::online)
            ?: throw UpdateProblem("Sem internet para acessar o GitHub. A rede da câmera pode continuar funcionando.")
    }

    private fun open(url: String, asset: Boolean, offset: Long = 0): HttpURLConnection {
        val network = internetNetwork()
        var destination = URL(url)
        repeat(if (asset) 6 else 1) {
            val host = destination.host.lowercase()
            val allowed = if (asset) host == "github.com" || host.endsWith(".githubusercontent.com")
                else host == "api.github.com"
            if (!allowed || destination.protocol != "https" || destination.userInfo != null ||
                destination.port !in listOf(-1, 443)) throw UpdateProblem("Endereço do GitHub não permitido.")
            val connection = (network.openConnection(destination) as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "MinhaCamera-Android")
                setRequestProperty("Accept-Encoding", "identity")
                if (!asset) setRequestProperty("Accept", "application/vnd.github+json")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                val status = connection.responseCode
                if (!asset || status !in 300..399) return connection
                val next = connection.getHeaderField("Location")
                    ?: throw UpdateProblem("Redirecionamento sem destino.")
                destination = URL(destination, next)
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw UpdateProblem("Redirecionamentos demais no GitHub.")
    }
}

internal class UpdateProblem(message: String) : IOException(message)
internal fun updateError(error: Throwable): String = when (error) {
    is UpdateProblem -> error.message.orEmpty()
    is SocketTimeoutException -> "O GitHub demorou para responder. Tente novamente."
    is IOException -> "Ocorreu uma falha de rede ou de armazenamento. Tente novamente."
    else -> "Não foi possível ler a atualização do GitHub."
}
