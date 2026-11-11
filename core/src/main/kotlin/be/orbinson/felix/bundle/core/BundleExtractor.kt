package be.orbinson.felix.bundle.core

import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

/**
 * Scans a Felix launchpad bundles directory and produces:
 *  - binary JARs in [ExtractionConfig.artifactsDirectory]
 *  - -sources JARs in [ExtractionConfig.sourcesDirectory]
 *
 * This class has **no IntelliJ Platform API dependencies** and can be used from
 * both the CLI and the IntelliJ plugin.
 *
 * Progress and logging are reported via an [ExtractionListener] so the caller
 * can forward them to a `ProgressIndicator`, stdout, or any other sink.
 */
object BundleExtractor {

    /**
     * Runs a full extraction pass.
     *
     * @param config   All settings for this extraction run.
     * @param listener Callback for progress updates and log messages.  May be omitted.
     */
    fun extractAll(config: ExtractionConfig, listener: ExtractionListener = object : ExtractionListener {}) {
        val bundlesDir = File(config.bundlesDirectory)
        if (!bundlesDir.isDirectory) {
            listener.warn("Bundles directory does not exist or is not a directory: ${bundlesDir.absolutePath}")
            return
        }

        File(config.artifactsDirectory).mkdirs()
        File(config.sourcesDirectory).mkdirs()

        val artifactsDir = File(config.artifactsDirectory)
        val sourcesDir = File(config.sourcesDirectory)

        val bundleDirs = bundlesDir.listFiles { f -> f.isDirectory } ?: return
        val bundles = orderSlowLast(scanBundles(bundleDirs, config, listener))
        listener.onStart(bundles.size)

        bundles.forEachIndexed { index, bundle ->
            if (listener.isCancelled()) return
            val label = if (bundle.slow) {
                "${bundle.dir.name} (${bundle.info.artifactId}: large bundle, may take several minutes)"
            } else {
                bundle.dir.name
            }
            listener.onProgress(index, bundles.size, label)

            try {
                extractBundle(bundle, config, artifactsDir, sourcesDir, listener)
            } catch (e: Exception) {
                listener.error("Could not extract bundle: ${bundle.dir.absolutePath}", e)
            }
        }
    }

    // ----- internal helpers -----

    internal data class ScannedBundle(val dir: File, val jar: Path, val info: BundleInfo, val slow: Boolean)

    private fun scanBundles(
        bundleDirs: Array<File>,
        config: ExtractionConfig,
        listener: ExtractionListener
    ): List<ScannedBundle> {
        val slowPattern = config.slowArtifactIds.trim().takeIf { it.isNotEmpty() }?.toRegex()
        return bundleDirs.mapNotNull { dir ->
            try {
                val jar = findBundleJar(dir.toPath()) ?: return@mapNotNull null
                val info = JarFile(jar.toFile()).use { readBundleInfo(it, config, listener) } ?: return@mapNotNull null
                if (info.excluded) return@mapNotNull null
                ScannedBundle(dir, jar, info, slow = slowPattern?.matches(info.artifactId) == true)
            } catch (e: Exception) {
                listener.error("Could not read bundle: ${dir.absolutePath}", e)
                null
            }
        }
    }

    /** Slow bundles go last so everything else is available before they finish. */
    internal fun orderSlowLast(bundles: List<ScannedBundle>): List<ScannedBundle> =
        bundles.sortedBy { it.slow }

    internal fun timeoutFor(bundle: ScannedBundle, config: ExtractionConfig): Int {
        val timeout = config.decompilationTimeoutSeconds
        return if (bundle.slow && timeout > 0) maxOf(timeout, config.slowDecompilationTimeoutSeconds) else timeout
    }

