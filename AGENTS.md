# AGENTS.md - felix-bundle-extractor

## Project Overview

IntelliJ IDEA plugin **and** standalone CLI tool (Kotlin) that attaches AEM / Apache Felix
OSGi bundles as a searchable **External Library** in IntelliJ IDEA, and can also extract
bundles from the command line.

- **Plugin ID:** `be.orbinson.felix.bundle.extractor`
- **Package (core/cli):** `be.orbinson.felix.bundle.core` / `be.orbinson.felix.bundle.cli`
- **Package (plugin):** `be.orbinson.felix.bundle.intellij`
- **Language:** Kotlin 2.1.0
- **Target IDE:** IntelliJ IDEA Community/Ultimate 2024.2+ (`sinceBuild = "242"`)
- **Build system:** Gradle 8.14.3 with IntelliJ Platform Gradle Plugin 2.10.5 (2.11+ needs Gradle 9)
- **License:** MIT

## Module Structure

```text
core/    Pure Kotlin/JVM — no IntelliJ API dependencies
           ExtractionConfig, ExtractionListener, DecompilationBehavior
           BundleExtractor   — scan/copy logic (slow bundles such as aem-precompiled-scripts last)
           SourcesUtil       — Maven download + Fernflower/Vineflower decompilation
           DecompiledLineMapping — parses `// <line>` markers, realigns decompiled sources
         Depends on: org.apache.maven:maven-model, org.vineflower:vineflower

cli/     Standalone fat JAR entry point (java -jar)
           Main.kt           — argument parsing, delegates to BundleExtractor
         Depends on: :core (shadow JAR via com.github.johnrengelman.shadow)

plugin/  IntelliJ Platform plugin — bridges IntelliJ APIs with core logic
           FelixBundleSettings          — PersistentStateComponent
           FelixBundleLibrary           — SyntheticLibrary
           FelixBundleLibraryProvider   — AdditionalLibraryRootsProvider
           FelixBundleStorageService    — .idea/felix-bundles/ paths
           FelixBundleExtractor         — builds ExtractionConfig, runs BundleExtractor
           FelixBundleSettingsConfigurable — Settings UI
           FelixBundleEditorNotification   — EditorNotificationProvider
           FelixBundleSearchScopeProvider  — SearchScopesProvider
           FelixBundleLineMapping          — installs LineNumbersMapping on decompiled sources
           FelixBundleLineMappingListener  — FileEditorManagerListener.Before (file opened)
           FelixBundlePositionManagerFactory — debugger hook (stops, frames, breakpoints)
         Depends on: :core (vineflower excluded — bundled java-decompiler.jar takes over)
```

Storage layout at runtime inside the IntelliJ project:

```text
.idea/felix-bundles/
  artifacts/   # binary bundle JARs renamed to <artifactId>-<version>.jar
  sources/     # -sources.jar files (downloaded from Maven Central or decompiled)
```

## Build Commands

```bash
# Full builds
./gradlew :core:build          # compile + test core library
./gradlew :cli:shadowJar       # standalone fat JAR → cli/build/libs/cli-2.0.0-SNAPSHOT.jar
./gradlew :plugin:buildPlugin  # plugin ZIP → plugin/build/distributions/plugin-2.0.0-SNAPSHOT.zip

# Verify plugin project configuration
./gradlew :plugin:verifyPluginProjectConfiguration

