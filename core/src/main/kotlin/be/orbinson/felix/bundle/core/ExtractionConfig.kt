package be.orbinson.felix.bundle.core

/**
 * All configuration needed to perform a full bundle extraction.
 *
 * This is a plain data class with no IntelliJ dependencies so it can be
 * constructed from the CLI (via command-line arguments) or from the plugin
 * (via `FelixBundleSettings` in the `plugin` module).
 *
 * @param bundlesDirectory     Absolute path to the Felix launchpad bundles directory.
 * @param artifactsDirectory   Absolute path where binary bundle JARs will be written.
 * @param sourcesDirectory     Absolute path where -sources JARs will be written.
 * @param includeGroupIds      Regex; only bundles whose groupId matches are extracted.
 *                             Empty string means include all.
 * @param excludeGroupIds      Regex; bundles whose groupId matches are skipped.
 *                             Empty string means no exclusion.
 * @param excludeArtifactIds   Regex; bundles whose artifactId matches are skipped.
 *                             Empty string means no exclusion.
 * @param excludeNonMavenArtifacts  When true, bundles without an embedded pom.xml are skipped.
 * @param mavenRepositoryUrl   Base URL of the Maven repository used for downloading sources.
 * @param decompilationBehavior  What to do when Maven sources are unavailable.
 * @param decompilationTimeoutSeconds  Per-bundle Fernflower timeout; 0 means no limit.
 */
data class ExtractionConfig(
    val bundlesDirectory: String,
    val artifactsDirectory: String,
    val sourcesDirectory: String,
    val includeGroupIds: String = "",
    val excludeGroupIds: String = "",
    val excludeArtifactIds: String = "",
    val excludeNonMavenArtifacts: Boolean = false,
    val mavenRepositoryUrl: String = "https://repo1.maven.org/maven2",
    val decompilationBehavior: DecompilationBehavior = DecompilationBehavior.ASK,
    val decompilationTimeoutSeconds: Int = 60,
)
