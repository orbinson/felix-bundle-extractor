package be.orbinson.felix.bundle.intellij

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Represents the Felix OSGi bundles as an IntelliJ SyntheticLibrary (External Library).
 *
 * @param binaryRoots  Original bundle JAR roots.
 * @param sourceRoots  Generated source roots, kept separate from binaries for debugging.
 * @param name         Display name shown in the External Libraries tree node.
 * @param showInExternalLibraries  Whether to show this library in the External Libraries tree.
 */
class FelixBundleLibrary(
    private val binaryRoots: Collection<VirtualFile>,
    private val sourceRoots: Collection<VirtualFile>,
    private val name: String = "Felix Bundles",
    private val showInExternalLibraries: Boolean = true
) : SyntheticLibrary(), ItemPresentation {

    override fun getBinaryRoots(): Collection<VirtualFile> = binaryRoots

    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots

    override fun isShowInExternalLibrariesNode(): Boolean = showInExternalLibraries

    override fun getPresentableText(): String = name

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FelixBundleLibrary) return false
        return name == other.name && binaryRoots == other.binaryRoots && sourceRoots == other.sourceRoots
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + binaryRoots.hashCode()
        result = 31 * result + sourceRoots.hashCode()
        return result
    }
}