# Sandbox IDEs with the plugin installed
./gradlew :plugin:runIde        # IntelliJ IC 2024.2 (compile target)
./gradlew :plugin:runLocalIde   # local install, default /Applications/IntelliJ IDEA.app (-PlocalIdePath=...)
```

**Java home:** Gradle tasks require Java 21. Ensure `JAVA_HOME` points to a Java 21 JDK before running any build tasks.

## Testing

Tests live in `core/src/test/`. They run on plain JVM — no IntelliJ fixture required.

```bash
./gradlew :core:test
```

## Installing / Updating the Plugin

Install from disk in IntelliJ: Settings → Plugins → ⚙ → Install Plugin from Disk →
select `plugin/build/distributions/plugin-2.0.0-SNAPSHOT.zip` → restart.
Reinstalling over an existing version updates it in-place (same plugin ID).

## Dependencies

| Library                                          | Module | Purpose                                                                                                      |
|--------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------|
| `org.apache.maven:maven-model:3.9.6`             | core   | `MavenXpp3Reader` — parse embedded pom.xml                                                                   |
| `org.vineflower:vineflower:1.11.1`               | core   | Fernflower-compatible decompiler on Maven Central (same package names as IntelliJ's bundled java-decompiler) |
| `bundledPlugin("com.intellij.java")`             | plugin | `AdditionalLibraryRootsProvider`, Java indexing                                                              |
| `bundledPlugin("org.jetbrains.java.decompiler")` | plugin | Fernflower decompiler (overrides vineflower at runtime; classloader wired via `<depends>`)                   |
| `bundledPlugin("org.jetbrains.idea.maven")`      | plugin | Maven model classes at compile time                                                                          |

## Key Design Decisions and Discoveries

### Vineflower as the core decompiler dependency

`java-decompiler.jar` (IntelliJ's Fernflower fork) is not published to any Maven
repository — it is only available as a bundled IntelliJ plugin. Vineflower
(`org.vineflower:vineflower`) is a maintained Fernflower fork that uses **identical
package and class names** (`org.jetbrains.java.decompiler.*`). `core` compiles against
Vineflower; inside the plugin, Vineflower is excluded from the dependency graph and
IntelliJ's bundled `java-decompiler.jar` takes precedence at runtime.

### Fernflower / java-decompiler classloader

`bundledPlugin("org.jetbrains.java.decompiler")` in `plugin/build.gradle.kts` puts the
decompiler on the **compile** classpath only. Without a matching `<depends>` entry in
`plugin.xml`, the IntelliJ classloader system does NOT wire the decompiler plugin's
classloader to ours — resulting in `NoClassDefFoundError: IFernflowerLogger` at
runtime even though the code compiled fine.

Fix: `plugin.xml` declares:

```xml

<depends optional="true" config-file="be.orbinson.felix.bundle.extractor-withDecompiler.xml">
    org.jetbrains.java.decompiler
</depends>
```

The companion XML file is a required placeholder (can be empty `<idea-plugin>`).
The `config-file` attribute must follow the naming pattern `pluginId-$NAME$.xml`.

### Fernflower API — use constructor, not main()

`ConsoleDecompiler.main()` calls `System.exit()` internally — this kills the IDE
process. Use the constructor API instead:

```kotlin
val decompiler = ConsoleDecompiler(outputDir, emptyMap<String, Any>(), logger)
decompiler.addSource(bundleJar.toFile())
decompiler.decompileContext()
```

### Debuggable decompiled sources

Decompiled code does not line up with the bytecode `LineNumberTable`, so breakpoints and stepping
land on the wrong lines. Three layers fix this:

1. **Markers.** `SourcesUtil.DECOMPILER_OPTIONS` mirrors IntelliJ's `IdeaDecompiler` options;
   `bsm=1` + `__dump_original_lines__=1` append `// <original line>` to every statement, and the
   `ban` banner (`DECOMPILED_BANNER`) marks files as ours. These options only take effect through the
   constructor API: the `ConsoleDecompiler` CLI only parses 3-letter `-xxx=` options.
2. **Realignment** (`DecompiledLineMapping.realign`). Pads blank lines, or drops blank lines and joins
   structural lines (only when that lands the statement exactly), so statements sit on their original
   line. Needed because IntelliJ's `SourceCodeChecker` ("Source code does not match the bytecode")
   only applies line mappings to `PsiCompiledFile`s; for attached sources it compares raw bytecode
   lines with the method's line range. Out-of-order code (inner classes, reordered branches) can't be
   realigned. Measured on all 653 AEM bundles: 67% of bytecode lines aligned, 18% of methods warn.
   Configurable (`realignDecompiledSources`, default on; CLI `--no-realign`): aligned files are
   ~1.8x longer than compact output.
3. **Line mapping (plugin).** `LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY` on the source
   `VirtualFile`, built from the markers. The debugger reads it eagerly (stop positions, frames, and
   `CompoundPositionManager.locationsOfLine` before asking any position manager), so it is installed
   both on file open and from `FelixBundlePositionManager` (`getSourcePosition`, `getAllClasses`,
   `createPrepareRequest`), which then throws `NoDataException` to defer to the built-in manager.
   Custom position managers are prepended, so they run first.

### Concurrent extraction guard

