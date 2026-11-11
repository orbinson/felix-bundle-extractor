package be.orbinson.felix.bundle.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Provides the Felix Bundles as an IntelliJ SyntheticLibrary (External Library).
 *
 * IntelliJ calls this at project load and whenever the library is invalidated.
 * Sources are attached to the bundle JARs; decompiled sources stay debuggable through
 * [FelixBundleLineMappingListener].
 */
class FelixBundleLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val settings = FelixBundleSettings.getInstance(project)
        val bundlesDir = settings.bundlesDirectory.trim()
        if (bundlesDir.isEmpty()) return emptyList()

        val binaryRoots = collectJarRoots(FelixBundleStorageService.getArtifactsDir(project))
        val sourceRoots = collectJarRoots(FelixBundleStorageService.getSourcesDir(project))

        if (binaryRoots.isEmpty() && sourceRoots.isEmpty()) return emptyList()
        return listOf(FelixBundleLibrary(binaryRoots, sourceRoots))
    }

    fun getSearchableRoots(project: Project): Collection<VirtualFile> =
        collectJarRoots(FelixBundleStorageService.getArtifactsDir(project)) +
                collectJarRoots(FelixBundleStorageService.getSourcesDir(project))

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        val storageRoot = FelixBundleStorageService.getStorageRoot(project)
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(storageRoot) ?: return emptyList()
        return listOf(vFile)
    }

    // ----- helpers -----

    private fun collectJarRoots(dir: File): List<VirtualFile> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".jar") }
            ?.mapNotNull { jarFile -> toJarRoot(jarFile) }
            ?: emptyList()
    }

    // Called under a read lock, so no synchronous refresh here; FelixBundleExtractor refreshes the VFS first.
    private fun toJarRoot(jarFile: File): VirtualFile? {
        val localFile = LocalFileSystem.getInstance().findFileByIoFile(jarFile) ?: return null
        return JarFileSystem.getInstance().getJarRootForLocalFile(localFile)
    }
}
