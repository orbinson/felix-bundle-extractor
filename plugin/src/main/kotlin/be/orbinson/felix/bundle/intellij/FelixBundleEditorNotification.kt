package be.orbinson.felix.bundle.intellij

import be.orbinson.felix.bundle.core.SOURCES_SUFFIX
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.io.File
import java.util.function.Function
import javax.swing.JComponent

class FelixBundleEditorNotification : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.extension != "class") return null

        val settings = FelixBundleSettings.getInstance(project)
        if (settings.bundlesDirectory.isBlank()) return null

        val fileIndex = ProjectFileIndex.getInstance(project)
        if (!fileIndex.isInLibrary(file)) return null

        val artifactsDir = FelixBundleStorageService.getArtifactsDir(project)
        if (!artifactsDir.isDirectory) return null

        // VFS URL form: jar:///path/to/artifact.jar!/com/Foo.class
        val artifactsDirUrl = "jar://" + artifactsDir.canonicalPath
        if (!file.url.startsWith(artifactsDirUrl)) return null

        val afterPrefix = file.url.removePrefix(artifactsDirUrl).trimStart('/')
        val artifactJarName = afterPrefix.substringBefore("!/")
        if (artifactJarName.isNotBlank()) {
            val sourcesJarName = artifactJarName.removeSuffix(".jar") + SOURCES_SUFFIX
            val sourcesJar = File(FelixBundleStorageService.getSourcesDir(project), sourcesJarName)
            if (sourcesJar.exists()) return null
        }

        return Function { _ ->
            val panel = EditorNotificationPanel()
            panel.text = "Felix bundle: sources not yet fetched"
            panel.createActionLabel("Fetch sources") {
                FelixBundleExtractor.extractAllInBackground(project)
                panel.isVisible = false
            }
            panel.createActionLabel("Dismiss") {
                panel.isVisible = false
            }
            panel
        }
    }
}