`FelixBundleExtractor` is an `object` (singleton). Multiple button clicks launch
multiple `Task.Backgroundable` instances that race on the filesystem. Fixed with
an `AtomicBoolean running` flag: `compareAndSet(false, true)` at entry, reset in
`finally` and `onCancel()`. Duplicate requests are silently dropped.

### buildSearchableOptions / verifyPluginProjectConfiguration / prepareTest JVM

The IntelliJ Platform Gradle Plugin spawns subprocesses using the **JBR bundled with
the downloaded IDE**, not the Gradle JDK. Fixed by setting `runtimeDirectory` on **three**
tasks to `JAVA_HOME` in `plugin/build.gradle.kts`:

- `verifyPluginProjectConfiguration`
- `buildSearchableOptions`
- `prepareTest` — also implements `RuntimeAware`; it runs before the `test` task and
  collects JVM metadata using the JBR. Without overriding it the `test` task fails with
  `javaLauncher` property evaluation error (exit code 137). Setting `executable` on
  the `test` task alone is not sufficient — `prepareTest` must also be overridden.

Do NOT use `testFramework(TestFrameworkType.Platform)` in `plugin/build.gradle.kts` — it
triggers a download of `test-framework-*.jar` from the geo-blocked JetBrains CDN
(403 Forbidden). Plain JUnit 5 tests need only `junit-jupiter-*` on the test classpath.

### No synchronous VFS refresh in the library provider

`getAdditionalProjectLibraries` runs under a read lock; `refreshAndFindFileByIoFile` there logs
"Do not perform a synchronous refresh under read lock". The provider uses `findFileByIoFile`, and
`FelixBundleExtractor` refreshes the storage dirs synchronously from its background task first.

### instrumentCode disabled

`:instrumentCode` and `:instrumentTestCode` require downloading from a
geo-restricted JetBrains CDN (403 Forbidden). Since the plugin has no `.form` GUI
Designer files, these tasks are disabled with `enabled = false`.

### projectConfigurable parentId

Use `parentId="build"` (not `groupId="build"`) to nest the settings page under
Settings → Build, Execution, Deployment. Using `groupId` causes the page to be
silently absent from the settings tree.

### EditorNotification bundle detection

The `.class` file's VFS URL has the form `jar:///path/to/artifact.jar!/com/Foo.class`.
Membership check: `file.url.startsWith("jar://" + artifactsDir.canonicalPath)`.
Do NOT walk the artifacts directory on every `.class` file open — that is O(n) disk I/O.

### AdditionalLibraryRootsListener.fireAdditionalLibraryChanged

Confirmed present in IC-2024.2 with signature:

```java
static void fireAdditionalLibraryChanged(Project, String, Collection<VirtualFile>,
                                         Collection<VirtualFile>, String)
```

## Code Style Guidelines

### Language

- Kotlin. Follow standard Kotlin idioms (data classes, object declarations, `when`
  expressions, named arguments).
- No annotation processing, no DI framework.
- `core` and `cli`: use `java.util.logging` or the `ExtractionListener` callback for logging.
- `plugin`: use `Logger.getInstance(Foo::class.java)` from `com.intellij.openapi.diagnostic`.

### Formatting

- **Indentation:** 4 spaces.
- **Encoding:** UTF-8.
- **Braces:** K&R style (opening brace on same line).
- **Line length:** ~120 characters.

### Naming Conventions

| Element        | Convention                                  | Example                             |
|----------------|---------------------------------------------|-------------------------------------|
| Classes        | PascalCase                                  | `BundleExtractor`                   |
| Methods        | camelCase, verb-based                       | `extractAll()`, `downloadSources()` |
| Top-level vals | camelCase or UPPER_SNAKE_CASE for constants | `LOG`, `SOURCES_SUFFIX`             |
| Packages       | lowercase, reverse-domain                   | `be.orbinson.felix.bundle.core`     |

### Error Handling

- **Log and continue** — catch exceptions, log them, continue.
- Never re-throw. Exception variable is always `e`.
- Error messages follow the `"Could not <action>"` pattern.
- In `plugin`: always pass the exception object as the second argument to `LOG.error()`.
- In `core`/`cli`: pass `cause` to `ExtractionListener.error()`.

### Null Handling

- Prefer Kotlin null-safety (`?.`, `?:`, `!!` only when provably non-null).
- Do not use `Optional`.
