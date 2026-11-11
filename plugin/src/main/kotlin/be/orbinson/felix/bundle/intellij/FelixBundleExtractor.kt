package be.orbinson.felix.bundle.intellij

import be.orbinson.felix.bundle.core.BundleExtractor
import be.orbinson.felix.bundle.core.ExtractionConfig
import be.orbinson.felix.bundle.core.ExtractionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.EditorNotifications
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = Logger.getInstance(FelixBundleExtractor::class.java)

/**
 * IntelliJ-facing entry point for bundle extraction.
 *
 * Bridges IntelliJ Platform APIs ([ProgressIndicator], [Task.Backgroundable], VFS refresh)
 * with the platform-agnostic [BundleExtractor] in the `core` module.
 */
object FelixBundleExtractor {

    // Prevents concurrent extractions (e.g. from the user clicking the button multiple times).
    private val running = AtomicBoolean(false)

    /**
     * Launches a background task that runs [BundleExtractor.extractAll] and then
     * triggers an IntelliJ library roots refresh. Duplicate requests are ignored.
     */
    fun extractAllInBackground(project: Project) {
        if (!running.compareAndSet(false, true)) {
            LOG.info("Felix bundle extraction already in progress, ignoring duplicate request")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Extracting Felix Bundles",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val config = buildConfig(project)
                    val listener = indicatorListener(indicator)
                    BundleExtractor.extractAll(config, listener)
                    refreshStorage(project)
                    notifyLibraryChanged(project)
                } finally {
                    running.set(false)
                }
            }

            override fun onCancel() {
                running.set(false)
            }
        })
    }

    /**
     * Synchronously refreshes the storage dirs and the contents of every JAR in them. Call from a
     * background thread without a read lock; the library provider then finds the JARs without
     * refreshing under its read lock.
     */
    fun refreshStorage(project: Project) {
        val dirs = listOf(
            FelixBundleStorageService.getArtifactsDir(project),
            FelixBundleStorageService.getSourcesDir(project),
        ).mapNotNull { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it) }
        if (dirs.isEmpty()) return
        VfsUtil.markDirtyAndRefresh(false, true, true, *dirs.toTypedArray())

        // JAR contents are cached separately from the JAR file itself. A JAR that was replaced (or
        // missing when last read) otherwise keeps stale or empty entries, so its classes never get indexed.
        val jarRoots = dirs.flatMap { it.children.asList() }
            .filter { it.extension == "jar" }
            .mapNotNull { JarFileSystem.getInstance().getJarRootForLocalFile(it) }
        if (jarRoots.isNotEmpty()) {
            VfsUtil.markDirtyAndRefresh(false, true, true, *jarRoots.toTypedArray())
        }
        LOG.debug { "Refreshed ${dirs.size} storage dirs and ${jarRoots.size} JAR roots" }
    }

    /** Makes IntelliJ re-query [FelixBundleLibraryProvider]. */
    fun notifyLibraryChanged(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ApplicationManager.getApplication().runWriteAction {
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project,
                    "Felix Bundles",
                    emptyList(),
                    emptyList(),
                    FelixBundleLibraryProvider::class.java.name
                )
            }
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    // ----- helpers -----

    private fun buildConfig(project: Project): ExtractionConfig {
        val settings = FelixBundleSettings.getInstance(project)
        return ExtractionConfig(
            bundlesDirectory = settings.bundlesDirectory.trim(),
            artifactsDirectory = FelixBundleStorageService.getArtifactsDir(project).absolutePath,
            sourcesDirectory = FelixBundleStorageService.getSourcesDir(project).absolutePath,
            includeGroupIds = settings.includeGroupIds,
            excludeGroupIds = settings.excludeGroupIds,
            excludeArtifactIds = settings.excludeArtifactIds,
            excludeNonMavenArtifacts = settings.excludeNonMavenArtifacts,
            mavenRepositoryUrl = settings.mavenRepositoryUrl,
            decompilationBehavior = settings.decompilationBehavior,
            decompilationTimeoutSeconds = settings.decompilationTimeoutSeconds,
            realignDecompiledSources = settings.realignDecompiledSources,
        )
    }

    private fun indicatorListener(indicator: ProgressIndicator) = object : ExtractionListener {
        override fun onStart(total: Int) {
            indicator.isIndeterminate = false
            indicator.fraction = 0.0
        }

        override fun onProgress(index: Int, total: Int, bundleName: String) {
            indicator.fraction = index.toDouble() / total
            indicator.text = "Processing bundle: $bundleName"
        }

        override fun isCancelled(): Boolean = indicator.isCanceled

        override fun info(message: String) = LOG.info(message)
        override fun warn(message: String) = LOG.warn(message)
        override fun debug(message: String) = LOG.debug(message)
        override fun error(message: String, cause: Throwable?) {
            if (cause != null) LOG.error(message, cause) else LOG.error(message)
        }
    }
}
