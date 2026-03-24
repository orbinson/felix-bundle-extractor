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

                    // Refresh the VFS so new files are visible immediately.
                    val artifactsVfs = VfsUtil.findFileByIoFile(
                        FelixBundleStorageService.getArtifactsDir(project), false
                    )
                    val sourcesVfs = VfsUtil.findFileByIoFile(
                        FelixBundleStorageService.getSourcesDir(project), false
                    )
                    val roots = listOfNotNull(artifactsVfs, sourcesVfs)
                    if (roots.isNotEmpty()) {
                        VfsUtil.markDirtyAndRefresh(true, true, true, *roots.toTypedArray())
                    }

                    // Notify IntelliJ to re-scan the library roots (must run on EDT)
                    ApplicationManager.getApplication().invokeLater {
                        AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                            project,
                            "Felix Bundles",
                            emptyList(),
                            emptyList(),
                            FelixBundleLibraryProvider::class.java.name
                        )
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                } finally {
                    running.set(false)
                }
            }

            override fun onCancel() {
                running.set(false)
            }
        })
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
