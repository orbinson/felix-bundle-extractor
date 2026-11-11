package be.orbinson.felix.bundle.intellij

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Installs the line mapping before a decompiled source is shown, so breakpoints set in it map correctly. */
class FelixBundleLineMappingListener(private val project: Project) : FileEditorManagerListener.Before {

    override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        FelixBundleLineMapping.installIfDecompiled(project, file)
    }
}
