package be.orbinson.felix.bundle.intellij

import be.orbinson.felix.bundle.core.DecompiledLineMapping
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile

private val LOG = Logger.getInstance(FelixBundleLineMapping::class.java)

/**
 * Decompiled sources don't line up with the bytecode line numbers. The debugger reads
 * [LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY] from the source file to translate breakpoints and
 * stack frames, so it is built from the `// <line>` markers and stored on the [VirtualFile].
 */
object FelixBundleLineMapping {

    fun installIfDecompiled(project: Project, file: VirtualFile) {
        if (file.extension != "java") return
        if (file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY) != null) return
        if (!isInSourcesDir(project, file)) return

        try {
            val mapping = file.inputStream.bufferedReader(file.charset).use { DecompiledLineMapping.parse(it) }
            if (mapping.isNotEmpty()) {
                file.putUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY, LineNumbersMapping.ArrayBasedMapping(mapping))
                LOG.debug { "Installed line mapping for ${file.path} (${mapping.size / 2} entries)" }
            }
        } catch (e: Exception) {
            LOG.warn("Could not read line mapping for ${file.path}", e)
        }
    }

    private fun isInSourcesDir(project: Project, file: VirtualFile): Boolean {
        val jar = JarFileSystem.getInstance().getVirtualFileForJar(file) ?: return false
        val sourcesDir = FileUtil.toSystemIndependentName(FelixBundleStorageService.getSourcesDir(project).canonicalPath)
        return jar.parent?.path == sourcesDir
    }
}
