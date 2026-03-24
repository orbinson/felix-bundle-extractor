# Felix Bundle Extractor

An IntelliJ IDEA plugin **and** standalone CLI tool that attaches AEM / Apache Felix OSGi
bundles as a searchable **External Library** in IntelliJ IDEA, and can also extract bundles
from the command line for use in CI/CD pipelines or scripted workflows.

## Features

- Point the plugin at a local Felix launchpad bundles directory
- All bundles are automatically indexed and available for **Go To Class**, **Find Usages**, and **Find in Files**
- Sources are fetched from Maven Central where available, or decompiled on demand via IntelliJ's built-in Fernflower
  decompiler (plugin) / Vineflower (CLI)
- **"Fetch sources"** editor banner when opening a `.class` file from a bundle without sources
- Dedicated **Felix Bundles** search scope for targeted searches in Find in Files / Find Usages
- Configurable include/exclude filters based on groupId and artifactId (regex)

## Project Structure

```text
core/    Pure Kotlin/JVM extraction logic — no IntelliJ API dependencies
cli/     Standalone fat JAR entry point (java -jar)
plugin/  IntelliJ Platform plugin — delegates to core, wires IntelliJ Platform APIs
```

## CLI Usage

```bash
# Build the fat JAR
./gradlew :cli:shadowJar
# Output: cli/build/libs/cli-2.0.0-SNAPSHOT.jar

# Run
java -jar cli/build/libs/cli-2.0.0-SNAPSHOT.jar <bundlesDir> <outputDir> [options]

# Options
#   --maven-repo <url>           Maven repository base URL (default: Maven Central)
#   --include-groups <regex>     Only extract bundles whose groupId matches
#   --exclude-groups <regex>     Skip bundles whose groupId matches
#   --exclude-artifacts <regex>  Skip bundles whose artifactId matches
#   --exclude-non-maven          Skip bundles without an embedded pom.xml
#   --no-decompile               Do not decompile when Maven sources are unavailable
#   --decompile-timeout <secs>   Per-bundle timeout in seconds (default: 60, 0 = no limit)
#   --verbose                    Print debug-level messages
```

Output is written to `<outputDir>/artifacts/` (binary JARs) and `<outputDir>/sources/` (-sources JARs).

## Plugin Installation

1. Build the plugin ZIP:

   ```bash
   ./gradlew :plugin:buildPlugin
   ```

   Output: `plugin/build/distributions/plugin-2.0.0-SNAPSHOT.zip`

2. In IntelliJ IDEA: **Settings → Plugins → ⚙ → Install Plugin from Disk**
   Select the ZIP file and restart the IDE.

## Plugin Configuration

Open **Settings → Build, Execution, Deployment → Felix Bundle Extractor**.

| Setting                      | Description                                                                                    |
|------------------------------|------------------------------------------------------------------------------------------------|
| Bundles directory            | Absolute path to the Felix launchpad bundles directory (e.g. `crx-quickstart/launchpad/felix`) |
| Include groupIds             | Regex — only bundles whose groupId matches are extracted (leave blank for all)                 |
| Exclude groupIds             | Regex — bundles whose groupId matches are skipped                                              |
| Exclude artifactIds          | Regex — bundles whose artifactId matches are skipped                                           |
| Exclude non-Maven artifacts  | Skip bundles that have no embedded `pom.xml`                                                   |
| When sources cannot be found | `Ask before decompiling` / `Always decompile` / `Never decompile`                              |

Click **Extract / Refresh Bundles Now** to run extraction immediately after saving settings.

## Building

Requires Java 21. Ensure `JAVA_HOME` points to a Java 21 JDK, then:

```bash
./gradlew :core:build          # compile + test core library
./gradlew :cli:shadowJar       # build standalone fat JAR
./gradlew :plugin:buildPlugin  # build IntelliJ plugin ZIP
```

## License

MIT
