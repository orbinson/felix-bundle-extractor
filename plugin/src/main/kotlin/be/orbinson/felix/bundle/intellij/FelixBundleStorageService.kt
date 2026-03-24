package be.orbinson.felix.bundle.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Provides stable on-disk storage paths for extracted bundle JARs and source JARs.
 *
 * Files are stored under .idea/felix-bundles/ within the project directory so they
 * are local to each project and survive IDE restarts.
 *
 * Layout:
 *   .idea/felix-bundles/
 *     artifacts/   - binary bundle JARs (renamed to <artifactId>-<version>.jar)
 *     sources/     - -sources.jar files (downloaded or decompiled)
 */
object FelixBundleStorageService {

    fun getStorageRoot(project: Project): File {
        val projectDir = File(project.basePath ?: FileUtil.getTempDirectory())
        return File(projectDir, ".idea/felix-bundles")
    }

    fun getArtifactsDir(project: Project): File = File(getStorageRoot(project), "artifacts")

    fun getSourcesDir(project: Project): File = File(getStorageRoot(project), "sources")
}
