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
 * Original bundles and generated sources are separate libraries so the debugger
 * uses the IDE decompiler for bundle classes while generated Java remains indexed.
 */
class FelixBundleLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val settings = FelixBundleSettings.getInstance(project)
        val bundlesDir = settings.bundlesDirectory.trim()
        if (bundlesDir.isEmpty()) return emptyList()

        val binaryRoots = collectJarRoots(FelixBundleStorageService.getArtifactsDir(project))
        val sourceRoots = collectJarRoots(FelixBundleStorageService.getSourcesDir(project))

        return buildList {
            if (binaryRoots.isNotEmpty()) add(FelixBundleLibrary(binaryRoots, emptyList()))
            if (sourceRoots.isNotEmpty()) {
                add(FelixBundleLibrary(emptyList(), sourceRoots, "Felix Bundle Decompiled Sources", false))
            }
        }
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

    private fun toJarRoot(jarFile: File): VirtualFile? {
        val localFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jarFile) ?: return null
        return JarFileSystem.getInstance().getJarRootForLocalFile(localFile)
    }
}
