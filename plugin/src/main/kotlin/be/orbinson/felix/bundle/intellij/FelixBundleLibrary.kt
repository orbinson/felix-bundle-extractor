package be.orbinson.felix.bundle.intellij

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Represents the Felix OSGi bundles as an IntelliJ SyntheticLibrary (External Library).
 *
 * @param binaryRoots  VirtualFiles pointing to the bundle JARs in the artifacts/ directory.
 * @param sourceRoots  VirtualFiles pointing to the -sources JARs in the sources/ directory.
 * @param name         Display name shown in the External Libraries tree node.
 */
class FelixBundleLibrary(
    private val binaryRoots: Collection<VirtualFile>,
    private val sourceRoots: Collection<VirtualFile>,
    private val name: String = "Felix Bundles"
) : SyntheticLibrary(), ItemPresentation {

    override fun getBinaryRoots(): Collection<VirtualFile> = binaryRoots

    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots

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
