package be.orbinson.felix.bundle.cli

import be.orbinson.felix.bundle.core.BundleExtractor
import be.orbinson.felix.bundle.core.DecompilationBehavior
import be.orbinson.felix.bundle.core.ExtractionConfig
import be.orbinson.felix.bundle.core.ExtractionListener
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """
Usage: felix-bundle-extractor <bundlesDir> <outputDir> [options]

Arguments:
  <bundlesDir>   Path to the Felix launchpad bundles directory
                 (e.g. crx-quickstart/launchpad/felix)
  <outputDir>    Directory where extracted JARs will be written.
                 Subdirectories 'artifacts/' and 'sources/' are created automatically.

Options:
  --maven-repo <url>           Maven repository base URL for downloading sources
                               (default: https://repo1.maven.org/maven2)
  --include-groups <regex>     Only extract bundles whose groupId matches this regex
  --exclude-groups <regex>     Skip bundles whose groupId matches this regex
  --exclude-artifacts <regex>  Skip bundles whose artifactId matches this regex
  --exclude-non-maven          Skip bundles without an embedded pom.xml
  --no-decompile               Do not decompile bundles when Maven sources are unavailable
  --decompile-timeout <secs>   Per-bundle decompilation timeout in seconds (default: 60, 0 = no limit)
  --verbose                    Print debug-level messages
  --help                       Show this help message
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help")) {
        println(USAGE.trim())
        exitProcess(0)
    }

    // Parse positional arguments
    val positional = args.filter { !it.startsWith("--") }
    if (positional.size < 2) {
        System.err.println("Error: <bundlesDir> and <outputDir> are required.\n")
        System.err.println(USAGE.trim())
        exitProcess(1)
    }

    val bundlesDir = positional[0]
    val outputDir = positional[1]

    // Parse options
    var mavenRepo = "https://repo1.maven.org/maven2"
    var includeGroups = ""
    var excludeGroups = ""
    var excludeArtifacts = ""
    var excludeNonMaven = false
    var noDecompile = false
    var decompileTimeout = 60
    var verbose = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--maven-repo"          -> { mavenRepo = args.getOrElse(++i) { argError("--maven-repo") } }
            "--include-groups"      -> { includeGroups = args.getOrElse(++i) { argError("--include-groups") } }
            "--exclude-groups"      -> { excludeGroups = args.getOrElse(++i) { argError("--exclude-groups") } }
            "--exclude-artifacts"   -> { excludeArtifacts = args.getOrElse(++i) { argError("--exclude-artifacts") } }
            "--exclude-non-maven"   -> excludeNonMaven = true
            "--no-decompile"        -> noDecompile = true
            "--decompile-timeout"   -> { decompileTimeout = args.getOrElse(++i) { argError("--decompile-timeout") }.toIntOrNull() ?: argError("--decompile-timeout requires an integer") }
            "--verbose"             -> verbose = true
            "--help"                -> { println(USAGE.trim()); exitProcess(0) }
        }
        i++
    }

    val artifactsDir = File(outputDir, "artifacts").absolutePath
    val sourcesDir = File(outputDir, "sources").absolutePath

    val config = ExtractionConfig(
        bundlesDirectory = bundlesDir,
        artifactsDirectory = artifactsDir,
        sourcesDirectory = sourcesDir,
        includeGroupIds = includeGroups,
        excludeGroupIds = excludeGroups,
        excludeArtifactIds = excludeArtifacts,
        excludeNonMavenArtifacts = excludeNonMaven,
        mavenRepositoryUrl = mavenRepo,
        decompilationBehavior = if (noDecompile) DecompilationBehavior.NEVER_DECOMPILE else DecompilationBehavior.ALWAYS_DECOMPILE,
        decompilationTimeoutSeconds = decompileTimeout,
    )

    val listener = object : ExtractionListener {
        override fun onStart(total: Int) {
            println("Processing $total bundle directories...")
        }

        override fun onProgress(index: Int, total: Int, bundleName: String) {
            val pct = ((index + 1) * 100) / total
            print("\r[$pct%] $bundleName${" ".repeat(maxOf(0, 60 - bundleName.length))}")
        }

        override fun info(message: String) { println("\n$message") }
        override fun warn(message: String) { System.err.println("\nWARN  $message") }
        override fun debug(message: String) { if (verbose) println("DEBUG $message") }
        override fun error(message: String, cause: Throwable?) {
            System.err.println("\nERROR $message")
            if (verbose) cause?.printStackTrace(System.err)
        }
    }

    BundleExtractor.extractAll(config, listener)
    println("\nDone.")
}

private fun argError(flag: String): Nothing {
    System.err.println("Error: missing value for $flag")
    exitProcess(1)
}
