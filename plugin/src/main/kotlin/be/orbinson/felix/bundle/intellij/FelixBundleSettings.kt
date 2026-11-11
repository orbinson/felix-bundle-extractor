package be.orbinson.felix.bundle.intellij

import be.orbinson.felix.bundle.core.DecompilationBehavior
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level persistent settings for the Felix Bundle Extractor plugin.
 *
 * Stored in .idea/felixBundleExtractor.xml within the project.
 */
@Service(Service.Level.PROJECT)
@State(name = "FelixBundleExtractor", storages = [Storage("felixBundleExtractor.xml")])
class FelixBundleSettings : PersistentStateComponent<FelixBundleSettings> {

    /** Absolute path to the Felix launchpad bundles directory (e.g. .../crx-quickstart/launchpad/felix). */
    var bundlesDirectory: String = ""

    /** Optional regex to include only bundles whose groupId matches. Empty string means include all. */
    var includeGroupIds: String = ""

    /** Optional regex to exclude bundles whose groupId matches. Empty string means no exclusion. */
    var excludeGroupIds: String = ""

    /** Optional regex to exclude bundles whose artifactId matches. Empty string means no exclusion. */
    var excludeArtifactIds: String = ""

    /** When true, bundles without an embedded pom.xml are skipped entirely. */
    var excludeNonMavenArtifacts: Boolean = false

    /**
     * Maximum time in seconds to allow Fernflower to decompile a single bundle JAR.
     * Set to 0 to disable the timeout entirely.
     */
    var decompilationTimeoutSeconds: Int = 60

    /**
     * Base URL of the Maven repository used to download sources JARs.
     * Defaults to Maven Central.
     */
    var mavenRepositoryUrl: String = "https://repo1.maven.org/maven2"

    /** Decompilation behaviour when sources cannot be downloaded from the Maven repository. */
    var decompilationBehavior: DecompilationBehavior = DecompilationBehavior.ASK

    /** Pad decompiled sources so statements sit on their original line numbers (see README). */
    var realignDecompiledSources: Boolean = true

    override fun getState(): FelixBundleSettings = this

    override fun loadState(state: FelixBundleSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): FelixBundleSettings =
            project.getService(FelixBundleSettings::class.java)
    }
}
