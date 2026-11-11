package be.orbinson.felix.bundle.core

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Unit tests for [SourcesUtil].
 *
 * These tests run on plain JVM — no IntelliJ platform fixture or running IDE required.
 *
 * Two scenarios are covered:
 *  1. **Decompilation** — feed a real compiled JAR (containing a .class file compiled from
 *     a minimal Java source) through Fernflower/Vineflower and assert that a sources JAR
 *     is produced.
 *  2. **Download from repository** — spin up a local HTTP server on a random port, serve a
 *     pre-built sources JAR from it, and assert that [SourcesUtil.downloadSources]
 *     fetches and writes it correctly.
 */
class SourcesUtilTest {

    // -------------------------------------------------------------------------
    // 1. Decompilation via Fernflower/Vineflower
    // -------------------------------------------------------------------------

    @Test
    fun `decompile produces a non-empty sources JAR containing java files`(@TempDir tempDir: Path) {
        val bundleJar = buildMinimalJar(tempDir, "sample-bundle.jar")
        val sourcesJar = tempDir.resolve("sample-bundle-sources.jar").toFile()

        val result = SourcesUtil.decompile(bundleJar = bundleJar, sourcesJar = sourcesJar, artifactId = "sample-bundle")

        assertTrue(result, "decompile() should return true when Fernflower produces output")
        assertTrue(sourcesJar.exists(), "Sources JAR should have been created at $sourcesJar")
        assertTrue(sourcesJar.length() > 0, "Sources JAR must be non-empty")

        val allEntries = java.util.jar.JarFile(sourcesJar).use { jar ->
            jar.entries().asSequence().map { it.name }.toList()
        }
        val sourceEntries = allEntries.filter { it.endsWith(".java") || it.endsWith(".kt") }
        assertTrue(sourceEntries.isNotEmpty(), "Sources JAR should contain at least one .java or .kt file, got: $allEntries")
    }

    @Test
    fun `decompiled sources carry banner and original line markers`(@TempDir tempDir: Path) {
        // Blank lines push the statements down so original and decompiled line numbers differ.
        val source = "package sample;\n\n\n\n\n\npublic class Greeter {\n\n\n" +
                "    public String greet(String name) {\n\n\n" +
                "        String prefix = \"Hello \";\n\n\n" +
                "        return prefix + name;\n" +
                "    }\n}\n"
        val srcFile = tempDir.resolve("src/sample/Greeter.java")
        Files.createDirectories(srcFile.parent)
        Files.writeString(srcFile, source)
        val classesDir = Files.createDirectories(tempDir.resolve("classes"))
        val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
        assertEquals(0, compiler.run(null, null, null, "-g", "-d", classesDir.toString(), srcFile.toString()))

        val bundleJar = tempDir.resolve("greeter.jar")
        JarOutputStream(Files.newOutputStream(bundleJar), Manifest()).use { jos ->
            jos.putNextEntry(JarEntry("sample/Greeter.class"))
            jos.write(Files.readAllBytes(classesDir.resolve("sample/Greeter.class")))
            jos.closeEntry()
        }
        val sourcesJar = tempDir.resolve("greeter-sources.jar").toFile()
        assertTrue(SourcesUtil.decompile(bundleJar = bundleJar, sourcesJar = sourcesJar, artifactId = "greeter"))

        val decompiled = java.util.jar.JarFile(sourcesJar).use { jar ->
            jar.getInputStream(jar.getJarEntry("sample/Greeter.java")).bufferedReader().readText()
        }
        val mapping = DecompiledLineMapping.parse(decompiled.reader().buffered())
        val originalToDecompiled = mapping.toList().chunked(2).associate { (orig, dec) -> orig to dec }
        val lines = decompiled.lines()

        // Original line 13 is `String prefix = "Hello ";`, line 16 is the return statement.
        assertTrue(lines[originalToDecompiled.getValue(13) - 1].contains("\"Hello \""), decompiled)
        assertTrue(lines[originalToDecompiled.getValue(16) - 1].contains("return"), decompiled)

        // Realigned: statements sit on their original lines, so debuggers need no mapping.
        assertEquals(13, originalToDecompiled.getValue(13), decompiled)
        assertEquals(16, originalToDecompiled.getValue(16), decompiled)

        // With realignment off the output stays compact, but the markers still map every statement.
        val compactJar = tempDir.resolve("greeter-compact-sources.jar").toFile()
        assertTrue(SourcesUtil.decompile(bundleJar = bundleJar, sourcesJar = compactJar, artifactId = "greeter", realign = false))
        val compact = java.util.jar.JarFile(compactJar).use { jar ->
            jar.getInputStream(jar.getJarEntry("sample/Greeter.java")).bufferedReader().readText()
        }
        val compactMapping = DecompiledLineMapping.parse(compact.reader().buffered()).toList().chunked(2)
            .associate { (orig, dec) -> orig to dec }
        assertTrue(compact.lines().size < decompiled.lines().size, compact)
        assertTrue(compactMapping.getValue(13) < 13, compact)
        assertTrue(compact.lines()[compactMapping.getValue(13) - 1].contains("\"Hello \""), compact)
    }