    /** Felix stores bundles as: <bundleId>/version<N>/bundle.jar */
    private fun extractBundle(
        bundle: ScannedBundle,
        config: ExtractionConfig,
        artifactsDir: File,
        sourcesDir: File,
        listener: ExtractionListener
    ) {
        val bundleJar = bundle.jar
        val info = bundle.info
        val artifactJar = File(artifactsDir, "${info.artifactId}-${info.version}.jar")
        val sourcesJar = File(sourcesDir, "${info.artifactId}-${info.version}$SOURCES_SUFFIX")

        if (!artifactJar.exists()) {
            Files.copy(bundleJar, artifactJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            listener.info("Extracted artifact: ${artifactJar.name}")
        }

        if (!sourcesJar.exists()) {
            val fetched = if (info.fromMaven) {
                SourcesUtil.downloadSources(
                    groupId = info.groupId,
                    artifactId = info.artifactId,
                    version = info.version,
                    mavenRepositoryUrl = config.mavenRepositoryUrl,
                    destination = sourcesJar,
                    listener = listener
                )
            } else {
                false
            }

            if (!fetched) {
                val shouldDecompile = when (config.decompilationBehavior) {
                    DecompilationBehavior.ALWAYS_DECOMPILE -> true
                    DecompilationBehavior.NEVER_DECOMPILE -> false
                    // ASK: in non-interactive contexts (CLI, background task) default to decompile
                    DecompilationBehavior.ASK -> true
                }
                if (shouldDecompile) {
                    if (bundle.slow) {
                        listener.info("Decompiling ${info.artifactId}: large bundle, this can take several minutes")
                    }
                    SourcesUtil.decompile(
                        bundleJar = bundleJar,
                        sourcesJar = sourcesJar,
                        artifactId = info.artifactId,
                        timeoutSeconds = timeoutFor(bundle, config),
                        realign = config.realignDecompiledSources,
                        listener = listener
                    )
                } else {
                    listener.debug("Skipping decompilation for ${info.artifactId} (NEVER_DECOMPILE)")
                }
            }
        }
    }

    private fun findBundleJar(bundleDir: Path): Path? {
        val children = bundleDir.toFile().listFiles { f -> f.isDirectory } ?: return null
        for (child in children) {
            if (child.name.startsWith("version")) {
                val bundleJar = File(child, "bundle.jar")
                if (bundleJar.exists()) return bundleJar.toPath()
            }
        }
        return null
    }

    internal data class BundleInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val fromMaven: Boolean,
        val excluded: Boolean
    )

    private fun readBundleInfo(jar: JarFile, config: ExtractionConfig, listener: ExtractionListener): BundleInfo? {
        val pomEntry = jar.entries().asSequence().firstOrNull { entry ->
            entry.name.startsWith("META-INF/maven/") && entry.name.endsWith("/pom.xml")
        }
        if (pomEntry != null) {
            return jar.getInputStream(pomEntry).use { stream ->
                readFromPom(stream, jar, config, listener)
            }
        }
        if (config.excludeNonMavenArtifacts) return null
        return readFromManifest(jar, config)
    }

    private fun readFromPom(
        stream: InputStream,
        jar: JarFile,
        config: ExtractionConfig,
        listener: ExtractionListener
    ): BundleInfo? {
        return try {
            val model = MavenXpp3Reader().read(stream)
            val groupId = (model.groupId ?: model.parent?.groupId ?: "").trim()
            val artifactId = (model.artifactId ?: "").trim()
            val version = (model.version ?: model.parent?.version ?: "").trim()
            if (artifactId.isEmpty()) return null
            val embedDependency = jar.manifest?.mainAttributes?.getValue("Embed-Dependency") ?: ""
            if (embedDependency.isNotBlank() && embedDependency.contains(artifactId)) return null
            val excluded = isExcluded(groupId, artifactId, config)
            BundleInfo(groupId, artifactId, version, fromMaven = true, excluded = excluded)
        } catch (e: Exception) {
            listener.error("Could not parse pom.xml from bundle", e)
            null
        }
    }

    private fun readFromManifest(jar: JarFile, config: ExtractionConfig): BundleInfo? {
        val attrs = jar.manifest?.mainAttributes ?: return null
        val groupId = attrs.getValue("Implementation-Vendor-Id") ?: "com.adobe.manifest"
        val artifactId = attrs.getValue("Bundle-SymbolicName")
            ?: attrs.getValue("Implementation-Title")
            ?: return null
        val version = attrs.getValue("Implementation-Version") ?: ""
        val excluded = isExcluded(groupId, artifactId.trim(), config)
        return BundleInfo(groupId, artifactId.trim(), version, fromMaven = false, excluded = excluded)
    }

    private fun isExcluded(groupId: String, artifactId: String, config: ExtractionConfig): Boolean {
        val include = config.includeGroupIds.trim()
        if (include.isNotEmpty() && !groupId.matches(include.toRegex())) return true
        val excludeGroup = config.excludeGroupIds.trim()
        if (excludeGroup.isNotEmpty() && groupId.matches(excludeGroup.toRegex())) return true
        val excludeArtifact = config.excludeArtifactIds.trim()
        if (excludeArtifact.isNotEmpty() && artifactId.matches(excludeArtifact.toRegex())) return true
        return false
    }
}
