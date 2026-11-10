package be.orbinson.felix.bundle.intellij

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider

class FelixBundleSearchScopeProvider : SearchScopeProvider {

    override fun getDisplayName(): String = "Felix Bundles"

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> {
        val settings = FelixBundleSettings.getInstance(project)
        if (settings.bundlesDirectory.isBlank()) return emptyList()

        val allRoots = FelixBundleLibraryProvider().getSearchableRoots(project).toSet()
        if (allRoots.isEmpty()) return emptyList()

        val scope = object : GlobalSearchScope(project) {
            override fun getDisplayName(): String = "Felix Bundles"
            override fun contains(file: VirtualFile): Boolean =
                allRoots.any { root -> file == root || file.url.startsWith(root.url) }
            override fun isSearchInModuleContent(aModule: Module): Boolean = false
            override fun isSearchInLibraries(): Boolean = true
        }

        return listOf(scope)
    }
}