    @Test
    fun `decompile returns false and writes nothing when input jar is missing`(@TempDir tempDir: Path) {
        val missingJar = tempDir.resolve("does-not-exist.jar")
        val sourcesJar = tempDir.resolve("output-sources.jar").toFile()

        val result = SourcesUtil.decompile(bundleJar = missingJar, sourcesJar = sourcesJar, artifactId = "missing")

        assertFalse(result)
        assertFalse(sourcesJar.exists())
    }

    // -------------------------------------------------------------------------
    // 2. Download from Maven repository
    // -------------------------------------------------------------------------

    @Test
    fun `downloadSources fetches and writes sources JAR from repository`(@TempDir tempDir: Path) {
        val fakeSourcesJar = buildMinimalJar(tempDir, "commons-lang3-3.14.0-sources.jar")
        val fakeSourcesBytes = Files.readAllBytes(fakeSourcesJar)

        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        val expectedPath = "/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0-sources.jar"

        server.createContext(expectedPath) { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "HEAD" -> {
                    exchange.responseHeaders.add("Content-Length", fakeSourcesBytes.size.toString())
                    exchange.sendResponseHeaders(200, -1)
                    exchange.responseBody.close()
                }
                "GET" -> {
                    exchange.sendResponseHeaders(200, fakeSourcesBytes.size.toLong())
                    exchange.responseBody.use { it.write(fakeSourcesBytes) }
                }
                else -> {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.responseBody.close()
                }
            }
        }
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.responseBody.close()
        }
        server.start()

        try {
            val destination = tempDir.resolve("downloaded-sources.jar").toFile()
            val result = SourcesUtil.downloadSources(
                groupId = "org.apache.commons",
                artifactId = "commons-lang3",
                version = "3.14.0",
                mavenRepositoryUrl = "http://localhost:$port",
                destination = destination
            )
            assertTrue(result)
            assertTrue(destination.exists())
            assertArrayEquals(fakeSourcesBytes, Files.readAllBytes(destination.toPath()))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `downloadSources returns false when repository responds with 404`(@TempDir tempDir: Path) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.responseBody.close()
        }
        server.start()

        try {
            val destination = tempDir.resolve("should-not-exist.jar").toFile()
            val result = SourcesUtil.downloadSources(
                groupId = "com.example",
                artifactId = "nonexistent",
                version = "1.0.0",
                mavenRepositoryUrl = "http://localhost:$port",
                destination = destination
            )
            assertFalse(result)
            assertFalse(destination.exists())
        } finally {
            server.stop(0)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Timeout parameter wiring
    // -------------------------------------------------------------------------

    @Test
    fun `decompile respects timeoutSeconds parameter and succeeds within limit`(@TempDir tempDir: Path) {
        val bundleJar = buildMinimalJar(tempDir, "timeout-test-bundle.jar")
        val sourcesJar = tempDir.resolve("timeout-test-sources.jar").toFile()
        val result = SourcesUtil.decompile(bundleJar = bundleJar, sourcesJar = sourcesJar, artifactId = "timeout-test", timeoutSeconds = 60)
        assertTrue(result)
        assertTrue(sourcesJar.exists())
    }

    @Test
    fun `decompile with timeoutSeconds zero disables timeout and succeeds`(@TempDir tempDir: Path) {
        val bundleJar = buildMinimalJar(tempDir, "no-timeout-bundle.jar")
        val sourcesJar = tempDir.resolve("no-timeout-sources.jar").toFile()
        val result = SourcesUtil.decompile(bundleJar = bundleJar, sourcesJar = sourcesJar, artifactId = "no-timeout", timeoutSeconds = 0)
        assertTrue(result)
        assertTrue(sourcesJar.exists())
    }

    @Test
    fun `decompile returns false cleanly when input is missing even with short timeout`(@TempDir tempDir: Path) {
        val missingJar = tempDir.resolve("missing.jar")
        val sourcesJar = tempDir.resolve("missing-sources.jar").toFile()
        val result = SourcesUtil.decompile(bundleJar = missingJar, sourcesJar = sourcesJar, artifactId = "missing-short-timeout", timeoutSeconds = 1)
        assertFalse(result)
        assertFalse(sourcesJar.exists())
    }

    @Test
    fun `downloadSources returns false when host is unreachable`(@TempDir tempDir: Path) {
        val destination = tempDir.resolve("should-not-exist.jar").toFile()
        val result = SourcesUtil.downloadSources(
            groupId = "com.example",
            artifactId = "unreachable",
            version = "1.0.0",
            mavenRepositoryUrl = "http://localhost:1",
            destination = destination
        )
        assertFalse(result)
        assertFalse(destination.exists())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildMinimalJar(dir: Path, name: String): Path {
        val className = SourcesUtilTest::class.java.name
        val classResource = className.replace('.', '/') + ".class"
        val classBytes = SourcesUtilTest::class.java.classLoader
            .getResourceAsStream(classResource)
            ?.readBytes()
            ?: error("Could not load class resource: $classResource")

        val jarPath = dir.resolve(name)
        val manifest = Manifest().apply {
            mainAttributes[java.util.jar.Attributes.Name.MANIFEST_VERSION] = "1.0"
        }
        JarOutputStream(Files.newOutputStream(jarPath), manifest).use { jos ->
            jos.putNextEntry(JarEntry(classResource))
            jos.write(classBytes)
            jos.closeEntry()
        }
        return jarPath
    }
}
