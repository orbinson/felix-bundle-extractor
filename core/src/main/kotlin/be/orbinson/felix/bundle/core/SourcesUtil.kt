package be.orbinson.felix.bundle.core

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val SOURCES_SUFFIX = "-sources.jar"

/** First line of every decompiled file; identifies sources whose lines carry `// <original line>` markers. */
const val DECOMPILED_BANNER = "// Decompiled by Felix Bundle Extractor (Fernflower), line markers map to bytecode"

/**
 * Same options as IntelliJ's own `IdeaDecompiler`. `bsm` + `__dump_original_lines__` append the original
 * line numbers as a trailing `// 12 13` comment, which the plugin turns into a debugger line mapping.
 */
internal val DECOMPILER_OPTIONS: Map<String, Any> = mapOf(
    "hdc" to "0",
    "dgs" to "1",
    "rsy" to "1",
    "rbr" to "1",
    "nls" to "1",
    "mpm" to 60,
    "ind" to "    ",
    "iib" to "1",
    "vac" to "1",
    "bsm" to "1",
    "__dump_original_lines__" to "1",
    "ban" to "$DECOMPILED_BANNER\n",
)

/**
 * Utilities for acquiring sources for a bundle JAR:
 * - [downloadSources]: tries to fetch a `-sources.jar` from a Maven repository.
 * - [decompile]: uses Fernflower/Vineflower to decompile a bundle JAR into a sources JAR.
 *
 * No IntelliJ Platform API dependencies — usable from both the CLI and the plugin.
 * The plugin's runtime classpath replaces the bundled `vineflower` dependency with
 * IntelliJ's own `java-decompiler.jar`, which uses the same package names.
 */
object SourcesUtil {

    fun downloadSources(
        groupId: String,
        artifactId: String,
        version: String,
        mavenRepositoryUrl: String,
        destination: File,
        listener: ExtractionListener = object : ExtractionListener {}
    ): Boolean {
        val groupPath = groupId.replace('.', '/')
        val url =
            "${mavenRepositoryUrl.trimEnd('/')}/$groupPath/$artifactId/$version/$artifactId-$version$SOURCES_SUFFIX"

        listener.debug("Checking Maven repository for $artifactId sources...")
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val status = connection.responseCode
            connection.disconnect()

            if (status == HttpURLConnection.HTTP_OK) {
                listener.debug("Downloading $artifactId$SOURCES_SUFFIX...")
                downloadFile(url, destination)
                listener.info("Downloaded sources: ${destination.name}")
                true
            } else {
                listener.debug("Sources not found on repository (HTTP $status) for $artifactId")
                false
            }
        } catch (e: IOException) {
            listener.debug("Could not reach Maven repository for $artifactId: ${e.message}")
            false
        }
    }

    fun decompile(
        bundleJar: Path,
        sourcesJar: File,
        artifactId: String,
        timeoutSeconds: Int = 60,
        realign: Boolean = true,
        listener: ExtractionListener = object : ExtractionListener {}
    ): Boolean {
        listener.debug("Decompiling $artifactId...")
        val tempDir = Files.createTempDirectory("felix-decompile-$artifactId")
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "felix-decompile-$artifactId").also { it.isDaemon = true }
        }
        val startMs = System.currentTimeMillis()

        return try {
            val future = executor.submit<Boolean> {
                val fernflowerLogger = object : IFernflowerLogger() {
                    override fun writeMessage(message: String, severity: Severity) = Unit
                    override fun writeMessage(message: String, severity: Severity, t: Throwable) = Unit
                }
                fernflowerLogger.setSeverity(IFernflowerLogger.Severity.ERROR)

                val decompiler = FelixDecompiler(tempDir.toFile(), DECOMPILER_OPTIONS, fernflowerLogger)
                decompiler.addSource(bundleJar.toFile())
                decompiler.decompileContext()

                val decompiled = tempDir.resolve(bundleJar.fileName).toFile()
                if (decompiled.exists()) {
                    val output = if (realign) {
                        tempDir.resolve("realigned.jar").toFile().also { realignSources(decompiled, it) }
                    } else {
                        decompiled
                    }
                    Files.move(output.toPath(), sourcesJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    true
                } else {
                    false
                }
            }

            val success = if (timeoutSeconds > 0) {
                future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            } else {
                future.get()
            }

            val elapsedMs = System.currentTimeMillis() - startMs
            if (success) {
                listener.info("Decompiled $artifactId in ${elapsedMs}ms -> ${sourcesJar.name}")
            } else {
                listener.warn("Fernflower produced no output for $artifactId (${elapsedMs}ms)")
            }
            success
        } catch (e: TimeoutException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            listener.warn("Decompilation of $artifactId timed out after ${elapsedMs}ms (limit: ${timeoutSeconds}s) — skipping")
            false
        } catch (e: ExecutionException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            listener.error("Could not decompile $artifactId (${elapsedMs}ms)", e.cause ?: e)
            false
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startMs
            listener.error("Could not decompile $artifactId (${elapsedMs}ms)", e)
            false
        } finally {
            executor.shutdownNow()
            tempDir.toFile().deleteRecursively()
        }
    }

    /** Copies [input] to [output], applying [DecompiledLineMapping.realign] to every `.java` entry. */
    internal fun realignSources(input: File, output: File) {
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.endsWith(".java")) {
                            val lines = bytes.toString(Charsets.UTF_8).split("\n")
                            out.write(DecompiledLineMapping.realign(lines).joinToString("\n").toByteArray(Charsets.UTF_8))
                        } else {
                            out.write(bytes)
                        }
                    }
                    out.closeEntry()
                }
            }
        }
    }

    private fun downloadFile(urlString: String, destination: File) {
        val connection = URI(urlString).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
            connection.inputStream.use { input ->
                Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Exposes the `protected` [ConsoleDecompiler] constructor so we can instantiate it
 * without calling [ConsoleDecompiler.main], which calls [System.exit].
 */
private class FelixDecompiler(
    outputDir: File,
    options: Map<String, Any>,
    logger: IFernflowerLogger
) : ConsoleDecompiler(outputDir, options, logger)
