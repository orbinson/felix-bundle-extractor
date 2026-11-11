package be.orbinson.felix.bundle.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Re-reads the extracted JARs on project open: they may have been replaced or deleted while the IDE
 * was closed (or written by the CLI), and IntelliJ would otherwise keep their cached, stale contents.
 */
class FelixBundleStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (FelixBundleSettings.getInstance(project).bundlesDirectory.isBlank()) return
        if (!FelixBundleStorageService.getStorageRoot(project).isDirectory) return
        withContext(Dispatchers.IO) { FelixBundleExtractor.refreshStorage(project) }
        // Empty old roots: (re)index everything, in case JARs appeared or changed while the IDE was closed.
        FelixBundleExtractor.notifyLibraryChanged(project, emptyList())
    }
}
