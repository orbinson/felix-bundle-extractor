package be.orbinson.felix.bundle.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class BundleExtractorTest {

    @Test
    fun `slow bundles are processed last and labelled`(@TempDir tempDir: Path) {
        val felix = tempDir.resolve("felix")
        // bundle0 is the slow one, so directory order alone would process it first.
        writeBundle(felix, "bundle0", "aem-precompiled-scripts")
        writeBundle(felix, "bundle1", "com.example.one")
        writeBundle(felix, "bundle2", "com.example.two")

        val labels = mutableListOf<String>()
        BundleExtractor.extractAll(config(tempDir, felix), object : ExtractionListener {
            override fun onProgress(index: Int, total: Int, bundleName: String) {
                labels.add(bundleName)
            }
        })

        assertEquals(3, labels.size)
        assertTrue(labels.last().startsWith("bundle0 (aem-precompiled-scripts: large bundle"), labels.toString())
        assertTrue(labels.take(2).none { it.contains("large bundle") }, labels.toString())
    }

    @Test
    fun `slow bundles get the longer timeout unless there is no limit`(@TempDir tempDir: Path) {
        val slow = BundleExtractor.ScannedBundle(
            tempDir.toFile(), tempDir, BundleExtractor.BundleInfo("g", "aem-precompiled-scripts", "1", false, false), slow = true
        )
        val normal = slow.copy(slow = false)
        val base = config(tempDir, tempDir)

        assertEquals(900, BundleExtractor.timeoutFor(slow, base))
        assertEquals(60, BundleExtractor.timeoutFor(normal, base))
        assertEquals(1200, BundleExtractor.timeoutFor(slow, base.copy(decompilationTimeoutSeconds = 1200)))
        assertEquals(0, BundleExtractor.timeoutFor(slow, base.copy(decompilationTimeoutSeconds = 0)))
    }

    private fun config(tempDir: Path, felix: Path) = ExtractionConfig(
        bundlesDirectory = felix.toString(),
        artifactsDirectory = tempDir.resolve("out/artifacts").toString(),
        sourcesDirectory = tempDir.resolve("out/sources").toString(),
        decompilationBehavior = DecompilationBehavior.NEVER_DECOMPILE,
    )

    /** A manifest-only bundle (no pom.xml), so the artifactId is its Bundle-SymbolicName. */
    private fun writeBundle(felix: Path, dir: String, symbolicName: String) {
        val jar = Files.createDirectories(felix.resolve("$dir/version0.0")).resolve("bundle.jar")
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Bundle-SymbolicName", symbolicName)
            mainAttributes.putValue("Implementation-Version", "1.0.0")
        }
        JarOutputStream(Files.newOutputStream(jar), manifest).use { }
    }
}
